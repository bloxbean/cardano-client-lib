# ADR 0016: Map<String, V> Field Support

## Status
Accepted

## Context
Users needed key-value structures in metadata (e.g., configuration settings, named scores, address registries). Without Map support, they had to create wrapper classes or manually build MetadataMap instances.

## Decision
Support `Map<String, V>` fields where V can be:
- Any supported scalar type (String, BigInteger, Integer, etc.)
- An enum type
- A `@MetadataType`-annotated class (leveraging ADR 0015)

### Constraints
- **Only `String` keys** — `Map<Integer, V>` or other key types emit a compile-time ERROR. Cardano metadata maps typically use string keys for human readability.
- **Null values skipped** — entries with null values are omitted during serialization.
- **Preserved order** — deserialization uses `LinkedHashMap` to maintain insertion order.

### Code Generation
A new `MapCodeGen` class handles serialization/deserialization:

**Serialization:**
```java
MetadataMap _mapKey = MetadataBuilder.createMap();
for (Map.Entry<String, V> _entry : obj.getField().entrySet()) {
    if (_entry.getValue() != null) {
        _mapKey.put(_entry.getKey(), /* converted value */);
    }
}
map.put("key", _mapKey);
```

**Deserialization:**
```java
if (v instanceof MetadataMap) {
    MetadataMap _rawMap = (MetadataMap) v;
    Map<String, V> _result = new LinkedHashMap<>();
    for (Object _k : _rawMap.keys()) {
        if (_k instanceof String) {
            Object _val = _rawMap.get((String) _k);
            // type-specific value deserialization
        }
    }
    obj.setField(_result);
}
```

### MetadataTypeCodeGen Extensions
Two new methods added to the `MetadataTypeCodeGen` interface:
- `emitSerializeMapValue()` — emits value conversion for map entry serialization
- `emitDeserializeMapValue()` — emits value conversion for map entry deserialization

Implementations provided in `AbstractMetadataTypeCodeGen` (template-based) and `StringCodeGen` (handles chunking).

### MetadataFieldInfo Extensions
- `mapType`, `mapKeyTypeName`, `mapValueTypeName`
- `mapValueEnumType`, `mapValueNestedType`, `mapValueConverterFqn`

## Consequences
- String chunking (>64 UTF-8 bytes) applies to Map string values, maintaining consistency
- Map values can be nested `@MetadataType` objects, enabling rich hierarchical structures
- Empty maps are serialized as empty MetadataMap (not omitted)
