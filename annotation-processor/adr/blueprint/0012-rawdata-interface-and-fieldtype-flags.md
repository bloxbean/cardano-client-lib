# ADR 0012: RawData Interface and FieldType Flags

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0003 (Shared Blueprint Type Registry), ADR-0009 (Shared Type Converter Architecture), CIP-57

## Context

Shared types in the blueprint processor fall into two categories based on their on-chain encoding:

1. **Constr-based types** (e.g., `Address`, `Credential`) — serialized as `ConstrPlutusData` with constructor index and fields.
2. **Bytes-wrapper types** (e.g., `VerificationKeyHash`, `ScriptHash`) — serialized as raw `BytesPlutusData`, not wrapped in a constructor.

The original implementation used FQN string scanning to detect shared types, which was fragile and required maintaining lists of known type names. The converter generator also lacked a clean way to distinguish between these two encoding strategies, leading to incorrect casts (e.g., casting `BytesPlutusData` to `ConstrPlutusData`).

## Decision

Introduce two marker interfaces and corresponding `FieldType` flags for clean, interface-based shared type detection.

### Marker Interfaces

**`Data<T>`** — `com.bloxbean.cardano.client.plutus.blueprint.model.Data`
```java
public interface Data<T> {
    ConstrPlutusData toPlutusData();
}
```
Implemented by constr-based shared types. The type parameter `T` enables typed `fromPlutusData(ConstrPlutusData)` static methods.

**`RawData`** — `com.bloxbean.cardano.client.plutus.blueprint.model.RawData`
```java
public interface RawData {
    PlutusData toPlutusData();
}
```
Implemented by bytes-wrapper shared types. Returns `PlutusData` (not `ConstrPlutusData`) because the on-chain representation is raw bytes.

### FieldType Flags

`FieldType` gains two boolean flags:

| Flag | Set When | Effect on Serialization | Effect on Deserialization |
|------|----------|------------------------|--------------------------|
| `dataType` | Type implements `Data<T>` and is NOT `@Constr`-annotated | `obj.getField().toPlutusData()` | `Type.fromPlutusData((ConstrPlutusData) data)` |
| `rawDataType` | Type implements `RawData` | `obj.getField().toPlutusData()` | `new TypeConverter().fromPlutusData(data)` (no cast) |

**`isSharedType()`** — convenience method: `return dataType || rawDataType`

### Detection Mechanism

`ClassDefinitionGenerator` detects these types using the annotation processing type system:

```java
boolean isDataType(TypeMirror type) {
    return typeUtils.isAssignable(
        typeUtils.erasure(type),
        typeUtils.erasure(dataInterfaceElement.asType())
    ) && type.getAnnotation(Constr.class) == null;
}

boolean isRawDataType(TypeMirror type) {
    return typeUtils.isAssignable(
        typeMirror,
        rawDataInterfaceElement.asType()
    );
}
```

The `@Constr` exclusion in `isDataType()` is critical: `@Constr`-annotated classes have their own generated converters and should not be treated as shared types.

### Impact on Converter Generation

The `ConverterCodeGenerator` uses `FieldType.isSharedType()` to choose the serialization strategy:

- **Shared types** (`isSharedType() == true`): inline `toPlutusData()` and `fromPlutusData()` calls directly on the field value. No generated converter is used.
- **Regular types**: delegate through the generated `*Converter` class.
- **Raw data types** specifically: deserialization passes the raw `PlutusData` object without casting to `ConstrPlutusData`.

## Rationale

1. **Interface-based detection** (`typeUtils.isAssignable()`) is cleaner and more maintainable than FQN string scanning. Adding a new shared type only requires implementing the appropriate interface.
2. **Two separate interfaces** reflect the genuine encoding difference: `ConstrPlutusData` vs raw `PlutusData`. A single interface would lose this distinction.
3. **The `@Constr` exclusion** prevents double-processing: `@Constr` classes already have their own generated converter infrastructure.

## Consequences

### Positive
- Clean, type-safe shared type detection replaces fragile string-based checks.
- Correct serialization for both constr-based and bytes-wrapper shared types.
- Adding new shared types requires only implementing `Data<T>` or `RawData` — no processor changes needed.
- `isSharedType()` convenience method simplifies conditional logic in generators.

### Negative
- Shared types must explicitly implement the appropriate marker interface.
  - **Mitigation**: This is a one-time setup per shared type and makes the encoding contract explicit.
- The `@Constr` exclusion check adds a subtle coupling between the detection logic and the annotation system.

## References

- `RawData` — bytes-wrapper marker interface (`plutus` module)
- `Data<T>` — constr-based marker interface (`plutus` module)
- `FieldType` — field type model with `rawDataType`, `dataType`, `isSharedType()` flags
- `ClassDefinitionGenerator.isDataType()`, `isRawDataType()` — detection methods
- `ConverterCodeGenerator` — serialization strategy selection
- ADR-0003: Shared Blueprint Type Registry
- ADR-0009: Shared Type Converter Architecture
