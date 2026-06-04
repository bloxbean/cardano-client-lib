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
    implementation 'com.bloxbean.cardano:cardano-client-lib-plutus-aiken:${ccl.version}'
    annotationProcessor 'com.bloxbean.cardano:cardano-client-lib-plutus-aiken:${ccl.version}'
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
| `PaymentCredential` | Payment credential (VerificationKey / Script) |
| `StakeCredential` | Stake credential (Inline / Pointer) |

Usage:

```java
// Create a verification key credential
PaymentCredential cred = PaymentCredential.verificationKey(keyHashBytes);

// Create a script credential
PaymentCredential cred = PaymentCredential.script(scriptHashBytes);

// Deserialize from PlutusData
PaymentCredential cred = PaymentCredential.fromPlutusData(constrPlutusData);
```

### Complex Types

| Class | Description |
|---|---|
| `Address` | Full Cardano address (payment credential + optional stake credential) |
| `OutputReference` | Transaction output reference (tx ID + output index) |
| `IntervalBound` | Validity interval bound |
| `IntervalBoundType` | Interval bound type (NegativeInfinity / Finite / PositiveInfinity) |
| `ValidityRange` | Full validity range interval |

Usage:

```java
OutputReference ref = OutputReference.of(txIdBytes, BigInteger.valueOf(0));
ConstrPlutusData plutusData = ref.toPlutusData();
```

## Aiken Stdlib Compatibility

The registry targets the latest Aiken standard library (stdlib v3.x — verified against 3.0 and 3.1). Older stdlib versions (v1/v2) are no longer supported; blueprints compiled with those versions need to be re-emitted with a modern Aiken compiler.

The `@AikenStdlib(AikenStdlibVersion.V3)` annotation exists as a source-compatibility marker and a future extension point. It currently has only the single value `V3` (= `LATEST`) and is a no-op for the processor — you do not need to add it to your blueprint interface. If a future stdlib version introduces breaking changes, the annotation will be promoted to load-bearing then.

## How the Registry Works

Under the hood:

1. The `plutus-aiken` JAR contains a `META-INF/services/com.bloxbean.cardano.client.plutus.blueprint.registry.BlueprintTypeRegistry` file that registers `AikenBlueprintTypeRegistry`.
2. During annotation processing, the processor discovers the registry via ServiceLoader.
3. For each definition in the blueprint, the processor computes a **schema signature** and checks whether the registry has a matching pre-built type.
4. If a match is found, the processor generates a converter that uses the shared type instead of generating a new model class.

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
