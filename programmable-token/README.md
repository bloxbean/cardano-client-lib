# Programmable Token

`cardano-client-programmable-token` adds protocol-neutral programmable-token intents to QuickTx.
CIP-113 is the initial protocol adapter and has been tested against reference contract suite
`0.5.0-alpha.2` (see `src/it/resources/blueprint/cip113/compatibility.yml` for the pinned snapshot).

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
and deployment metadata (`network`, `bootstrap_tx`), which are validated against the configured
deployment before any chain access. Optional `contract_version` is informational provenance and
is preserved, but it is not used for dispatch or compatibility validation. Ordinary
`Tx.payToAddress(...)` never performs programmable-token routing. Use
`programmableTokens.txPlanCodec("tokens")` when a document needs a custom namespace; use the generic
`TxPlanCodec` builder when combining multiple extensions.

## Operations

| Verb | YAML type | Notes |
|------|-----------|-------|
| `transfer(receiver, amount, redeemer[, datum])` | `pt:transfer` | Owner transfer to a smart wallet; optional bounded inline datum on the receiving output. |
| `mint(policy, receiver, assets, redeemer, datum)` | `pt:mint` | Policy may be a literal id or the name of a registration in the same plan. |
| `burn(policy, assets, authorization)` | `pt:burn` | Transfer and issuance redeemers are distinct; every burn of one policy in a transaction must agree on both. |
| `thirdPartyTransfer(holder, receiver, amount, redeemer)` | `pt:third_party_transfer` | One holder and one policy per transaction; many outputs for that policy aggregate. |
| `register(name, registration, redeemer)` | `pt:register` | Publishes a named policy for later intents in the same plan. |
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
- A third-party transaction acts on one policy, because the `third_party` redeemer names one
  registry node. Composition rules apply to the whole ledger transaction: every programmable-token
  operation of one transaction must be authored in a single `Tx` (fragments composed with
  `compose(tx1, tx2)` are validated together and refused before any lookup), and an unfracking
  cannot share a transaction with any mint or burn because its validator requires an empty mint. A
  third-party transfer may share a transaction with an unrelated native-asset mint: its validator
  reads only the acted policy's mint and pins only the paired smart-wallet outputs. Owner transfers
  of several policies, with or without a native mint, compose in one `Tx`.
- Programmable outputs hold one policy each; inline datums are bounded by the deployment's
  `max_inline_datum_bytes`.
