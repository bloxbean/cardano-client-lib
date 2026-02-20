# ADR metadata/0008: Optional<T> Field Support

- **Status**: Accepted
- **Date**: 2026-02-20
- **Deciders**: Cardano Client Lib maintainers

## Context

`List<T>`, `Set<T>`, and `SortedSet<T>` are now supported (ADR 0005–0007). The next
natural Java type to support is `java.util.Optional<T>`, which models **explicitly nullable
scalar fields**: the value may be present or absent, and the absence is meaningful.

Without this support, `Optional<T>` fields cause the processor to emit a WARNING and skip
the field entirely.

## Decision

Support `java.util.Optional<T>` as a field type for all scalar types already accepted by
`isSupportedScalarType()`. Raw primitives (`int`, `long`, etc.) cannot appear inside
`Optional<>` in valid Java, so this restriction is automatic.

### On-chain representation

A present `Optional` is serialized **identically to the corresponding plain scalar**: the
inner value is unwrapped with `.get()` and written directly to the metadata map. An absent
or null `Optional` means the key is **omitted** from the map — indistinguishable from a
missing plain field.

### Serialization (toMetadataMap)

The existing outer null guard (emitted by `buildToMetadataMapMethod`) handles `null`
Optional references. Inside that guard, an additional `isPresent()` check is emitted. If
present, `.get()` unwraps the value and `emitToMapPutDefault` is called with the element
type and the unwrapped expression:

```java
if (obj.getLabel() != null) {
    if (obj.getLabel().isPresent()) {
        // same as plain String — including 64-byte chunking for strings
        map.put("label", obj.getLabel().get());
    }
}
```

### Deserialization (fromMetadataMap)

The setter is **always called**, unlike plain scalars where it is only called on a type
match. This ensures the Optional field is always initialised after deserialization:

- On a type match: `obj.setX(Optional.of(value))`
- On no match (key absent or wrong type): `obj.setX(Optional.empty())`

For `Optional<String>`, both the plain `String` branch and the `MetadataList` chunk
reassembly branch wrap the result in `Optional.of(...)`:

```java
v = map.get("label");
if (v instanceof String) {
    obj.setLabel(Optional.of((String) v));
} else if (v instanceof MetadataList) {
    StringBuilder _sb = new StringBuilder();
    MetadataList _list = (MetadataList) v;
    for (int _i = 0; _i < _list.size(); _i++) {
        Object _chunk = _list.getValueAt(_i);
        if (_chunk instanceof String) {
            _sb.append((String) _chunk);
        }
    }
    obj.setLabel(Optional.of(_sb.toString()));
} else {
    obj.setLabel(Optional.empty());
}
```

For `Optional<BigInteger>`:

```java
v = map.get("amount");
if (v instanceof BigInteger) {
    obj.setAmount(Optional.of((BigInteger) v));
} else {
    obj.setAmount(Optional.empty());
}
```

### `as=` restriction

Same as collection fields — WARNING emitted, forced to DEFAULT (MVP scope).

### Supported element types

All scalar types supported by `isSupportedScalarType()`:
`String`, `BigInteger`, `BigDecimal`, `Long`, `Integer`, `Short`, `Byte`, `Boolean`,
`Double`, `Float`, `Character`, `byte[]`.

## Alternatives considered

### 1. Treat absent Optional identically to null on deserialization (rejected)

Leaving the field at its default Java value (`null`) when the key is missing would mean
the caller cannot distinguish "key was absent" from "field was never set". Always calling
`Optional.empty()` preserves the explicit-absence semantics of `Optional`.

### 2. Support `as=` overrides for Optional fields (deferred)

String encoding overrides (`STRING_HEX`, `STRING_BASE64`) are only meaningful for `byte[]`
and are already unsupported on collection fields. Extending `as=` to Optional would add
complexity with no clear use case at this stage.

### 3. Omit key on `Optional.empty()` vs. writing a sentinel (not chosen)

Writing a sentinel value (e.g. an empty string or `BigInteger.ZERO`) for absent optionals
would pollute the on-chain map and break symmetry with absent plain scalar fields. Omitting
the key is the correct on-chain representation for an absent optional.

## Trade-offs

### Positive
- Explicit nullable semantics: callers can tell "absent" from "present but zero/empty".
- Round-trip fidelity: `Optional.empty()` → serialize → deserialize → `Optional.empty()`.
- Zero new runtime dependencies.
- Reuses `emitToMapPutDefault` for serialization — element-type logic is not duplicated.

### Negative / Limitations
- **On-chain format is indistinguishable from a missing plain field**: consumers without
  the Java schema cannot tell whether an absent key means "null scalar" or "empty optional".
- **`null` Optional reference is silently treated as absent**: this follows the same
  convention as null plain scalars and collection fields.
- **`as=` not supported**: forced to DEFAULT with a WARNING.

## Consequences

- `isSupportedType()` in `MetadataAnnotationProcessor` now accepts `java.util.Optional<T>`
  for any scalar element type.
- `extractFields()` extracts `elementTypeName` for Optional fields (same guard as
  List/Set/SortedSet).
- `emitToMapPut()` in `MetadataConverterGenerator` dispatches to `emitToMapPutOptional()`
  before the `switch (as)` block.
- `emitFromMapGet()` dispatches to `emitFromMapGetOptional()`.
- Three new private helpers manage setter emission with `Optional.of()`/`Optional.empty()`.

## Related

- ADR metadata/0005: List\<T\> Field Support
- ADR metadata/0006: Set\<T\> Field Support
- ADR metadata/0007: SortedSet\<T\> Field Support
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- `MetadataConverterGeneratorTest$OptionalFields` — unit tests
- `SampleOptional` / `SampleOptionalMetadataConverterIT` — integration test POJO and tests
