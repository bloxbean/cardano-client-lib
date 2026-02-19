# ADR metadata/0007: SortedSet<T> Field Support

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

ADR 0006 added `Set<T>` support using `LinkedHashSet` as the deserialization implementation.
`SortedSet<T>` is the natural next collection variant for domain models that require
**natural ordering guarantees** — e.g. a sorted set of policy IDs, sorted fee tiers, or
sorted block slots.

A `SortedSet<T>` field currently causes the processor to emit a WARNING and skip the field.

## Decision

Support `java.util.SortedSet<T>` as a field type, using the same element-type matrix as
`List<T>` and `Set<T>` (ADR 0005, ADR 0006), **except `byte[]`** which is excluded because
it does not implement `Comparable` and a `TreeSet<byte[]>` would throw `ClassCastException`
at runtime.

On-chain, `SortedSet<T>` is stored as `MetadataList` — identical to `List<T>` and `Set<T>`.
The difference is only on the Java side during deserialization: a `TreeSet` is produced,
which maintains the natural ordering of elements.

### Serialization (toMetadataMap)

The serialization loop is **identical to `List<T>` and `Set<T>`**: iterate the sorted set
with a for-each loop, skip null elements, and add each non-null element to a `MetadataList`
using the same element-type logic (including 64-byte sub-list chunking for `String`
elements). The existing `emitToMapPutList()` is reused as-is.

### Deserialization (fromMetadataMap)

A `TreeSet<T>` is instantiated rather than `ArrayList<T>` or `LinkedHashSet<T>`. The
element reading loop is otherwise identical, shared via the existing
`emitFromMapGetCollection(interfaceClass, implClass)` method (introduced in ADR 0006):

- List:       `interfaceClass=List`,       `implClass=ArrayList`
- Set:        `interfaceClass=Set`,        `implClass=LinkedHashSet`
- SortedSet:  `interfaceClass=SortedSet`,  `implClass=TreeSet`

**Why `TreeSet` and not a custom `Comparator`-based implementation?**

`TreeSet` uses natural ordering (`Comparable`) which is available for all supported scalar
types except `byte[]`. A `Comparator`-based alternative would require generating or
injecting user-supplied comparators, adding complexity with no clear use case. The natural
ordering of strings, integers, and BigDecimal values is well-defined and expected.

### Excluded element type: `byte[]`

`byte[]` arrays do not implement `Comparable`. A `TreeSet<byte[]>` will throw
`ClassCastException` the moment a second element is inserted. Therefore, `byte[]` is
**excluded** as a valid element type for `SortedSet<T>`:

- `MetadataAnnotationProcessor.isSupportedType()` rejects `SortedSet<byte[]>` with a
  WARNING and skips the field.
- The existing `Set<byte[]>` support is unaffected (`LinkedHashSet` does not require
  `Comparable`).

### `as=` restriction

Same as `List<T>` and `Set<T>` — WARNING emitted, forced to DEFAULT.

### Example

```java
@MetadataType
public class FeeSchedule {
    private SortedSet<BigInteger> tiers;    // sorted fee amounts on-chain
    private SortedSet<String> policyIds;   // lexicographically sorted IDs
}
```

Generated `toMetadataMap` fragment (identical to List/Set serialization):

```java
if (feeSchedule.getTiers() != null) {
    MetadataList _list = MetadataBuilder.createList();
    for (BigInteger _el : feeSchedule.getTiers()) {
        if (_el != null) {
            _list.add(_el);
        }
    }
    map.put("tiers", _list);
}
```

Generated `fromMetadataMap` fragment (differs only in `SortedSet` / `TreeSet`):

```java
v = map.get("tiers");
if (v instanceof MetadataList) {
    MetadataList _rawList = (MetadataList) v;
    SortedSet<BigInteger> _result = new TreeSet<>();
    for (int _i = 0; _i < _rawList.size(); _i++) {
        Object _el = _rawList.getValueAt(_i);
        if (_el instanceof BigInteger) {
            _result.add((BigInteger) _el);
        }
    }
    obj.setTiers(_result);
}
```

## Alternatives considered

### 1. Allow `byte[]` with a custom `Comparator` (rejected)

Generating a `Arrays.compare`-based comparator at code-gen time would work at runtime but
adds generated-code complexity for an edge case. The simpler and safer decision is to
reject `SortedSet<byte[]>` with a clear compile-time WARNING.

### 2. Use `LinkedHashSet` for `SortedSet<T>` (rejected)

Using `LinkedHashSet` as the implementation for a `SortedSet<T>` field would violate the
declared Java type contract — assigning a `LinkedHashSet` where `SortedSet` is declared
causes a `ClassCastException` at the call site.

### 3. Require `SortedSet<T>` fields to also implement `NavigableSet<T>` (deferred)

`TreeSet` implements `NavigableSet<T>` which extends `SortedSet<T>`. The generated
`_result` variable could be declared as `NavigableSet<T>`. This adds no value for
the current use cases and would require an additional import. Deferred.

## Trade-offs

### Positive
- Zero new runtime dependencies.
- Code sharing: `emitFromMapGetCollection()` is reused for all three collection types.
- `TreeSet` provides natural ordering — `first()` / `last()` and iteration in sort order.
- `SortedSet<byte[]>` is rejected at compile time with a clear WARNING, preventing runtime
  `ClassCastException`.

### Negative / Limitations
- **On-chain format is identical to List and Set**: consumers without the Java schema cannot
  distinguish a sorted set from a list or unsorted set.
- **Element order on-chain follows natural sort order**: the `TreeSet` iteration order
  determines the on-chain `MetadataList` element order. This is deterministic but may differ
  from insertion order, which could surprise consumers expecting insertion-order preservation.
- **No `byte[]` element type**: `SortedSet<byte[]>` fields are skipped with a WARNING, which
  may be confusing if the user already has `Set<byte[]>` fields working.

## Consequences

The `isSupportedType()` check in `MetadataAnnotationProcessor` now handles three collection
prefixes:
1. `java.util.List<` — all scalar types including `byte[]`
2. `java.util.Set<` — all scalar types including `byte[]`
3. `java.util.SortedSet<` — all scalar types **except** `byte[]`

The `emitFromMapGet()` dispatch in `MetadataConverterGenerator` now has three collection
branches, all delegating to `emitFromMapGetCollection(interfaceClass, implClass)` with the
appropriate class pair.

Adding future collection types (e.g. `NavigableSet<T>`, `Queue<T>`) follows the same
pattern: extend the `isSupportedType()` guard and add a dispatch branch.

## Related

- ADR metadata/0005: List\<T\> Field Support
- ADR metadata/0006: Set\<T\> Field Support
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- `MetadataConverterGenerator.emitFromMapGetCollection()` — shared deserialization
- `MetadataConverterGeneratorTest$SortedSetFields` — unit tests
- `SampleSortedSet` / `SampleSortedSetMetadataConverterIT` — integration test POJO and tests
