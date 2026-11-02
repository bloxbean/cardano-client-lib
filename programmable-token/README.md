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
be authorised. `Cip113Deployment.isUnfrackingEnabled()` describes this on-chain property only.
**Unfracking is temporarily unsupported by this release on every deployment**, including custom
deployments whose dispatcher enables it. `unfrack(...)` throws `UnsupportedOperationException`,
and the adapter does not advertise `UNFRACK`.

**Smart-wallet key.** A smart wallet is `(base script, owner credential)`, and CIP-113 lets the
application key it by the owner's payment or stake credential. `Cip113Deployment.smartWalletKey`
says which (`PAYMENT` by default). It decides the wallet a sender spends from, the wallet a plain
receiver address is mapped to, and the key declared as required signer. An application that keeps
tokens at `(base script, stake credential)` sets `STAKE`; under the default it could receive at
such a wallet but never spend from it.

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

The `pt:unfrack` decoder is retained to report an explicit unsupported error for existing documents.
Its intent validation throws `UnsupportedOperationException`; TxPlan reads/writes reject it and
portable TxFlow compilation reports `TXFLOW_EXTENSION_INTENT_INVALID` before any step runs.
Protocol data fields such as `unfracking_logic_script` remain part of registry datums and updates.

## Operations

| Verb | YAML type | Notes |
|------|-----------|-------|
| `transfer(receiver, amount, redeemer[, datum])` | `pt:transfer` | Owner transfer to a smart wallet; optional bounded inline datum on the receiving output. The unit is read as standard QuickTx reads it (hex in any case; a 56-character unit is the empty asset name). |
| `mint(policy, receiver, assets, redeemer, datum)` | `pt:mint` | Policy may be a literal id or the name of a registration in the same transaction. |
| `burn(policyId, assets, authorization)` | `pt:burn` | Literal `policy_id` only: a `policy_ref` names a registration in the same transaction, and a burn needs the policy's registry node and held tokens, which do not exist until that registration is on chain. Transfer and issuance redeemers are distinct; every burn of one policy in a transaction must agree on both. |
| `thirdPartyTransfer(holder, receiver, amount, redeemer)` | `pt:third_party_transfer` | Seizure: the token's third-party logic authorises moving tokens out of `holder`'s smart wallet, inputs coin-selected from it. One policy per transaction, any number of holders. |
| `thirdPartyBurn(holderUtxoRef, policyId, assets, authorization)` | `pt:third_party_burn` | Burns `assets` out of the named holder UTxO (`txHash#index`), authorised by the token's third-party logic and minting logic (`ThirdPartyBurnAuthorization`: one redeemer each). Literal `policy_id` only. See "Third-party actions" below. |
| `register(name, registration, redeemer)` | `pt:register` | Publishes a named policy for mints in the same transaction, wherever they are written. |
| `updateRegistry(policy, update, authorization)` | `pt:update_registry` | Must be its own transaction. |
| `unfrack(policy, authorization)` | `pt:unfrack` | Reserved for future support. Throws `UnsupportedOperationException`; persisted intents are rejected during validation. |

Authoring models (`Tx`, `TxPlan`, `QuickTxBuilder.TxContext`) are mutable and not thread-safe,
but a plan can be built again sequentially: extension-generated intents are a build-local overlay
and never change the authored plan. Services and extension descriptors are safe to share between
independent concurrent builds.

Reference inputs work as usual. The adapter adds the protocol's own at build time (coordination
UTxO, the policy's registry node, global state when present, and the published scripts it needs),
so no verb takes them as arguments. Anything the caller adds with `Tx.readFrom(...)` or a
`reference_input` intent in a plan lands in the same body; naming a UTxO the protocol already reads
adds nothing twice, and every CIP-113 redeemer index is computed over the ledger-sorted union.

## Third-party actions

`thirdPartyTransfer` (seize) and `thirdPartyBurn` act on someone else's smart wallet without the
holder's signature. The token's `third_party_transfer_logic_script` decides who may do that, and a
burn additionally runs its `minting_logic_script`.

- **Signer.** Neither verb declares a signer. Whatever key the token's third-party or minting logic
  checks (an admin or power-user key) is the caller's to add with `withRequiredSigners(...)` and to
  sign with.
- **Anyone, if the logic says so.** A token whose third-party logic always succeeds lets *anyone*
  seize or burn *anyone's* tokens of it. That is the token's design, not something this library can
  refuse.
- **The burnt UTxO is named.** `holderUtxoRef` must be unspent, sit at a smart wallet of the
  configured deployment, hold the policy and cover the assets, and carry no datum hash. A holder UTxO
  carrying a reference script cannot be third-party burned through CCL. Everything else in it (other
  asset names, other policies, lovelace, the inline datum) stays in a continuing output at the same
  address; a pinned UTxO is never picked by a seizure's coin selection.
- **Composition.** Any mix of seizures and third-party burns of **one policy**, from any number of
  holders, shares one transaction, as may `mint` and `register`. The `third_party` redeemer names a
  single registry node, so a second policy is refused, and owner `transfer`, `burn` and `unfrack`
  are refused because the dispatcher authorises one action per transaction. Also refused: the same
  UTxO named twice; different redeemers for one logic script (third-party, minting, or one
  credential serving both); a registration whose covering registry node is the acted policy's node;
  a mint and a burn of the same asset name that net to zero (otherwise they net into one mint
  entry); and a seizure back into the very wallet it seizes from.
- **Output order.** The continuing outputs are the last base-script outputs of the transaction, one
  per acted-on input in ledger input order; destinations and minted outputs precede them. An output
  at a smart-wallet address authored in the same `Tx` lands before them and is fine; one from a later
  `compose(...)` fragment would land after them and is refused.

## Limitations of the CIP-113 adapter

- Verification-key transfer, third-party and unfracking credentials are not invoked; registrations
  and registry updates naming one are refused so a token cannot be created that this library could
  not operate. The empty-key sentinel that forbids unfracking is data and remains valid.
- Unfracking is temporarily unsupported, regardless of the deployment's dispatcher or registry hook.
- Protocol-upgrade and authority operations (`ProtocolUpgrade`, `NominateAuthority`,
  `PromoteAuthority`) are not authored by this module.
- A third-party transaction acts on one policy, because the `third_party` redeemer names one
  registry node. Composition rules apply to the whole ledger transaction: every programmable-token
  operation of one transaction must be authored in a single `Tx` (fragments composed with
  `compose(tx1, tx2)` are validated together and refused before any lookup). A
  third-party action may share a transaction with an unrelated native-asset mint: its validator
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
