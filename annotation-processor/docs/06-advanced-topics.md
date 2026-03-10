# Advanced Configuration

This document covers advanced usage patterns, configuration options, and edge cases for the blueprint annotation processor.

## Compiler Options

### Disabling the Shared Type Registry

By default, the annotation processor uses any `BlueprintTypeRegistry` implementations found on the classpath (e.g., from `plutus-aiken`). To disable this and generate all types from scratch:

```groovy
compileJava {
    options.compilerArgs += ['-Acardano.registry.enable=false']
}
```

This is useful for:
- Debugging generated code
- Projects that don't use Aiken stdlib types
- Verifying that full generation works correctly

## Multiple Blueprints in One Project

You can have multiple `@Blueprint`-annotated interfaces, each pointing to a different blueprint JSON:

```java
@Blueprint(fileInResources = "blueprint/contract-a.json",
           packageName = "com.example.contracta")
@ExtendWith(LockUnlockValidatorExtender.class)
@AikenStdlib(AikenStdlibVersion.V3)
public interface ContractABlueprint { }

@Blueprint(fileInResources = "blueprint/contract-b.json",
           packageName = "com.example.contractb")
@ExtendWith(MintValidatorExtender.class)
@AikenStdlib(AikenStdlibVersion.V2)
public interface ContractBBlueprint { }
```

Each blueprint generates its own set of classes under its specified package. When using `plutus-aiken`, common types like `Credential` and `Address` are shared across all blueprints.

## Supported Plutus Data Type Mapping

Complete mapping of Plutus/Aiken types to Java:

| Plutus / Aiken Type | CIP-57 Schema | Java Type |
|---|---|---|
| `ByteArray` | `"dataType": "bytes"` | `byte[]` |
| `Int` | `"dataType": "integer"` | `BigInteger` |
| `String` | `"dataType": "#string"` | `String` |
| `Bool` | Constructor with True(0)/False(1) | `boolean` |
| `List<T>` | `"dataType": "list", "items"` | `java.util.List<T>` |
| `Map<K,V>` | `"dataType": "map", "keys", "values"` | `java.util.Map<K,V>` |
| `Option<T>` | `"anyOf"` with Some(0)/None(1) | `java.util.Optional<T>` |
| `Data` (opaque) | `"title": "Data"` | `PlutusData` |
| Named constructor | `"dataType": "constructor"` | Generated class |
| Tagged union | `"anyOf"` with multiple constructors | Interface + variant classes in sub-package |
| Fieldless union | `"anyOf"` with all-empty constructors | Java `enum` |

## Tuple Support

Fixed-size tuples in blueprints (encoded as lists with typed items) map to generic tuple classes:

| Items | Java Type |
|---|---|
| 2 | `Pair<A, B>` |
| 3 | `Triple<A, B, C>` |
| 4 | `Quartet<A, B, C, D>` |
| 5 | `Quintet<A, B, C, D, E>` |

Tuples with 6 or more items are rejected during annotation processing.

Example — a 2-tuple of `(ByteArray, Int)` generates a field of type `Pair<byte[], BigInteger>`.

## Optional / Option Handling

Aiken's `Option<T>` maps to `java.util.Optional<T>`. The encoding uses the standard Plutus convention:

- `Some(value)` → `ConstrPlutusData` with alternative `0` and the value as a field
- `None` → `ConstrPlutusData` with alternative `1` and no fields

The generated converter handles this automatically:

```java
// Setting an optional field
myDatum.setSomeField(Optional.of(value));
myDatum.setSomeField(Optional.empty());

// Reading an optional field
Optional<String> maybeValue = myDatum.getSomeField();
```

## Working with RawData Types

Types implementing `RawData` serialize to raw `PlutusData` rather than being wrapped in a `ConstrPlutusData`. Common examples:

- All byte array wrapper types (`VerificationKeyHash`, `PolicyId`, `AssetName`, etc.)
- Types that serialize to `BytesPlutusData` directly

```java
// RawData types serialize differently
VerificationKeyHash vkh = VerificationKeyHash.of(hashBytes);
PlutusData raw = vkh.toPlutusData();  // Returns BytesPlutusData, not ConstrPlutusData

// Regular Data types always return ConstrPlutusData
Owner owner = new OwnerData();
ConstrPlutusData constr = owner.toPlutusData();  // Returns ConstrPlutusData
```

## Using file Path Instead of Resources

If your blueprint JSON is not in the resources directory, you can specify an absolute file path:

```java
@Blueprint(file = "/path/to/my/blueprint.json",
           packageName = "com.example.mycontract")
public interface MyBlueprint { }
```

Use `fileInResources` for files in `src/main/resources` (recommended) or `file` for absolute paths.

## Generated Validator Class Name

The validator class name is derived from the validator title in the blueprint JSON:

| Blueprint Title | Generated Class |
|---|---|
| `helloworld.hello_world` | `HelloWorldValidator` |
| `mint_validator.mint` | `MintValidator` |
| `my_module.my_validator` | `MyValidatorValidator` |

The naming strategy:
1. Splits the title by `.`
2. Uses the last segment as the base name (skipping the module prefix if it would create a stutter)
3. Applies PascalCase
4. Appends `Validator` suffix

## Working with PlutusData Directly

You can always bypass the generated types and work with raw `PlutusData`:

```java
// Use typed datum
Owner datum = new OwnerData();
datum.setOwner(keyHash);

// Or use raw PlutusData
ConstrPlutusData rawDatum = ConstrPlutusData.of(0,
    BytesPlutusData.of(keyHash));

// Both work with the validator
validator.lock(address, amount, datum);
validator.unlockToContract(datum, redeemer, scriptAddress, rawDatum);
```

## Error Handling

Transaction operations return `Result<String>`:

```java
var result = validator.lock(address, amount, datum)
        .feePayer(account.baseAddress())
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

if (result.isSuccessful()) {
    String txHash = result.getValue();
    System.out.println("Transaction submitted: " + txHash);
} else {
    System.err.println("Transaction failed: " + result.getResponse());
}
```

## Next Steps

- [Getting Started](01-getting-started.md) — initial setup
- [Understanding Generated Code](02-generated-code.md) — generated class hierarchy
- [Working with Validators](03-using-validators.md) — lock/unlock and minting workflows
- [Shared Types and plutus-aiken](04-shared-types-and-plutus-aiken.md) — type sharing across contracts
- [Blueprint JSON Format](05-blueprint-json-format.md) — understanding the input format
