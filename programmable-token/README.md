# Programmable Token

`cardano-client-programmable-token` adds protocol-neutral programmable-token intents to QuickTx.
CIP-113 (`0.5.0-alpha.2`) is the initial protocol adapter.

```java
Cip113ProgrammableTokenService programmableTokens =
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
TxPlanCodec codec = TxPlanCodec.builder().withExtension("pt", extension).build();
String yaml = codec.toYaml(plan);

TxPlan replay = codec.fromYaml(yaml);
new QuickTxBuilder(backend).withExtension(extension).compose(replay).build();
```

The namespace `pt` is document-local. Persisted plans also pin extension id, schema version,
protocol, contract version, and deployment metadata. Ordinary `Tx.payToAddress(...)` never performs
programmable-token routing.
