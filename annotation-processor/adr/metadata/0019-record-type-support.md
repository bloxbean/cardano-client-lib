# ADR 0019: Record Type Support

## Status
Accepted

## Context
Java 16 introduced records as immutable data carriers. Records use a canonical constructor and component accessors (`name()`) instead of no-arg constructors, setters, and `getName()`-style getters. Users annotating records with `@MetadataType` expected them to work like POJOs, but the processor assumed mutable beans with setters.

## Decision
Support Java records as first-class `@MetadataType` targets. The processor detects records via `ElementKind.RECORD` and switches to a record-specific extraction and code generation path.

### Detection
- `MetadataFieldExtractor.isRecord(TypeElement)` checks `typeElement.getKind() == ElementKind.RECORD`
- `MetadataAnnotationProcessor.processType()` branches on `isRecord` to choose extraction strategy

### Field Extraction
- `extractRecordFields()` iterates `typeElement.getRecordComponents()` (preserves declaration order)
- Maps each `RecordComponentElement` to its backing `VariableElement` for annotation lookup
- Builds two lists:
  - **fields** — serialized fields (respects `@MetadataIgnore`)
  - **allComponents** — all record components in declaration order (needed for constructor call)
- Sets `recordMode = true` on each `MetadataFieldInfo`
- Accessor is the component name directly (e.g., `name()` not `getName()`)
- No setter is set (records are immutable)

### Code Generation — Serialization
Identical to POJOs except accessors use component names:
```java
if (sampleRecord.name() != null) {
    map.put("name", sampleRecord.name());
}
```

### Code Generation — Deserialization
Uses a three-phase approach instead of setter calls:

1. **Phase 1**: Declare local variables `_fieldName` with type-appropriate defaults for ALL components (including ignored ones)
2. **Phase 2**: Deserialize only serialized fields into their local variables
3. **Phase 3**: Call canonical constructor with all components in declaration order

```java
public SampleRecord fromMetadataMap(MetadataMap map) {
    String _name = null;
    int _age = 0;
    String _address = null;
    // ... deserialize fields into locals ...
    return new SampleRecord(_name, _age, _address);
}
```

### Default Values for Primitives
`defaultForType()` provides zero-values for primitive types in local variable declarations:

| Type | Default |
|------|---------|
| `int` | `0` |
| `long` | `0L` |
| `boolean` | `false` |
| `double` | `0.0d` |
| `float` | `0.0f` |
| `char` | `'\0'` |
| Reference types | `null` |

### MetadataFieldAccessor
A `MetadataFieldAccessor` class consolidates three accessor patterns:
- **Record mode**: `_fieldName = expr`
- **POJO with setter**: `obj.setFieldName(expr)`
- **POJO without setter**: `obj.fieldName = expr`

### Example
```java
@MetadataType(label = 900)
public record SampleRecord(
        String name,
        int age,
        @MetadataField(key = "addr") String address,
        BigInteger amount,
        List<String> tags
) {}
```

## Consequences
- Records work with all existing features: collections, maps, enums, Optional, nested types, polymorphic types, adapters, required/defaultValue, labels
- `@MetadataIgnore` on record components works — the component still appears in the constructor call with its default value
- No-arg constructor validation is skipped for records
- Lombok detection is skipped for records
- Component declaration order is critical — reordering components changes the generated constructor call
