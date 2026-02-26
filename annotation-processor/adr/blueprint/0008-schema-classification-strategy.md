# ADR 0008: Schema Classification Strategy

- Status: Accepted
- Date: 2026-02-25
- Owners: Cardano Client Lib maintainers
- Related: CIP-57 Plutus Contract Blueprints

## Context

CIP-57 blueprint schemas can represent fundamentally different kinds of types: simple aliases for primitives, optional wrappers, enumerations, polymorphic interfaces, and concrete data classes. The annotation processor must classify each schema to determine the correct generation strategy.

Previously, this classification logic was embedded directly in `FieldSpecProcessor`, making it difficult to test, reason about, and extend. The classification was extracted into a dedicated `SchemaClassifier` with a well-defined `SchemaClassification` enum and `SchemaClassificationResult` value object.

## Decision

Define a 7-type classification system where each blueprint schema is categorized into exactly one of the following types, checked in priority order:

### Classification Priority Order

| Priority | Classification | Condition | Generation Action |
|----------|---------------|-----------|------------------|
| 1 | `ALIAS` | Primitive `dataType`, no structure (no items, fields, anyOf, allOf, oneOf, notOf) | **Skip** — handled by primitive type mapping |
| 2 | `OPTION` | Title is "Option" or "Optional", `anyOf` has exactly 2 variants titled "Some" (1 field) and "None" (0 fields) | **Skip** — handled by `OptionDataTypeProcessor` |
| 3 | `PAIR_ALIAS` | Title is "Pair" and `dataType == pair` | **Skip** — handled by `PairDataTypeProcessor` |
| 4 | `ENUM` | `anyOf` with >1 variant, all variants have `dataType == constructor`, all have titles, none have fields | **Generate** Java enum |
| 5 | `INTERFACE` | `anyOf` with >1 variant (not matching ENUM criteria) | **Generate** Java interface + implementing classes |
| 6 | `CLASS` | Default — anything not matching above | **Generate** Java class |
| 7 | `UNKNOWN` | Null schema | **Error** — should not occur in valid blueprints |

### The "Skippable" Concept

Classifications `ALIAS`, `OPTION`, and `PAIR_ALIAS` are **skippable** — they do not generate top-level classes because dedicated `DataTypeProcessor` implementations handle them inline during field type resolution:

```java
public boolean isSkippable() {
    return classification == SchemaClassification.ALIAS
            || classification == SchemaClassification.OPTION
            || classification == SchemaClassification.PAIR_ALIAS;
}
```

### ENUM vs INTERFACE Distinction

Both ENUM and INTERFACE schemas have `anyOf` with multiple variants. The key difference:

- **ENUM**: All variants are constructors with **titles but no fields** (pure value enumeration).
  - Example: `{ "anyOf": [{ "dataType": "constructor", "title": "Buy", "index": 0 }, { "dataType": "constructor", "title": "Sell", "index": 1 }] }`

- **INTERFACE**: `anyOf` with multiple variants where **at least one variant has fields** (polymorphic type).
  - Example: `{ "anyOf": [{ "dataType": "constructor", "title": "VerificationKey", "fields": [...] }, { "dataType": "constructor", "title": "Script", "fields": [...] }] }`

ENUM is checked before INTERFACE because it is the stricter condition — every variant must be a titled, field-less constructor.

### SchemaClassificationResult

The result carries additional metadata required downstream:

- **`enumValues`**: For ENUM classification, the list of variant titles (e.g., `["Buy", "Sell"]`).
- **`interfaceName`**: For INTERFACE classification, the PascalCase interface name derived from the schema title via `NamingStrategy.toClassName()`.

## Implementation

### SchemaClassifier

Located at `classifier/SchemaClassifier.java`. Takes a `NamingStrategy` for interface name resolution.

```java
public SchemaClassificationResult classify(BlueprintSchema schema) {
    if (schema == null)       return SchemaClassificationResult.unknown();
    if (isAlias(schema))      return SchemaClassificationResult.alias();
    if (isOptionType(schema)) return SchemaClassificationResult.option();
    if (isPairAlias(schema))  return SchemaClassificationResult.pairAlias();
    if (isEnum(schema))       return SchemaClassificationResult.enumType(extractEnumValues(schema));
    if (isInterface(schema))  return SchemaClassificationResult.interfaceType(nameStrategy.toClassName(schema.getTitle()));
    return SchemaClassificationResult.classType();
}
```

### Detection Rules (Detail)

**ALIAS**: `dataType` is a primitive type AND no items, fields, anyOf, allOf, oneOf, or notOf lists.

**OPTION**: Title is "Option" or "Optional", anyOf has exactly 2 elements, first titled "Some" with exactly 1 field, second titled "None" with 0 fields.

**PAIR_ALIAS**: Title is "Pair" and `dataType == BlueprintDatatype.pair`.

**ENUM**: `anyOf` has >1 element, no top-level fields, every anyOf element has `dataType == constructor`, a non-empty title, and no fields.

**INTERFACE**: `anyOf` has >1 element (this check runs after ENUM, so at least one variant has fields or is not a constructor).

### SchemaClassificationResult

Located at `classifier/SchemaClassificationResult.java`. Immutable value object with a builder pattern and convenient factory methods (`alias()`, `option()`, `enumType(values)`, `interfaceType(name)`, `classType()`, `unknown()`).

## Rationale

1. **Separation of concerns**: Classification logic is isolated from code generation, making each independently testable.
2. **Priority ordering**: Stricter checks run first (ALIAS before OPTION, ENUM before INTERFACE) to avoid misclassification.
3. **Extensibility**: New classifications can be added by extending the enum and adding a check at the appropriate priority level.
4. **Skippable concept**: Makes it explicit which types are handled elsewhere, preventing accidental duplicate generation.

## Consequences

### Positive
- Clear, testable classification logic with well-defined priority order.
- `isSkippable()` eliminates ad-hoc skip checks scattered through the codebase.
- `SchemaClassificationResult` carries all metadata needed for generation in one place.

### Negative
- The priority order is implicit in the `if-else` chain. A misconfigured check order could misclassify schemas.
  - **Mitigation**: Comprehensive unit tests for each classification boundary.

## References

- `classifier/SchemaClassification.java` — the 7-type enum
- `classifier/SchemaClassifier.java` — classification logic with priority ordering
- `classifier/SchemaClassificationResult.java` — immutable result with metadata
- `FieldSpecProcessor` — consumer that uses `isSkippable()` and classification metadata
- `model/DatumModelFactory` — consumer for datum model creation based on classification
