# ADR metadata/0005: List<T> Field Support

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

Real-world Cardano metadata schemas frequently contain ordered collections: a list of asset
names, a list of policy IDs, a list of amounts, a list of feature flags. Prior to this ADR
the annotation processor only handled scalar fields, requiring developers to either manage
`MetadataList` manually or represent collections as separate top-level keys.

The goal is to support `List<T>` fields where T is any scalar type already handled by the
processor, mapping to Cardano's native **list** type (`MetadataList`).

## Decision

Support `java.util.List<T>` as a first-class field type. The processor recognises it during
field scanning and the generator emits a `MetadataList`-based serialization loop for
`toMetadataMap` and a typed `ArrayList`-based deserialization loop for `fromMetadataMap`.

### Supported element types

All scalar types from ADR 0002 are valid element types:

| `List<T>`             | Cardano element type | Write expression                              | Read back                                |
|-----------------------|----------------------|-----------------------------------------------|------------------------------------------|
| `List<String>`        | text (or sub-list)   | direct / sub-list chunks for > 64 bytes       | `instanceof String` / `instanceof MetadataList` reassembly |
| `List<BigInteger>`    | integer              | `_list.add(_el)`                              | `instanceof BigInteger` cast             |
| `List<Long>`          | integer              | `_list.add(BigInteger.valueOf(_el))`          | `.longValue()`                           |
| `List<Integer>`       | integer              | `_list.add(BigInteger.valueOf((long) _el))`   | `.intValue()`                            |
| `List<Short>`         | integer              | `_list.add(BigInteger.valueOf((long) _el))`   | `.shortValue()`                          |
| `List<Byte>`          | integer              | `_list.add(BigInteger.valueOf((long) _el))`   | `.byteValue()`                           |
| `List<Boolean>`       | integer              | `_list.add(_el ? BigInteger.ONE : BigInteger.ZERO)` | `BigInteger.ONE.equals(_el)`       |
| `List<Double>`        | text                 | `_list.add(String.valueOf(_el))`              | `Double.parseDouble`                     |
| `List<Float>`         | text                 | `_list.add(String.valueOf(_el))`              | `Float.parseFloat`                       |
| `List<Character>`     | text                 | `_list.add(String.valueOf(_el))`              | `.charAt(0)`                             |
| `List<BigDecimal>`    | text                 | `_list.add(_el.toPlainString())`              | `new BigDecimal((String) _el)`           |
| `List<byte[]>`        | bytes                | `_list.add(_el)`                              | `instanceof byte[]` cast                 |

Primitive generics (`List<int>`, `List<boolean>`, etc.) are not legal Java and are therefore
not supported. Only boxed types appear as valid element types.

### What is not supported

| Feature | Decision |
|---|---|
| `as=` override on List fields | **Ignored with WARNING**; DEFAULT always used |
| `List<List<T>>` (nested lists) | **Not supported**; field is skipped with WARNING |
| `Set<T>`, `Collection<T>` | **Not supported**; only `java.util.List` recognised |
| `Map<K,V>` as element type | **Not supported** |
| `null` elements within a list | **Silently skipped** during serialization |
| Ordering guarantees | Preserved — `MetadataList` is ordered, `ArrayList` preserves insertion order |

### String element chunking (64-byte rule)

`List<String>` inherits the same 64-byte rule from ADR 0003. Each element is checked
independently:

- **Short element (≤ 64 UTF-8 bytes)**: added directly to the outer `MetadataList`.
- **Long element (> 64 UTF-8 bytes)**: wrapped in a sub-`MetadataList` of ≤ 64-char
  chunks and that sub-list is added to the outer list.

On read-back, each outer list element is inspected:
- `instanceof String` → used directly.
- `instanceof MetadataList` → chunks are concatenated via `StringBuilder`.

This mirrors the scalar `String` handling from ADR 0003 but operates per-element
rather than at the top-level map value.

### Null-list handling

A `null` `List` field is subject to the same null-guard rule as any other reference type:
no entry is written to the `MetadataMap`. On read-back an absent key leaves the field at
its Java default (`null`).

### Variable scoping

All local variables emitted for list processing (`_list`, `_rawList`, `_result`, `_el`,
`_elChunks`, `_sb`, `_elList`) are declared inside `if`/`for` control-flow blocks. Java's
block scoping means multiple `List<T>` fields in the same class produce no name collisions
in the generated source.

### The `as=` restriction

The `as=` attribute on `@MetadataField` specifies the on-chain representation of a scalar
value. For lists it is unclear which semantic should apply: element-level encoding,
container-level encoding, or something else. Rather than guessing and producing silent
surprises, the processor emits a **WARNING** and falls back to DEFAULT. Future ADRs may
define element-level overrides if a concrete use case arises.

### Example

```java
@MetadataType
public class TokenBundle {

    private List<String> assetNames;          // text list, long names auto-chunked

    @MetadataField(key = "qty")
    private List<BigInteger> quantities;      // integer list

    private List<Boolean> activeFlags;        // 0/1 integer list

    private List<BigDecimal> prices;          // text list via toPlainString()

    private List<byte[]> signatures;          // bytes list
}
```

Generated `toMetadataMap` fragment for `assetNames`:

```java
if (tokenBundle.getAssetNames() != null) {
    MetadataList _list = MetadataBuilder.createList();
    for (String _el : tokenBundle.getAssetNames()) {
        if (_el != null) {
            if (_el.getBytes(StandardCharsets.UTF_8).length > 64) {
                MetadataList _elChunks = MetadataBuilder.createList();
                for (String _part : StringUtils.splitStringEveryNCharacters(_el, 64)) {
                    _elChunks.add(_part);
                }
                _list.add(_elChunks);
            } else {
                _list.add(_el);
            }
        }
    }
    map.put("assetNames", _list);
}
```

Generated `fromMetadataMap` fragment for `assetNames`:

```java
v = map.get("assetNames");
if (v instanceof MetadataList) {
    MetadataList _rawList = (MetadataList) v;
    List<String> _result = new ArrayList<>();
    for (int _i = 0; _i < _rawList.size(); _i++) {
        Object _el = _rawList.getValueAt(_i);
        if (_el instanceof String) {
            _result.add((String) _el);
        } else if (_el instanceof MetadataList) {
            StringBuilder _sb = new StringBuilder();
            MetadataList _elList = (MetadataList) _el;
            for (int _j = 0; _j < _elList.size(); _j++) {
                Object _chunk = _elList.getValueAt(_j);
                if (_chunk instanceof String) _sb.append((String) _chunk);
            }
            _result.add(_sb.toString());
        }
    }
    obj.setAssetNames(_result);
}
```

## Alternatives considered

### 1. Support `Set<T>` and `Collection<T>` (deferred)

Recognising any `java.util.Collection<T>` subtype would cover `Set`, `LinkedList`, etc.

**Why deferred:**
- `MetadataList` is ordered; `Set` semantics (uniqueness, no ordering) don't map cleanly.
- The getter return type comparison in `findGetter()` uses exact string matching on the
  type name, so `Set<String>` getters would need separate handling.
- No concrete use case for the added complexity at MVP stage.

### 2. Element-level `as=` override (deferred)

A `listElementAs = MetadataFieldType.STRING_HEX` attribute to encode each `byte[]`
element as a hex string.

**Why deferred:**
- No confirmed real-world use case yet.
- Would require extending `@MetadataField` API and the generator switch logic.
- Current WARNING-and-DEFAULT approach is safe: no silent data corruption.

### 3. Runtime helper instead of inlined loop (rejected)

Emit a call to a static utility method (e.g., `MetadataListUtil.toList(getter, ...)`)
instead of an inline loop.

**Why rejected:**
- Requires shipping a runtime helper class in the `metadata` module.
- The inlined loop is self-contained, readable, and has zero runtime dependencies beyond
  the existing `MetadataList`/`MetadataBuilder` API.
- Consistent with the existing inline approach used for scalar fields.

### 4. Emit `List<Object>` raw type (rejected)

Use a raw `List` (no generic parameter) in the generated `fromMetadataMap` to avoid
JavaPoet parameterised type handling.

**Why rejected:**
- Produces unchecked-cast warnings in downstream consumer code.
- Loses IDE type inference for the returned list.
- `ParameterizedTypeName.get(List.class, elementType)` in JavaPoet handles all supported
  element types including `byte[]` (`ArrayTypeName.of(TypeName.BYTE)`) correctly.

## Trade-offs

### Positive
- Zero new runtime dependencies; uses existing `MetadataList` / `MetadataBuilder` APIs.
- `null` list → absent from map; empty list → empty `MetadataList`. Both round-trip
  correctly (modulo `null` vs absent, which is standard metadata behaviour).
- `List<String>` long-element chunking is fully automatic and consistent with scalar
  `String` behaviour from ADR 0003.
- Block-scoped local variables mean multiple `List<T>` fields in one class generate
  no name collision.

### Negative / Limitations
- **Homogeneous lists only**: all elements must share the same declared type. Heterogeneous
  `List<Object>` fields are not supported and will be skipped with a WARNING.
- **No element-level `as=` override**: hex/Base64 encoding cannot be applied to individual
  list elements. A `List<byte[]>` always stores raw bytes.
- **`null` elements silently dropped**: a `null` inside the list is skipped during
  serialization and cannot be represented on-chain. Callers that need to preserve index
  positions of null entries must pre-process the list.
- **No `Set<T>` / ordering-sensitive types**: ordering is always preserved (insertion order
  of the Java list), but uniqueness constraints are not enforced.
- **Flat list only**: `List<List<T>>` is rejected. Nested Cardano list structures require
  manual `MetadataList` construction.

## Consequences

Getter and setter detection reuses the existing `findGetter` / `findSetter` logic unchanged,
since `List<String> getTags()` / `void setTags(List<String>)` follow the standard POJO
naming convention that the processor already handles.

The `isSupportedType()` check is now a two-level test: scalar check first, then List-wrapper
check. New scalar types added in future ADRs automatically become valid List element types
without additional changes to the List handling code.

## Related

- ADR metadata/0001: Annotation Processor Core Design
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- ADR metadata/0004: @MetadataField(as=…) Type Override Mechanism
- `MetadataConverterGenerator.emitToMapPutList()` / `emitFromMapGetList()`
- `MetadataConverterGeneratorTest$ListFields` — unit tests
- `SampleList` / `SampleListMetadataConverterIT` — integration test POJO and tests
