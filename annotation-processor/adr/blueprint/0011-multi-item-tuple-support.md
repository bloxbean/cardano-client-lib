# ADR 0011: Multi-Item Tuple Support (Pair through Quintet)

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0006 (Generic Type Syntax Handling), ADR-0015 (DataType Processor Strategy Pattern), CIP-57

## Context

CIP-57 blueprints represent fixed-length heterogeneous collections as `list` schemas with a fixed number of `items`. Aiken contracts commonly produce tuples of 2–5 elements for return values, datum fields, and redeemer parameters.

Previously, only 2-element tuples were supported (mapped to `Pair<T1, T2>`). Contracts using 3-, 4-, or 5-element tuples would fail at code generation time. Additionally, element-level PlutusData detection was not applied per tuple position, causing incorrect serialization for tuple elements that are shared types or complex types.

## Decision

Extend tuple support to 5 elements using dedicated generic types, and reject tuples of 6+ items with a clear error.

### Tuple Type Mapping

| Item Count | Java Type | Module |
|-----------|-----------|--------|
| 2 | `Pair<T1, T2>` | `plutus` |
| 3 | `Triple<T1, T2, T3>` | `plutus` |
| 4 | `Quartet<T1, T2, T3, T4>` | `plutus` |
| 5 | `Quintet<T1, T2, T3, T4, T5>` | `plutus` |
| 6+ | Rejected with `BlueprintGenerationException` | — |

All tuple classes are in `com.bloxbean.cardano.client.plutus.blueprint.type` and follow the same pattern: generic type parameters, positional getters (`getFirst()` through `getFifth()`), and `equals()`/`hashCode()`/`toString()`.

### Type Resolution in `SchemaTypeResolver`

The `resolveType()` method dispatches `list` schemas by item count:

```java
case list:
    int itemCount = schema.getItems().size();
    if (itemCount == 3) return resolveTripleType(namespace, schema);
    if (itemCount == 4) return resolveQuartetType(namespace, schema);
    if (itemCount == 5) return resolveQuintetType(namespace, schema);
    if (itemCount >= 6) throw new BlueprintGenerationException(
        "Tuples with 6+ items are not supported");
    return resolveListType(namespace, schema);  // 0, 1, or 2 items
```

Each `resolve*Type()` method iterates schema items, resolves each element's type recursively (including shared type and inner class detection), and constructs a `ParameterizedTypeName`.

### Element-Level PlutusData Detection

Each tuple position is individually checked for:
- **Shared types** (`Data<T>` / `RawData` implementations) — inline `toPlutusData()`/`fromPlutusData()` calls.
- **Complex types** (generated model classes) — delegate through generated converter.
- **Primitive types** (`BigInteger`, `byte[]`, `String`, `Boolean`) — direct serialization.

### Converter Generation

**Serialization** (`toPlutusData`):
```
ListPlutusData list = new ListPlutusData();
list.add(serialize(obj.getFirst()));   // per-element type handling
list.add(serialize(obj.getSecond()));
list.add(serialize(obj.getThird()));   // Triple and above
...
return list;
```

**Deserialization** (`fromPlutusData`):
```
ListPlutusData list = (ListPlutusData) plutusData;
T1 first = deserialize(list.get(0));   // per-element type handling
T2 second = deserialize(list.get(1));
T3 third = deserialize(list.get(2));   // Triple and above
...
return new Triple<>(first, second, third);
```

### FieldType and Type Enum

- `Type` enum extended with: `TRIPLE`, `QUARTET`, `QUINTET` (alongside existing `PAIR`).
- `JavaType` constants: `PAIR`, `TRIPLE`, `QUARTET`, `QUINTET`.
- `ClassDefinitionGenerator.detectFieldType()` detects parameterized tuple types by erasure and sets the appropriate `Type` and generic types. Raw (unparameterized) tuple types fall back to `Type.CONSTRUCTOR` with `rawDataType = true`.

## Consequences

### Positive
- Contracts returning up to 5-element tuples are fully supported without manual intervention.
- Element-level type detection ensures correct serialization for mixed-type tuples (e.g., `Triple<BigInteger, MyDatum, byte[]>`).
- Consistent naming: `Pair`, `Triple`, `Quartet`, `Quintet` follow the established naming convention from JavaTuples.

### Negative
- 6+ element tuples are rejected. Contracts with larger tuples require manual handling.
  - **Mitigation**: Tuples of 6+ items are extremely rare in practice. Aiken style guides recommend structs for large groupings.
- Four new `Type` enum values and tuple classes increase the type system surface area.

## References

- `SchemaTypeResolver` — type dispatch for list schemas
- `PairDataTypeProcessor`, `ListDataTypeProcessor` — processors for pair and list datatypes
- `Pair`, `Triple`, `Quartet`, `Quintet` — tuple type classes in `plutus` module
- `ClassDefinitionGenerator.detectFieldType()` — tuple detection in field analysis
- `ConverterCodeGenerator` — serialization/deserialization generation for tuple types
