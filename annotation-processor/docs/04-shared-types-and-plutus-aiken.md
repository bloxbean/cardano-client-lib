# Shared Types and the plutus-aiken Library

When working with multiple Aiken smart contracts, you'll often encounter common types from the Aiken standard library — `Credential`, `Address`, `OutputReference`, etc. Without any special handling, each blueprint generates its own copy of these types, leading to duplicate and incompatible classes.

The `plutus-aiken` module solves this by providing pre-built, reusable Java implementations of Aiken stdlib types.

## The Problem

Consider two contracts that both use `Credential` in their datums:

```
contract-a/blueprint.json → generates com.a.model.Credential
contract-b/blueprint.json → generates com.b.model.Credential
```

These are structurally identical but incompatible Java types. You can't pass one where the other is expected.

## The Solution

Add the `plutus-aiken` dependency and the annotation processor will automatically reuse shared types instead of generating duplicates:

```groovy
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-lib-plutus-aiken:0.8.0'
    annotationProcessor 'com.bloxbean.cardano:cardano-client-lib-plutus-aiken:0.8.0'
}
```

That's it. The `plutus-aiken` module uses Java's [ServiceLoader (SPI)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html) mechanism to register itself automatically. No additional configuration needed.

### With vs. Without plutus-aiken

| Scenario | Behavior |
|---|---|
| **With** `plutus-aiken` | Common Aiken stdlib types are resolved from the library. Only contract-specific types are generated. |
| **Without** `plutus-aiken` | All types are fully generated from the blueprint. Everything still works — you just get more generated code and no type sharing across contracts. |

## Provided Shared Types

All shared types live in `com.bloxbean.cardano.client.plutus.aiken.blueprint.std`:

### Byte Array Wrappers

These types wrap a `byte[]` and implement `RawData`. They serialize directly to `BytesPlutusData`.

| Class | Description |
|---|---|
| `VerificationKey` | Ed25519 verification key |
| `Script` | Script bytes |
| `Signature` | Ed25519 signature |
| `VerificationKeyHash` | Hash of a verification key |
| `ScriptHash` | Hash of a script |
| `DataHash` | Hash of a datum |
| `Hash` | Generic hash |
| `PolicyId` | Minting policy ID |
| `AssetName` | Token asset name |

Usage:

```java
VerificationKeyHash vkh = VerificationKeyHash.of(hashBytes);
PlutusData plutusData = vkh.toPlutusData();
VerificationKeyHash vkh2 = VerificationKeyHash.fromPlutusData(bytesPlutusData);
```

### Credential Types

| Class | Description |
|---|---|
| `Credential` | V1-style credential (VerificationKeyCredential / ScriptCredential) |
| `PaymentCredential` | V2/V3-style payment credential (VerificationKey / Script) |
| `StakeCredential` | Stake credential |
| `ReferencedCredential` | Referenced (inline) credential wrapper |

Usage:

```java
// Create a verification key credential
Credential cred = Credential.verificationKey(keyHashBytes);

// Create a script credential
Credential cred = Credential.script(scriptHashBytes);

// Deserialize from PlutusData
Credential cred = Credential.fromPlutusData(constrPlutusData);
```

### Complex Types

| Class | Description |
|---|---|
| `Address` | Full Cardano address (payment credential + optional stake credential) |
| `OutputReference` | Transaction output reference (tx ID + output index) |
| `OutputReferenceV1` | V1-style output reference (nested TransactionId wrapper) |
| `IntervalBound` | Validity interval bound |
| `IntervalBoundType` | Interval bound type (NegativeInfinity / Finite / PositiveInfinity) |
| `ValidityRange` | Full validity range interval |

Usage:

```java
OutputReference ref = OutputReference.of(txIdBytes, BigInteger.valueOf(0));
ConstrPlutusData plutusData = ref.toPlutusData();
```

## Aiken Stdlib Versions

Different versions of the Aiken standard library produce different schema signatures for the same logical type. For example, `Credential` has different constructor layouts in stdlib v1 vs. v2 vs. v3.

Declare the version your contract was compiled with using the `@AikenStdlib` annotation:

```java
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlib;
import com.bloxbean.cardano.client.plutus.aiken.annotation.AikenStdlibVersion;

@Blueprint(fileInResources = "blueprint/mycontract.json",
           packageName = "com.example.mycontract")
@AikenStdlib(AikenStdlibVersion.V2)
public interface MyContractBlueprint {
}
```

### Version Ranges

| Version Enum | Aiken stdlib Range |
|---|---|
| `AikenStdlibVersion.V1` | stdlib >= 1.9.0, < 2.0.0 |
| `AikenStdlibVersion.V2` | stdlib >= 2.0.0, < 3.0.0 |
| `AikenStdlibVersion.V3` | stdlib >= 3.0.0 (latest, default) |

If you omit `@AikenStdlib`, the default is `V3`.

### How to Determine Your Version

Check your Aiken project's `aiken.toml` for the stdlib dependency version:

```toml
[[dependencies]]
name = "aiken-lang/stdlib"
version = "2.2.0"    # This is V2
```

## How the Registry Works

Under the hood:

1. The `plutus-aiken` JAR contains a `META-INF/services/com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry` file that registers `AikenBlueprintTypeRegistry`.
2. During annotation processing, the processor discovers the registry via ServiceLoader.
3. For each definition in the blueprint, the processor computes a **schema signature** and checks whether the registry has a matching pre-built type.
4. If a match is found, the processor generates a converter that uses the shared type instead of generating a new model class.
5. The `@AikenStdlib` annotation provides a version hint so the registry can match version-specific schemas correctly.

## Disabling the Registry

If you want to force full code generation even when `plutus-aiken` is on the classpath:

```groovy
compileJava {
    options.compilerArgs += ['-Acardano.registry.enable=false']
}
```

This is useful for debugging or when you need to inspect the full generated code.

## Next Steps

- [Understanding Generated Code](02-generated-code.md) — learn about the generated class hierarchy
- [Working with Validators](03-using-validators.md) — use generated validators for transactions
- [Advanced Topics](06-advanced-topics.md) — configuration options and edge cases
