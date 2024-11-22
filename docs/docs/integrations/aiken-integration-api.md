---
description: Aiken Integration Apis
sidebar_label: Aiken Integration
sidebar_position: 1
---

# Overview

Using [Aiken Java Binding](https://github.com/bloxbean/aiken-java-binding) provides many useful functionalities, such as "off-chain" script cost evaluation and compiling parameterized contracts. Aiken Java Binding offers a JNI wrapper around the Aiken library, making it easier to integrate with Java-based applications.

## Dependencies

To use Aiken Java Binding in your project, include the following dependency in your build configuration.

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>com.bloxbean.cardano</groupId>
    <artifactId>aiken-java-binding</artifactId>
    <version>0.0.8</version>
</dependency>
```

### Gradle (`build.gradle`)

```groovy
implementation 'com.bloxbean.cardano:aiken-java-binding:${version}'
```

## Script Cost Evaluation

Aiken Java Binding provides an implementation of the `TransactionEvaluator` interface, known as `AikenTransactionEvaluator`, which is used to evaluate the cost of a script. Since it implements the CCL's `TransactionEvaluator` interface, you can use it directly with the QuickTx builder.

### Example Usage

```java
Result<String> result1 = quickTxBuilder.compose(scriptTx)
                .feePayer(address)
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(new AikenTransactionEvaluator(utxoSupplier, protocolParamsSupplier))
                .completeAndWait(System.out::println);
```

## Compile Parameterized Contract

Aiken Java Binding includes a utility class, `AikenScriptUtil`, which can be used to compile a parameterized script. The parameters needed for the script are passed as a `ListPlutusData` object.

The final compiled code can then be converted into a `PlutusScript` object using the `PlutusBlueprintUtil.getPlutusScriptFromCompiledCode` method.

### Example Usage

```java
PlutusData outputRef = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString("1f3f766bc864c3f8ce8ccc20716e3f3cf65f08a819073c75875ea4e67549947f")),
                BigIntPlutusData.of(0));
ListPlutusData params = ListPlutusData.of(BytesPlutusData.of("MyToken"), outputRef);

String contractCompiledCode = "590221010000323232323232323232323223222232533300b32323232533300f3370e9000180700089919191919191919191919299980e98100010991919299980e99b87480000044c94ccc078cdc3a4000603a002264a66603e66e1c011200213371e00a0322940c07000458c8cc004004030894ccc088004530103d87a80001323253330213375e6603a603e004900000d099ba548000cc0940092f5c0266008008002604c00460480022a66603a66e1c009200113371e00602e2940c06c050dd6980e8011bae301b00116301e001323232533301b3370e90010008a5eb7bdb1804c8dd59810800980c801180c800991980080080111299980f0008a6103d87a8000132323232533301f3371e01e004266e95200033023374c00297ae0133006006003375660400066eb8c078008c088008c080004c8cc004004008894ccc07400452f5bded8c0264646464a66603c66e3d221000021003133022337606ea4008dd3000998030030019bab301f003375c603a0046042004603e0026eacc070004c070004c06c004c068004c064008dd6180b80098078029bae3015001300d001163013001301300230110013009002149858c94ccc02ccdc3a40000022a66601c60120062930b0a99980599b874800800454ccc038c02400c52616163009002375c0026600200290001111199980399b8700100300c233330050053370000890011807000801001118029baa001230033754002ae6955ceaab9e5573eae815d0aba201";
String applyParamCompiledCode = AikenScriptUtil.applyParamToScript(params, contractCompiledCode);

// Convert Aiken compiled code to PlutusScript
PlutusScript plutusScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(applyParamCompiledCode, PlutusVersion.v2);
```
