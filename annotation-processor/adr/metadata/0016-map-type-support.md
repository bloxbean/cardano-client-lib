# ADR 0016: Map<K, V> Field Support

## Status
Accepted (updated: Integer/Long/BigInteger/byte[] keys added)

## Context
Users needed key-value structures in metadata (e.g., configuration settings, named scores, address registries). Without Map support, they had to create wrapper classes or manually build MetadataMap instances.

## Decision
Support `Map<K, V>` fields where K can be:
- `String` (stored as Cardano text)
- `Integer` (stored as Cardano integer via `BigInteger.valueOf`)
- `Long` (stored as Cardano integer via `BigInteger.valueOf`)
- `BigInteger` (stored as Cardano integer directly)
- `byte[]` (stored as Cardano byte string — see also ADR 0021)

And V can be:
- Any supported scalar type (String, BigInteger, Integer, etc.)
- An enum type
- A `@MetadataType`-annotated class (leveraging ADR 0015)

### Supported key types
| Map key type   | Serialization                        | Deserialization                        |
|----------------|--------------------------------------|----------------------------------------|
| `String`       | `_map.put(key, value)`               | `instanceof String` check              |
| `Integer`      | `_map.put(BigInteger.valueOf(key), value)` | `((BigInteger) _k).intValue()`   |
| `Long`         | `_map.put(BigInteger.valueOf(key), value)` | `((BigInteger) _k).longValue()`  |
| `BigInteger`   | `_map.put(key, value)`               | `instanceof BigInteger` check          |
| `byte[]`       | `_map.put(key, value)`               | `instanceof byte[]` check              |

Other key types (e.g., `Map<Boolean, V>`) emit a compile-time ERROR.

### Constraints
- **Null values skipped** — entries with null values are omitted during serialization.
- **Preserved order** — deserialization uses `LinkedHashMap` to maintain insertion order.
- **Negative BigInteger keys** — correctly handled using sign-aware helpers (`putNegative()`).

### Code Generation
A `MapCodeGen` class handles serialization/deserialization:

**Serialization (String keys):**
```java
MetadataMap _mapKey = MetadataBuilder.createMap();
for (Map.Entry<String, V> _entry : obj.getField().entrySet()) {
    if (_entry.getValue() != null) {
        _mapKey.put(_entry.getKey(), /* converted value */);
    }
}
map.put("key", _mapKey);
```

**Serialization (Integer/Long/BigInteger keys):**
```java
MetadataMap _mapKey = MetadataBuilder.createMap();
for (Map.Entry<BigInteger, V> _entry : obj.getField().entrySet()) {
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
    Map<K, V> _result = new LinkedHashMap<>();
    for (Object _k : _rawMap.keys()) {
        // key type check (instanceof String or instanceof BigInteger)
        Object _val = _rawMap.get(_k);
        // type-specific value deserialization
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

### Example

```java
@MetadataType
public class GameScores {
    // String keys (most common)
    private Map<String, BigInteger> playerScores;

    // Integer keys (e.g., level numbers)
    private Map<Integer, String> levelNames;

    // BigInteger keys
    private Map<BigInteger, String> blockData;
}
```

## Consequences
- String chunking (>64 UTF-8 bytes) applies to Map string values, maintaining consistency
- Map values can be nested `@MetadataType` objects, enabling rich hierarchical structures
- Empty maps are serialized as empty MetadataMap (not omitted)
- Integer, Long, and BigInteger keys are stored as Cardano integers, enabling numeric map lookups on-chain
- byte[] keys are stored as native Cardano byte strings (see ADR 0021 for details)
