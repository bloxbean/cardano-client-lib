---
description: Scalus Integration Apis
sidebar_label: Scalus Integration
sidebar_position: 2
---

# Scalus Integration

[Scalus](https://github.com/nau/scalus) compiles a subset of Scala code to Plutus Core, the language of Cardano smart contracts.
Scalus gives full control over the generated Plutus Core code. Write efficient and compact smart contracts and squeeze the most out of the Cardano blockchain.

It provides native integration with the Cardano Client Library. By using Scalus's Bloxbean integration, you can perform
script cost evaluation and compile parameterized contracts.

Since Scalus is written in Scala, a JVM language, it can be used on all supported platforms, including Linux arm64, which is not currently possible with Aiken Java Binding.

## Dependencies

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>org.scalus</groupId>
    <artifactId>scalus-bloxbean-cardano-client-lib_3</artifactId>
    <version>0.7.2</version>
</dependency>
```

### Gradle (`build.gradle`)

```groovy
implementation 'org.scalus:scalus-bloxbean-cardano-client-lib_3:0.7.2'
```

## Script Cost Evaluation

Scalus Bloxbean integration provides `ScalaTransactionEvaluator`, which can be used to evaluate the cost of a script.
Since it implements the CCL's `TransactionEvaluator` interface, you can use it directly with the QuickTx builder.

```java
var signedTx = quickTxBuilder
  .compose(scriptTx)
  .withTxEvaluator(ScalusTransactionEvaluator(protocolParams, utxoSupplier))
  // build your transaction
  .buildAndSign();
```

## Compile Parameterized Contract

Similar to Aiken Java Binding, Scalus Bloxbean integration provides api which can be used to 
compile a parameterized script. The parameters required for the script are passed as a `ListPlutusData` object.

You can create a utility class to apply these parameters to the compiled code from a Plutus Blueprint JSON file.

```java
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import scalus.uplc.Program;
import scalus.uplc.Term;
import scalus.uplc.Constant;

public class ScalusUtils {
    public static String applyParamToScript(ListPlutusData params, String compiledCode) {
        var program = Program.fromCborHex(compiledCode);
        for (var p : params.getPlutusDataList()) {
            var scalusData = Interop.toScalusData(p);
            var term = Term.Const.apply(Constant.Data.apply(scalusData));
            program = program.applyArg(term);
        }
        return HexUtil.encodeHexString(program.cborEncoded());
    }
}
```

### Apply Parameters to Double Encoded CBOR Hex

If you have a **double-encoded CBOR hex** string of the compiled code, you can use the following method to apply the parameters.
The utility method below will apply the parameters to the double-encoded CBOR hex string and return the updated double-encoded CBOR hex string.

```java
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import scalus.uplc.Program;
import scalus.uplc.Term;
import scalus.uplc.Constant;

public class ScalusUtils {
    public static String applyParamToScript(ListPlutusData params, String compiledCode) {
        var program = Program.fromDoubleCborHex(compiledCode);
        for (var p : params.getPlutusDataList()) {
            var scalusData = Interop.toScalusData(p);
            var term = Term.Const.apply(Constant.Data.apply(scalusData));
            program = program.applyArg(term);
        }
        return program.doubleCborHex();
    }
}
```
