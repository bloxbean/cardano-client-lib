# ADR 0021: Byte Array Map Keys and Collection Encoding

## Status
Accepted

## Context
Two related gaps existed for `byte[]` handling:

1. **Map keys**: ADR 0016 supported String, Integer, Long, and BigInteger map keys. Cardano metadata natively supports byte string keys, but `Map<byte[], V>` was not handled.
2. **Collection encoding**: Scalar `byte[]` fields supported `enc = STRING_HEX` and `enc = STRING_BASE64` (ADR 0004), but `List<byte[]>` / `Set<byte[]>` stored elements as raw bytes with no way to specify hex or base64 encoding per element.

## Decision

### Byte Array Map Keys
Extend `MapCodeGen` to support `byte[]` as a map key type:

| Map key type | Serialization | Deserialization |
|-------------|---------------|-----------------|
| `byte[]` | Pass-through (native byte string key) | `(byte[]) _k` cast |

```java
@MetadataType
public class SampleByteKeyMap {
    private Map<byte[], String> labels;
    private Map<byte[], BigInteger> amounts;
}
```

**Implementation details**:
- `resolveKeyTypeName()` returns `TypeName.get(byte[].class)` for `byte[]` keys
- `keyOnChainClass()` returns `byte[].class` (not `BigInteger.class` like numeric keys)
- `emitKeyInstanceofCheck()` uses literal `instanceof byte[]` because JavaPoet's `$T` doesn't work with array types
- `serKeyExpr()` passes byte[] keys through directly (no conversion needed)
- `deserKeyExpr()` casts to `(byte[]) _k`

### Byte Array Collection Encoding
Extend `ByteArrayCodeGen` and `CollectionCodeGen` to support hex and base64 encoding for `List<byte[]>` and `Set<byte[]>` elements:

```java
@MetadataType
public class Hashes {
    @MetadataField(enc = MetadataFieldType.STRING_HEX)
    private List<byte[]> hexHashes;

    @MetadataField(enc = MetadataFieldType.STRING_BASE64)
    private List<byte[]> base64Blobs;

    private List<byte[]> rawPayloads;  // DEFAULT: raw bytes
}
```

**Generated serialization** (hex example):
```java
MetadataList _list = MetadataBuilder.createList();
for (byte[] _el : obj.getHexHashes()) {
    _list.add(HexUtil.encodeHexString(_el));
}
map.put("hexHashes", _list);
```

**Generated deserialization** (hex example):
```java
if (_el instanceof String) {
    _result.add(HexUtil.decodeHexString((String) _el));
}
```

**New methods in `ByteArrayCodeGen`**:
- `emitSerializeToListHex()` / `emitSerializeToListBase64()` — element-level serialization
- `emitDeserializeElementHex()` / `emitDeserializeElementBase64()` — element-level deserialization

**`CollectionCodeGen` integration**: The collection serialization/deserialization dispatch checks for `byte[]` element type combined with `STRING_HEX` or `STRING_BASE64` encoding before falling through to the default scalar path.

## Consequences
- Byte array map keys are stored as native Cardano byte strings, enabling on-chain byte-key lookups
- Collection encoding respects the same `enc` attribute used for scalar byte[] fields, maintaining API consistency
- Default encoding for `List<byte[]>` remains raw bytes (no encoding), preserving backward compatibility
- The `enc` attribute on collections only applies to `byte[]` elements — other element types ignore it
