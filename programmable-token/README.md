# Programmable Token

`cardano-client-programmable-token` adds protocol-neutral programmable-token intents to QuickTx.
CIP-113 is the initial protocol adapter and has been tested against reference contract suite
`0.5.0-alpha.2`.

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
and deployment metadata. Optional `contract_version` is informational provenance and is preserved,
but it is not currently used for dispatch or compatibility validation. Ordinary
`Tx.payToAddress(...)` never performs programmable-token routing. Use
`programmableTokens.txPlanCodec("tokens")` when a document needs a custom namespace; use the generic
`TxPlanCodec` builder when combining multiple extensions.
