# ADR metadata/0003: 64-Byte String Chunking Ownership

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

Cardano transaction metadata (CIP-10) limits each **text** value to **64 UTF-8 bytes**.
Strings longer than 64 bytes must be represented as a Cardano **list** of ≤64-byte chunks,
reassembled on read-back.

The project's `CBORMetadataMap` helper class already contains auto-split logic that silently
chunks strings exceeding 64 bytes before writing to CBOR. This means that if the generated
`toMetadataMap` simply calls `map.put(key, longString)`, the split happens inside
`CBORMetadataMap` as a side effect.

The same auto-split has no counterpart in `fromMetadataMap`: the generated code must
explicitly handle a `MetadataList` value when reading a key that was stored as chunks.

### The problem with relying on CBORMetadataMap's auto-split

1. **Invisible behaviour**: The generator produces code like `map.put("note", value)`, but the
   actual on-chain representation is a list of chunks. Reading that generated code gives no
   indication that chunking occurs.

2. **Fragile coupling**: The auto-split is an implementation detail of `CBORMetadataMap`, not
   a published contract. It can be changed, removed, or made opt-in without breaking
   `CBORMetadataMap`'s API.

3. **Asymmetry**: Even with auto-split active, `fromMetadataMap` must still handle
   `MetadataList` explicitly — so half the chunking logic lives in the generator regardless.

## Decision

The **generated converter owns the 64-byte chunking logic in both directions**.
`CBORMetadataMap`'s auto-split is bypassed for generated code by putting the chunked
`MetadataList` directly, never passing a long string to `map.put`.

### Generated pattern — toMetadataMap

```java
if (order.getNote().getBytes(StandardCharsets.UTF_8).length > 64) {
    MetadataList _chunks = MetadataBuilder.createList();
    for (String _part : StringUtils.splitStringEveryNCharacters(order.getNote(), 64)) {
        _chunks.add(_part);
    }
    map.put("note", _chunks);
} else {
    map.put("note", order.getNote());
}
```

- Short strings (≤ 64 UTF-8 bytes) are stored directly as Cardano text.
- Long strings are split into ≤64-character chunks using `StringUtils.splitStringEveryNCharacters`
  and stored as a `MetadataList`.
- The byte-length check uses `getBytes(UTF_8)` because the limit is **bytes**, not characters.

### Generated pattern — fromMetadataMap

```java
Object v = map.get("note");
if (v instanceof String) {
    obj.setNote((String) v);
} else if (v instanceof MetadataList) {
    StringBuilder _sb = new StringBuilder();
    MetadataList _list = (MetadataList) v;
    for (int _i = 0; _i < _list.size(); _i++) {
        Object _chunk = _list.getValueAt(_i);
        if (_chunk instanceof String) {
            _sb.append((String) _chunk);
        }
    }
    obj.setNote(_sb.toString());
}
```

Both branches are always present, allowing the converter to read data written by:
- The generated converter (which writes chunks only when > 64 bytes).
- `CBORMetadataMap` auto-split (which always chunks; produces `MetadataList`).
- Any other well-formed metadata producer that respects CIP-10.

## Alternatives considered

### 1. Rely on CBORMetadataMap auto-split (rejected)

Generated `toMetadataMap` calls `map.put(key, value)` regardless of length; auto-split
handles it silently.

**Why rejected:**
- The generated code does not reflect the actual on-chain structure; it is misleading.
- Half the logic (`fromMetadataMap` MetadataList handling) must live in the generator anyway.
- A future change to `CBORMetadataMap` behaviour could silently break round-trips.

### 2. Always store as MetadataList (rejected)

Every string, regardless of length, is stored as a one-element list.

**Why rejected:**
- Wastes space for short strings (most strings in practice are short).
- Makes metadata harder to query — tools that read raw metadata expect plain strings for
  short values.
- Not idiomatic Cardano metadata.

### 3. Configurable threshold via annotation (rejected)

`@MetadataField(chunkSize = 64)` lets callers override the chunk size.

**Why rejected:**
- 64 bytes is a hard Cardano protocol limit, not a stylistic choice.
- Adding configuration for a fixed protocol constant adds complexity without benefit.

## Consequences

### Positive
- Generated code is self-contained and readable: the chunking logic is explicit in the source.
- No hidden dependency on `CBORMetadataMap` behaviour.
- `fromMetadataMap` handles both plain strings and lists, making it compatible with data
  written by any CIP-10-conformant producer.

### Neutral
- The 64-byte threshold appears in generated code. If the Cardano protocol ever increases this
  limit, generated code would need to be regenerated (acceptable — protocol limits rarely change).

### Negative
- Each String field adds several lines of generated code (the if/else + loop). For classes
  with many String fields this increases generated source size.

## Related

- ADR metadata/0001: Annotation Processor Core Design
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0004: @MetadataField(as=…) Type Override Mechanism
- CIP-10: Transaction Metadata (64-byte text limit)
- `StringUtils.splitStringEveryNCharacters` — utility used in generated code
