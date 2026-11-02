---
title: "Scalus Integration"
description: "Evaluate script costs locally and apply script parameters with Scalus."
---

[Scalus](https://github.com/scalus3/scalus) compiles a subset of Scala to Plutus Core, the language of Cardano smart contracts. Its Cardano Client Lib integration also works from plain Java, where you can use it to evaluate script costs locally and to apply parameters to compiled scripts.

Scalus runs on the JVM and bundles native libraries for macOS, Windows and Linux, including ARM64 Linux, which [Aiken Java Binding](/smart-contracts/aiken/) doesn't support.

## Dependencies

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>org.scalus</groupId>
    <artifactId>scalus-bloxbean-cardano-client-lib_3</artifactId>
    <version>1.4.1</version>
</dependency>
```

### Gradle (`build.gradle`)

```groovy
implementation 'org.scalus:scalus-bloxbean-cardano-client-lib_3:1.4.1'
```

This Scalus release declares a dependency on Cardano Client Lib 0.7.3. We tested it with the [latest preview](/start-here/installation/#versions) on a local devnet and on Preview. Gradle picks the higher version by itself. With Maven, declare `cardano-client-lib` directly in your `pom.xml` so that your version wins.

## Script Cost Evaluation

`ScalusTransactionEvaluator` implements the library's `TransactionEvaluator` interface, so you can pass it directly to the QuickTx builder.

```java
ProtocolParams params = backendService.getEpochService().getProtocolParameters().getValue();
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());

TxResult result = quickTxBuilder.compose(tx)
        .feePayer(address)
        .withSigner(SignerProviders.signerFrom(account))
        .withTxEvaluator(new ScalusTransactionEvaluator(SlotConfig.preview(), params, utxoSupplier))
        .completeAndWait();
```

`SlotConfig` is `scalus.cardano.ledger.SlotConfig`, and it also has `mainnet()` and `preprod()`. A script needs the right one to read a transaction's validity range correctly.

If the transaction uses reference scripts, add a script supplier so that the evaluator can read them:

```java
new ScalusTransactionEvaluator(SlotConfig.preview(), params, utxoSupplier,
        new ScriptServiceSupplier(backendService.getScriptService()))
```

See [Calling a Plutus script](/learn/plutus-scripts/) for a complete example.

## Apply Script Parameters

`ScalusScriptUtils.applyParamsToScript` applies parameters to a script. It takes and returns **double-encoded** CBOR hex, which is what `PlutusScript.getCborHex()` holds. A blueprint's `compiledCode` is single-encoded, so load it as a script first:

```java
PlutusScript unapplied = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(compiledCode, PlutusVersion.v3);

String applied = ScalusScriptUtils.applyParamsToScript(unapplied.getCborHex(), BytesPlutusData.of(issuerKeyHash));

PlutusV3Script script = PlutusV3Script.builder()
        .type("PlutusScriptV3")
        .cborHex(applied)
        .build();
```

The method also accepts a `ListPlutusData`. For the same inputs, we confirmed that the result matches `AikenScriptUtil.applyParamToScript`.
