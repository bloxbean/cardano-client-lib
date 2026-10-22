# Programmable Token

`cardano-client-programmable-token` adds protocol-neutral programmable-token intents to QuickTx.
CIP-113 is the initial protocol adapter. It builds transactions for the CIP-113 core contract
suite `v0.0.1` (cardano-foundation/cip113-programmable-tokens `6b75ba3`; see
`src/it/resources/blueprint/cip113/compatibility.yml` for the pinned snapshot) and ships the
public Preview, Preprod and Mainnet deployments of it.

The module is not part of the `cardano-client-lib` or `cardano-client-cip` aggregates while it is
experimental. Add it explicitly:

```groovy
implementation "com.bloxbean.cardano:cardano-client-programmable-token:${cardanoClientLibVersion}"
```

```java
ProgrammableTokenService programmableTokens =
        Cip113ProgrammableTokenService.create(backend, Cip113Deployments.PREVIEW);

ProgrammableTokenTx tx = new ProgrammableTokenTx()
        .from(owner)
        .transfer(receiver, amount, transferRedeemer)
        .transfer(secondReceiver, secondAmount, transferRedeemer);

new QuickTxBuilder(backend)
        .withExtension(programmableTokens.extension())
        .compose(tx)
        .withSigner(signer)
        .completeAndWait();
```

## Deployments

`Cip113Deployments.PREVIEW`, `PREPROD` and `MAINNET` describe the public v0.0.1 deployments. Every
script hash in them is re-derived from the vendored blueprint by `Cip113DeploymentsTest`, and the
values were checked against each chain. A v0.0.1 deployment is *described*, not discovered: the
base script, registry, inline-datum bound and unfracking script are compile-time parameters of
other validators and appear nowhere on chain in readable form. To use another deployment, build a
complete `Cip113Deployment` from its deployer's bootstrap record.

Resolution (on first use, or `advanced().resolveDeployment()`) locates the live coordination
UTxO by its NFT and adopts the dispatcher, issuance-logic and delegate credentials its datum names,
logging a warning when they differ from the description — an in-place upgrade swaps exactly those.
It also locates the issuance template and verifies each described reference script is still
published where the description says; a spent one is skipped and the script is carried as a
witness instead.

**None of the public deployments can unfrack.** Each compiled its `programmable_logic_global`
dispatcher with a placeholder instead of the unfracking script's hash, so `UnfrackingAct` can never
be authorised. `Cip113Deployment.isUnfrackingEnabled()` is false for them and the adapter does not
offer `UNFRACK`; a deployment whose dispatcher names the real script (as the integration suite's
devnet deployment does) supports it.

## Portable plans

For a portable plan, configure the same extension on the plan codec and builder:

```java
ProgrammableTokenExtension extension = programmableTokens.extension();
TxPlan plan = extension.configure(TxPlan.from(tx));
TxPlanCodec codec = programmableTokens.txPlanCodec();
String yaml = codec.toYaml(plan);

TxPlan replay = codec.fromYaml(yaml, runtimeVariables);
new QuickTxBuilder(backend).withExtension(extension).compose(replay).build();
```

The namespace `pt` is document-local. Persisted plans pin extension id, schema version, protocol,
and deployment metadata (`network`, `bootstrap_tx`, `params_policy`), which are validated against
the configured deployment before any chain access. `contract_version`, when present, must be
`0.0.1`: a plan written for another CIP-113 contract suite describes transaction shapes this
adapter does not build, and is refused rather than re-targeted. Ordinary
`Tx.payToAddress(...)` never performs programmable-token routing. Use
`programmableTokens.txPlanCodec("tokens")` when a document needs a custom namespace; use the generic
`TxPlanCodec` builder when combining multiple extensions.

Plain `TxPlan.toYaml()` and `TxPlan.from(yaml)` handle core intents only. They refuse a plan or
document with programmable-token intents or declared extensions (an `extensions:` block, as added by
`extension.configure(...)`) and point to `TxPlanCodec`, because the core reader could not
reconstruct those intents.

## Operations

| Verb | YAML type | Notes |
|------|-----------|-------|
| `transfer(receiver, amount, redeemer[, datum])` | `pt:transfer` | Owner transfer to a smart wallet; optional bounded inline datum on the receiving output. The unit is read as standard QuickTx reads it (hex in any case; a 56-character unit is the empty asset name). |
| `mint(policy, receiver, assets, redeemer, datum)` | `pt:mint` | Policy may be a literal id or the name of a registration in the same transaction. |
| `burn(policyId, assets, authorization)` | `pt:burn` | Literal `policy_id` only: a `policy_ref` names a registration in the same transaction, and a burn needs the policy's registry node and held tokens, which do not exist until that registration is on chain. Transfer and issuance redeemers are distinct; every burn of one policy in a transaction must agree on both. |
| `thirdPartyTransfer(holder, receiver, amount, redeemer)` | `pt:third_party_transfer` | One holder and one policy per transaction; many outputs for that policy aggregate. |
| `register(name, registration, redeemer)` | `pt:register` | Publishes a named policy for mints in the same transaction, wherever they are written. |
| `updateRegistry(policy, update, authorization)` | `pt:update_registry` | Must be its own transaction. |
| `unfrack(policy, authorization)` | `pt:unfrack` | Regroups one policy out of the sender's shared smart-wallet UTxOs into a fresh single-policy output. Must be its own transaction; the token's `unfracking_logic_script` hook authorises it, and a token whose issuer left the hook unset cannot be unfracked. |

Authoring models (`Tx`, `TxPlan`, `QuickTxBuilder.TxContext`) are mutable and not thread-safe,
but a plan can be built again sequentially: extension-generated intents are a build-local overlay
and never change the authored plan. Services and extension descriptors are safe to share between
independent concurrent builds.

Reference inputs work as usual. The adapter adds the protocol's own at build time (coordination
UTxO, the policy's registry node, global state when present, and the published scripts it needs),
so no verb takes them as arguments. Anything the caller adds with `Tx.readFrom(...)` or a
`reference_input` intent in a plan lands in the same body; naming a UTxO the protocol already reads
adds nothing twice, and every CIP-113 redeemer index is computed over the ledger-sorted union.

## Limitations of the CIP-113 adapter

- Verification-key transfer, third-party and unfracking credentials are not invoked; registrations
  and registry updates naming one are refused so a token cannot be created that this library could
  not operate. The empty-key sentinel that forbids unfracking is data and remains valid.
- Unfracking needs a deployment whose dispatcher was compiled against the real unfracking script;
  none of the public deployments was (see Deployments).
- Protocol-upgrade and authority operations (`ProtocolUpgrade`, `NominateAuthority`,
  `PromoteAuthority`) are not authored by this module.
- A third-party transaction acts on one policy, because the `third_party` redeemer names one
  registry node. Composition rules apply to the whole ledger transaction: every programmable-token
  operation of one transaction must be authored in a single `Tx` (fragments composed with
  `compose(tx1, tx2)` are validated together and refused before any lookup), and an unfracking
  cannot share a transaction with any mint or burn because its validator requires an empty mint. A
  third-party transfer may share a transaction with an unrelated native-asset mint: its validator
  reads only the acted policy's mint and pins only the paired smart-wallet outputs. Owner transfers
  and burns of several policies, with or without a native mint, compose in one `Tx`, and are
  funded by one input selection, so a smart-wallet UTxO holding several of those policies is spent
  once whatever order the operations are written in.
- Programmable outputs hold one policy each; inline datums are bounded by the deployment's
  `max_inline_datum_bytes`.
- TxFlow runs Java `TxPlan` steps that use programmable-token intents when the extension is
  registered on the runtime (`FlowRuntime.builder(...).extension(ext)`,
  `FlowEngine.builder(...).extension(ext)` or `FlowExecutor.withExtension(ext)`). Bind it on each
  plan with `ext.configure(plan)`. The registry, the coordination UTxO and global state are read
  through the build's UTxO supplier, so in PIPELINED mode a step sees the registry node an earlier
  pending step created once it declares
  `fundsFrom(thatStep)` — a mint after a registration needs `fundsFrom(register)`, and a transfer
  of that token needs `fundsFrom(register)` and `fundsFrom(mint)`. Without the edge the build
  fails with "not registered" rather than using stale state. A build that sees no coordination
  UTxO, or one with other protocol parameters than the resolved deployment, fails instead of
  referencing a spent or stale one; re-resolve the deployment after an in-place upgrade. Pipelined CIP-113 steps also need a
  local script evaluator (`txEvaluator(...)` on the engine), because the backend evaluator cannot
  see pending inputs. Portable TxFlow documents declare the binding in `spec.extensions` and use
  `pt:*` intents in a step's `tx.intents`. Write Plutus data there in structured form: the
  extension writes it back structured, and TxFlow rejects an intent that does not round-trip
  (`TXFLOW_EXTENSION_INTENT_LOSSY`). TxFlowStream items work the same way on an engine that
  registers the extension; each item is its own transaction.
