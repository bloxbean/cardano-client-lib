# ADR 0015: DataType Processor Strategy Pattern

- Status: Accepted
- Date: 2026-02-26
- Owners: Cardano Client Lib maintainers
- Related: ADR-0002 (Code Generation Pipeline), ADR-0011 (Multi-Item Tuple Support), CIP-57

## Context

CIP-57 blueprints define 9 distinct data types (`bytes`, `integer`, `string`, `bool`, `list`, `map`, `option`, `pair`, `constructor`) plus a null/absent data type for opaque `PlutusData`. Each data type requires different Java type mapping, field generation, and serialization logic.

Initially, this logic was handled by a large conditional chain in `FieldSpecProcessor`, making it difficult to extend (adding tuple support, for example, required modifying multiple branches) and hard to test in isolation.

## Decision

Apply the Strategy pattern: define a `DataTypeProcessor` interface, implement one processor per data type, and use `DataTypeProcessUtil` as the dispatcher with O(1) lookup.

### `DataTypeProcessor` Interface

```java
public interface DataTypeProcessor {
    BlueprintDatatype supportedType();
    List<FieldSpec> process(DataTypeProcessingContext context);
}
```

### `DataTypeProcessingContext`

Immutable context object passed to each processor:

```java
public class DataTypeProcessingContext {
    String namespace;         // Package namespace for inner class resolution
    String javaDoc;           // Documentation from schema description
    BlueprintSchema schema;   // The schema being processed
    String className;         // Enclosing class name (for nested types)
    String alternativeName;   // Fallback name when schema.title is absent
}
```

### `AbstractDataTypeProcessor`

Base class providing shared naming logic:

```java
public abstract class AbstractDataTypeProcessor implements DataTypeProcessor {
    protected final NamingStrategy nameStrategy;
    protected final SchemaTypeResolver typeResolver;

    protected String resolveFieldName(DataTypeProcessingContext context) {
        String title = context.getSchema().getTitle();
        if (title == null) title = context.getAlternativeName();
        return nameStrategy.firstLowerCase(nameStrategy.toCamelCase(title));
    }
}
```

### Processor Registry

`DataTypeProcessUtil` registers all processors in an `EnumMap` for O(1) dispatch:

```
┌──────────────────────────────────────────────────────┐
│  DataTypeProcessUtil                                 │
│                                                      │
│  EnumMap<BlueprintDatatype, DataTypeProcessor>       │
│  ┌─────────────┬──────────────────────────────────┐  │
│  │ bytes       │ BytesDataTypeProcessor           │  │
│  │ integer     │ IntegerDataTypeProcessor         │  │
│  │ string      │ StringDataTypeProcessor          │  │
│  │ bool        │ BoolDataTypeProcessor            │  │
│  │ list        │ ListDataTypeProcessor            │  │
│  │ map         │ MapDataTypeProcessor             │  │
│  │ option      │ OptionDataTypeProcessor          │  │
│  │ pair        │ PairDataTypeProcessor            │  │
│  │ constructor │ ConstructorDataTypeProcessor     │  │
│  └─────────────┴──────────────────────────────────┘  │
│                                                      │
│  Fallback (null dataType):                           │
│    PlutusDataTypeProcessor                           │
└──────────────────────────────────────────────────────┘
```

### Dispatch Logic

```java
List<FieldSpec> generateFieldSpecs(String ns, String javaDoc,
        BlueprintSchema schema, String className, String altName) {
    // 1. Check SharedTypeLookup first
    if (sharedTypeLookup.find(schema) != null) {
        return generateSharedTypeFieldSpec(...);
    }

    // 2. Build context
    DataTypeProcessingContext context = new DataTypeProcessingContext(
        ns, javaDoc, schema, className, altName);

    // 3. Dispatch
    if (schema.getDataType() == null) {
        return plutusDataTypeProcessor.process(context);
    }
    return processors.get(schema.getDataType()).process(context);
}
```

### Concrete Processors

| Processor | Type | Java Mapping | Notes |
|-----------|------|-------------|-------|
| `BytesDataTypeProcessor` | `bytes` | `byte[]` | Simple field |
| `IntegerDataTypeProcessor` | `integer` | `BigInteger` | Simple field |
| `StringDataTypeProcessor` | `string` | `String` | Simple field |
| `BoolDataTypeProcessor` | `bool` | `boolean` | Primitive type |
| `ListDataTypeProcessor` | `list` | `List<T>`, `Pair`, `Triple`, `Quartet`, `Quintet` | Delegates to `SchemaTypeResolver`; tuple dispatch by item count (ADR-0011) |
| `MapDataTypeProcessor` | `map` | `Map<K, V>` | Keys and values resolved via `SchemaTypeResolver` |
| `OptionDataTypeProcessor` | `option` | `Optional<T>` | Validates Some/None `anyOf` structure |
| `PairDataTypeProcessor` | `pair` | `Pair<T1, T2>` | Uses `left`/`right` schema fields |
| `ConstructorDataTypeProcessor` | `constructor` | Nested class or enum field | Iterates `schema.getFields()`, delegates field processing; empty constructors become enum fields |
| `PlutusDataTypeProcessor` | `null` | `PlutusData` | Fallback for schemas with no data type |

### Alternative Name Resolution

`DataTypeProcessUtil.determineAlternativeName()` provides fallback names when `schema.getTitle()` is absent:

1. `schema.getTitle()` if present
2. Last segment of normalized `$ref` (via `BlueprintUtil.normalizedReference()`) + index
3. `dataType.name()` + index (e.g., `bytes0`, `integer1`)
4. `"field"` + index as final fallback

## Consequences

### Positive
- **Open/Closed Principle**: New data types can be added by implementing `DataTypeProcessor` and registering — no modification to existing processors.
- **Testability**: Each processor can be unit tested in isolation with a mock `DataTypeProcessingContext`.
- **O(1) dispatch**: `EnumMap` lookup is faster and cleaner than if-else chains.
- **Shared logic**: `AbstractDataTypeProcessor` eliminates naming logic duplication across processors.
- **Clean extension point**: Adding tuple support (ADR-0011) required only creating new resolve methods in `SchemaTypeResolver` and updating `ListDataTypeProcessor` — no changes to other processors.

### Negative
- 10 processor classes for 10 data types adds file count.
  - **Mitigation**: Each is small (typically <50 lines) and focused. The alternative — a monolithic switch — was harder to maintain.
- The `PlutusDataTypeProcessor` fallback for null data types is a special case outside the `EnumMap`.
  - **Mitigation**: Explicit null check in the dispatcher makes this behavior visible and intentional.

## References

- `DataTypeProcessor` — strategy interface
- `DataTypeProcessingContext` — immutable context object
- `AbstractDataTypeProcessor` — base class with shared naming
- `DataTypeProcessUtil` — dispatcher with `EnumMap` registry
- `SchemaTypeResolver` — type resolution for complex types (list, map, option, pair, tuples)
- All 10 concrete processors in `com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype`
- ADR-0002: Code Generation Pipeline
- ADR-0011: Multi-Item Tuple Support (demonstrates extensibility)
