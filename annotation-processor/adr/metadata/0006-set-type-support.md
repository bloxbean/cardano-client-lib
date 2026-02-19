# ADR metadata/0006: Set<T> Field Support

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

ADR 0005 added `List<T>` support. `Set<T>` is the next most commonly needed Java collection
type. A `Set<T>` field currently causes the processor to emit a WARNING and skip the field.

The need arises from schemas where the Java domain model expresses uniqueness constraints —
a set of policy IDs, a set of feature tags, a set of unique amounts — that have no direct
equivalent in Cardano metadata but should still be serialisable without manual boilerplate.

## Decision

Support `java.util.Set<T>` as a field type, using the same element-type matrix as `List<T>`
(ADR 0005). On-chain, both are stored as `MetadataList`. The difference is only on the
Java side during deserialization: a `LinkedHashSet` is produced instead of an `ArrayList`.

### Serialization (toMetadataMap)

The serialization loop is **identical to `List<T>`**: iterate the set with a for-each loop,
skip null elements, and add each non-null element to a `MetadataList` using the same
element-type logic from ADR 0005 (including 64-byte sub-list chunking for `String`
elements).

The existing `emitToMapPutList()` method is reused as-is for both `List<T>` and `Set<T>`.

### Deserialization (fromMetadataMap)

A `LinkedHashSet<T>` is instantiated rather than an `ArrayList<T>`. The element reading
loop is otherwise identical.

**Why `LinkedHashSet` and not `HashSet` or `TreeSet`?**

| Implementation | Reason rejected / accepted |
|---|---|
| `HashSet` | Random iteration order makes round-trip test assertions fragile and produced set element order unpredictable |
| `TreeSet` | Requires elements to implement `Comparable`; `byte[]` does not, so `Set<byte[]>` would throw at runtime |
| `LinkedHashSet` | ✓ Preserves insertion order (= MetadataList order); no `Comparable` requirement |

### Code sharing via `emitFromMapGetCollection()`

Rather than duplicating the deserialization method, the existing `emitFromMapGetList()` was
renamed to `emitFromMapGetCollection(ClassName interfaceClass, ClassName implClass)`. Both
`List<T>` and `Set<T>` call this shared method, passing the appropriate interface and
implementation class names:

- List: `interfaceClass=List`, `implClass=ArrayList`
- Set:  `interfaceClass=Set`, `implClass=LinkedHashSet`

### Uniqueness semantics on round-trip

On the Java side, inserting duplicates into a `Set` removes them. When a `Set` is
serialised to a `MetadataList`, all elements present in the set are written (duplicates
were already removed by Java). On deserialisation, the `LinkedHashSet` will again deduplicate
if the on-chain list contains duplicates (e.g. produced by a third-party tool). This is
the correct behaviour: the Java type contract is respected.

**Note**: if a `Set` is written by this library and read back by this library, no
deduplication occurs on the read path because no duplicates were written. Round-trips
through third-party tools that produce duplicate list entries will silently deduplicate
on read.

### What is not supported

| Feature | Decision |
|---|---|
| `as=` override on Set fields | **Ignored with WARNING** (same as List) |
| `Set<Set<T>>` (nested sets) | **Not supported**; field skipped with WARNING |
| `SortedSet<T>`, `NavigableSet<T>` | **Not supported**; only `java.util.Set` recognised |
| `null` elements within a set | **Silently skipped** (Java `Set` cannot contain null in `LinkedHashSet` without caution; the null check in the generated loop prevents issues) |

### Example

```java
@MetadataType
public class Policy {
    private Set<String> policyIds;         // unique text list on-chain
    private Set<BigInteger> amounts;       // unique integer list on-chain
}
```

Generated `toMetadataMap` fragment (identical to List<T>):

```java
if (policy.getPolicyIds() != null) {
    MetadataList _list = MetadataBuilder.createList();
    for (String _el : policy.getPolicyIds()) {
        if (_el != null) {
            // 64-byte chunking as per ADR 0003 / ADR 0005
            _list.add(_el);
        }
    }
    map.put("policyIds", _list);
}
```

Generated `fromMetadataMap` fragment (differs only in `Set` / `LinkedHashSet`):

```java
v = map.get("policyIds");
if (v instanceof MetadataList) {
    MetadataList _rawList = (MetadataList) v;
    Set<String> _result = new LinkedHashSet<>();
    for (int _i = 0; _i < _rawList.size(); _i++) {
        Object _el = _rawList.getValueAt(_i);
        if (_el instanceof String) {
            _result.add((String) _el);
        } else if (_el instanceof MetadataList) {
            // chunked string reassembly (ADR 0005)
        }
    }
    obj.setPolicyIds(_result);
}
```

## Alternatives considered

### 1. `HashSet` as implementation (rejected)

`HashSet` has non-deterministic iteration order. Round-trip tests would need `containsAll`
instead of `assertEquals`, and the on-chain order of elements (which is observable in block
explorers) would vary between JVM runs. `LinkedHashSet` gives stable, predictable output.

### 2. Support `SortedSet<T>` / `TreeSet<T>` (deferred)

`TreeSet` preserves natural ordering and could be useful for numeric or lexicographic sets.
However, `byte[]` does not implement `Comparable`, so `TreeSet<byte[]>` would throw a
`ClassCastException` at runtime. Supporting `SortedSet` would require excluding `byte[]`
as an element type, adding conditional logic that increases complexity. Deferred until a
concrete use case arises.

### 3. Treat Set and List as the same type (rejected)

Always deserialise to `ArrayList` regardless of the declared Java type. This breaks the
Java contract: a `Set<String>` field would hold an `ArrayList`, causing
`ClassCastException` if caller code casts the result.

## Trade-offs

### Positive
- Zero new runtime dependencies.
- Code sharing: `emitFromMapGetCollection()` is reused for both `List<T>` and `Set<T>`.
- `LinkedHashSet` preserves insertion order, making round-trip tests deterministic.
- `Set` uniqueness semantics are honoured on the Java side in both directions.

### Negative / Limitations
- **On-chain format is identical to List**: a consumer that reads the metadata without
  access to the Java schema cannot distinguish a set from a list.
- **Duplicates silently deduplicated on read**: if a third-party tool writes a MetadataList
  with duplicate entries under a key that maps to a `Set<T>` field, the duplicates are
  silently removed. This is correct Java `Set` behaviour but may surprise consumers
  expecting all on-chain list elements to be preserved.
- **No `SortedSet<T>` support**: ordering of set elements on-chain follows insertion order
  of the Java `LinkedHashSet`, not a natural sort order.

## Consequences

The `isSupportedType()` check in `MetadataAnnotationProcessor` now handles both
`java.util.List<` and `java.util.Set<` prefixes uniformly. Adding future collection types
(e.g. `Queue<T>`) requires only extending this guard and adding an `emitFromMapGetCollection`
dispatch — the serialization loop and element-level code are already shared.

## Related

- ADR metadata/0005: List\<T\> Field Support
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- `MetadataConverterGenerator.emitFromMapGetCollection()` — shared deserialization
- `MetadataConverterGeneratorTest$SetFields` — unit tests
- `SampleSet` / `SampleSetMetadataConverterIT` — integration test POJO and tests
