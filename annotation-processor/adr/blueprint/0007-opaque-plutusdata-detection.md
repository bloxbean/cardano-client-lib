# ADR 0007: CIP-57 Opaque PlutusData Detection

- Status: Accepted
- Date: 2026-02-25
- Owners: Cardano Client Lib maintainers
- Related: CIP-57 Plutus Contract Blueprints, ADR-0004 (version-specific bug example)

## Context

CIP-57 defines that some schema definitions represent **opaque Plutus Data** — types with no concrete structure that map directly to `PlutusData.class` rather than generated Java classes.

From the CIP-57 specification:

> "The dataType keyword is optional. When missing, the instance is implicitly typed as an opaque Plutus Data."

These opaque types appear in blueprints as:
- Global abstract types: `"Data"`, `"Redeemer"` (Aiken v1.0.x)
- Namespaced abstract types: `"cardano/transaction/Redeemer"`, `"validator/AbstractRedeemer"` (Aiken v1.1.x)

If the processor attempts to generate Java classes for opaque types, compilation fails because there is no structure to instantiate. This was the root cause of a bug fixed in commit `5dd5d2d5`, where namespaced abstract redeemer types like `cardano~1transaction~1Redeemer` incorrectly triggered class generation.

## Decision

Implement a **three-layer defense** for detecting opaque PlutusData types. Each layer catches a different class of opaque types at a different point in the processing pipeline.

### Layer 1: Schema-Level Detection

**Method**: `BlueprintUtil.isAbstractPlutusDataType(BlueprintSchema schema)`

**Location**: Called in `FieldSpecProcessor.getInnerDatumClass()` and `FieldSpecProcessor.createDatumFieldSpec()`

**Logic**: A schema is opaque PlutusData when it has:
- No `dataType` (`schema.getDataType() == null`)
- AND no structural properties:
  - `anyOf` is null
  - `fields` is null
  - `items` is null
  - `keys` is null
  - `values` is null
  - `left` is null
  - `right` is null

```java
public static boolean isAbstractPlutusDataType(BlueprintSchema schema) {
    if (schema == null) return false;

    boolean hasNoDataType = schema.getDataType() == null;

    boolean hasNoStructure = schema.getAnyOf() == null &&
            schema.getFields() == null &&
            schema.getItems() == null &&
            schema.getKeys() == null &&
            schema.getValues() == null &&
            schema.getLeft() == null &&
            schema.getRight() == null;

    return hasNoDataType && hasNoStructure;
}
```

**Catches**: Direct references to abstract types (e.g., a field whose `$ref` resolves to a schema with only `title` and `description`, no structure).

### Layer 2: Reference Resolution Null Return

**Method**: `FieldSpecProcessor.resolveClassNameFromRef(String ref, BlueprintSchema refSchema)`

**Location**: Called in `FieldSpecProcessor.createDatumFieldSpec()` when a field has a `$ref`

**Logic**: Returns `null` when the referenced definition key resolves to a built-in container (checked via `BlueprintUtil.isBuiltInGenericContainer()`). A `null` return signals the caller to fall back to `PlutusData.class`.

**Catches**: References to built-in generic containers like `List<Int>`, `Option<Credential>`, `Data` — types that should not generate classes but instead map to `PlutusData` at the field level.

### Layer 3: Field-Level RefSchema Check

**Method**: Inline check in `FieldSpecProcessor.createDatumFieldSpec()`

**Location**: After the `createDatumTypeSpec()` call, before resolving the final class name

**Logic**: When `schema.getRefSchema() != null`, explicitly checks `isAbstractPlutusDataType(schema.getRefSchema())` before proceeding with class name resolution. This catches cases where the reference was resolved but the target schema is still opaque.

**Catches**: Namespaced abstract types referenced from fields (e.g., `"$ref": "#/definitions/test_module~1CustomData"` where `CustomData` has no dataType or structure). This was the specific bug scenario fixed in commit `5dd5d2d5`.

## Processing Flow

```
Field with $ref: "#/definitions/validator~1AbstractRedeemer"
  │
  ├─ Layer 1: getInnerDatumClass() checks isAbstractPlutusDataType(schema)
  │   └─ If schema itself is abstract → return PlutusData.class ✓
  │
  ├─ Layer 2: resolveClassNameFromRef() checks isBuiltInGenericContainer()
  │   └─ If ref resolves to built-in container → return null → PlutusData.class ✓
  │
  └─ Layer 3: createDatumFieldSpec() checks isAbstractPlutusDataType(refSchema)
      └─ If resolved refSchema is abstract → return PlutusData field ✓
```

## Rationale

**Why three layers instead of one?**

Each layer operates at a different point in the pipeline and catches a different class of opaque types:

1. **Layer 1** catches opaque types when the schema is directly available (no reference traversal needed).
2. **Layer 2** catches built-in containers that are opaque at the field level but identified by their definition key pattern.
3. **Layer 3** catches namespaced abstract types that can only be identified after resolving the `$ref` to the target schema.

A single check point would miss cases because:
- `getInnerDatumClass()` and `createDatumFieldSpec()` have different entry points and schema resolution paths.
- `$ref` resolution happens at different stages depending on whether the schema is a direct reference or a nested field.
- The bug in commit `5dd5d2d5` specifically required Layer 3 because Layers 1 and 2 did not examine `refSchema` in the field-creation path.

## Consequences

### Positive
- Correctly maps all known opaque PlutusData types to `PlutusData.class`.
- Handles both global (`Data`) and namespaced (`cardano/transaction/Redeemer`) abstract types.
- Defense-in-depth: no single code path failure can cause incorrect class generation for opaque types.

### Negative
- Three check points means three places to maintain. However, all three call the same `isAbstractPlutusDataType()` utility method, so the core logic is centralized.

## References

- CIP-57: [Plutus Contract Blueprints](https://cips.cardano.org/cip/CIP-0057)
- Commit `5dd5d2d5`: feat: Support opaque PlutusData types and fix validator name collisions
- ADR-0004: Blueprint Test File Naming (documents the version-specific bug example)
- `BlueprintUtil.isAbstractPlutusDataType()` — core detection logic
- `FieldSpecProcessor.resolveClassNameFromRef()` — Layer 2 implementation
- `FieldSpecProcessor.createDatumFieldSpec()` — Layer 3 implementation
