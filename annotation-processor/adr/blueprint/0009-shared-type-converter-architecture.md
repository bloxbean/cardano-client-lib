# ADR 0009: Shared Type Converter Architecture

- Status: Accepted
- Date: 2026-02-25
- Owners: Cardano Client Lib maintainers
- Related: ADR-0003 (Shared Blueprint Type Registry), ADR-0017 (Aiken Stdlib Version Hints SPI)

## Context

When the Shared Blueprint Type Registry (ADR-0003) maps a schema to a shared type (e.g., `Address`, `VerificationKeyHash`), the annotation processor still needs to generate **converter classes** that bridge between the shared type and its PlutusData serialization. Without converters, consumers would need to manually serialize/deserialize shared types.

The challenge is that shared types already implement their own `toPlutusData()` and `fromPlutusData()` methods. The converter must not duplicate this logic — it should delegate to the shared type's existing methods while conforming to the standard converter interface (`BasePlutusDataConverter`).

Additionally, different shared types use fundamentally different PlutusData representations:
- **Constructor-based** types (e.g., `Address`, `Credential`) serialize to `ConstrPlutusData`
- **Bytes-wrapper** types (e.g., `VerificationKeyHash`, `ScriptHash`) serialize to `BytesPlutusData`
- **Pair** types (e.g., `Pair<byte[], byte[]>`) serialize to `ListPlutusData` with two `BytesPlutusData` elements

> **Note (ADR-0017)**: Most list schemas with 2 items now produce parameterized `Pair<T1, T2>` via `SchemaTypeResolver.resolveListType()` at the type resolution layer, bypassing the registry entirely. The `PAIR` kind in `SharedTypeKind` and the `Pair` entry in the registry's `commonMappings` remain for backward compatibility with blueprints whose two-ByteArray tuple schema signature matches the registered `Pair` type.

## Decision

Generate **thin wrapper converters** that delegate to the shared type's own serialization methods. The converter shape is determined by a `SharedTypeKind` enum.

### SharedTypeKind

```java
public enum SharedTypeKind {
    CONSTRUCTOR,  // Address, Credential, PaymentCredential, etc. → ConstrPlutusData
    BYTES,        // VerificationKeyHash, ScriptHash, Hash, PolicyId, AssetName, etc. → PlutusData (BytesPlutusData)
    PAIR          // Pair (two-ByteArray tuple) → ListPlutusData (see note above)
}
```

### Kind Detection

`SharedTypeKind.kindOf(BlueprintSchema schema)` resolves the schema (following `$ref` if present) and determines the kind:

| Schema `dataType` | SharedTypeKind | PlutusData Type |
|-------------------|---------------|-----------------|
| `bytes` | `BYTES` | `PlutusData` (accepts `BytesPlutusData`) |
| `list` | `PAIR` | `PlutusData` (builds `ListPlutusData`) |
| anything else / `constructor` / `anyOf` | `CONSTRUCTOR` | `ConstrPlutusData` |

### Generated Methods (per kind)

Each converter generates 6 methods:

| Method | CONSTRUCTOR | BYTES | PAIR |
|--------|-------------|-------|------|
| `toPlutusData(T obj)` | Returns `ConstrPlutusData` via `obj.toPlutusData()` | Returns `PlutusData` via `obj.toPlutusData()` | Builds `ListPlutusData` from `obj.getFirst()` and `obj.getSecond()` |
| `fromPlutusData(...)` | Takes `ConstrPlutusData`, calls `T.fromPlutusData(constr)` | Takes `PlutusData`, calls `T.fromPlutusData(data)` | Takes `PlutusData`, casts to `ListPlutusData`, extracts two `BytesPlutusData` values |
| `serialize(T obj)` | CBOR-serializes via `CborSerializationUtil` | Same | Same |
| `serializeToHex(T obj)` | Hex via `toPlutusData().serializeToHex()` | Same | Same |
| `deserialize(byte[])` | CBOR-deserializes to `ConstrPlutusData`, then `fromPlutusData()` | CBOR-deserializes to `PlutusData`, then `fromPlutusData()` | Same as BYTES |
| `deserialize(String hex)` | Hex-decodes then calls `deserialize(byte[])` | Same | Same |

### Converter Placement

Converters are generated in a `converter` sub-package of the shared type's package:

```
<sharedType.package>.converter.<Name>Converter

Example:
  Shared type: com.bloxbean.cardano.client.plutus.aiken.blueprint.std.Address
  Converter:   com.bloxbean.cardano.client.plutus.aiken.blueprint.std.converter.AddressConverter
```

### Deduplication

Converters are tracked in `GeneratedTypesRegistry` to prevent duplicate generation. If a shared type's converter has already been generated (because multiple schemas map to the same shared type), subsequent encounters skip generation.

## Rationale

### Why thin wrappers?

Shared types already contain the conversion logic in their `toPlutusData()` and static `fromPlutusData()` methods. Duplicating this logic in converters would:
- Create a maintenance burden (two places to update when serialization changes).
- Risk divergence between the shared type's own serialization and the converter's.

Thin wrappers delegate entirely, ensuring a single source of truth for serialization logic.

### Why three kinds?

The three PlutusData representations are fundamentally different at the CBOR level:
- `ConstrPlutusData` has a constructor index and a list of fields.
- `BytesPlutusData` is a raw byte array.
- `ListPlutusData` is an ordered list of PlutusData items.

Using the wrong representation would produce invalid CBOR that Cardano nodes would reject. The kind system ensures type-safe serialization.

### Why not a single generic converter?

A single converter would need runtime type checks and casts, losing compile-time safety and making the generated code harder to understand and debug. Three dedicated shapes keep each converter simple and self-documenting.

## Implementation

### SharedTypeConverterGenerator

Located at `SharedTypeConverterGenerator.java`. Stateless generator that produces a `TypeSpec` given a `ClassName` and `SharedTypeKind`.

All generated converters:
- Extend `BasePlutusDataConverter`
- Have a `@Generated` annotation (via `GENERATED_CODE` constant)
- Are `public` classes
- PAIR converters additionally have `@SuppressWarnings("unchecked")` due to generic `Pair<byte[], byte[]>` casts

### Integration Point

`FieldSpecProcessor.generateSharedTypeConverter()` is called when a shared type is matched during field processing. It:
1. Determines `SharedTypeKind` via `SharedTypeConverterGenerator.kindOf(schema)`
2. Checks `GeneratedTypesRegistry` for duplicates
3. If not yet generated, calls `SharedTypeConverterGenerator.generate()` and writes the source file

## Consequences

### Positive
- Shared types get full converter support (serialize, deserialize, hex) without manual coding.
- Single source of truth for serialization logic (in the shared type itself).
- Type-safe: each kind produces the correct PlutusData representation at compile time.
- Deduplication prevents redundant source files.

### Negative
- Three converter shapes means three sets of method templates in the generator.
  - **Mitigation**: The templates are straightforward delegation code; complexity is minimal.
- Adding a new SharedTypeKind requires updating the generator's `switch` expression.

## References

- `SharedTypeConverterGenerator.java` — generator with `SharedTypeKind` enum and converter templates
- `FieldSpecProcessor.generateSharedTypeConverter()` — integration point
- `GeneratedTypesRegistry` — deduplication tracking
- ADR-0003: Shared Blueprint Type Registry (the registry that triggers converter generation)
- ADR-0017: Aiken Stdlib Version Hints SPI (parameterized Pair and version-aware lookup)
- `BasePlutusDataConverter` — base class for all converters
