# ADR metadata/0014: Null Handling Strategy

- **Status**: Accepted
- **Date**: 2026-03-12
- **Deciders**: Cardano Client Lib maintainers

## Context

Cardano metadata maps are sparse — keys can be present or absent. The generated converter
must decide how to handle Java `null` values during serialization (`toMetadataMap`) and how
to handle missing keys during deserialization (`fromMetadataMap`). These decisions interact
with every supported type category (primitives, reference scalars, collections, optionals,
enums) and must be consistent across the board.

## Decision

Adopt a **key-omission** strategy: null or absent values are represented by omitting the
key from the metadata map entirely, rather than storing a null/sentinel value.

### Serialization (`toMetadataMap`) — null guard rules

The processor wraps field serialization in `if (getExpr != null)` based on the field type:

| Field category | Null check? | Rationale |
|---|---|---|
| Primitive (`int`, `long`, `boolean`, etc.) | No | JVM guarantees a default value; cannot be null |
| Boxed primitive (`Integer`, `Long`, etc.) | Yes | Can be null |
| Reference scalar (`String`, `BigInteger`, `byte[]`, `URI`, etc.) | Yes | Can be null |
| Enum | Yes | Can be null |
| Collection (`List<T>`, `Set<T>`, `SortedSet<T>`) | Yes | Can be null |
| `Optional<T>` | Yes | Handled specially (see below) |

```java
// MetadataConverterGenerator.needsNullCheck()
private boolean needsNullCheck(String javaType, MetadataFieldInfo field) {
    if (field.isEnumType()) return true;
    if (!isScalar(javaType)) return true;        // collections, Optional
    MetadataTypeCodeGen codeGen = registry.get(javaType);
    return codeGen.needsNullCheck(javaType);      // delegates to type strategy
}
```

Each `MetadataTypeCodeGen` implementation determines whether its type needs a null check
via `needsNullCheck(javaType)`. Primitive types return `false`; all reference types return
`true`.

**When null**: the key is omitted from the metadata map (the `if` block is simply not entered).

### Serialization — Optional fields

`Optional<T>` fields use `if (getExpr.isPresent())` instead of a null check on the Optional
itself. When empty, the key is omitted — same as null for other types.

### Serialization — null elements in collections

Collections (List, Set, SortedSet) iterate their elements with an implicit null guard:

```java
// CollectionCodeGen — serialization loop
if (_el != null) {
    // serialize element
}
```

Null elements are **silently skipped** — they are not serialized. This prevents
`NullPointerException` during serialization and avoids inserting meaningless null entries
into the on-chain metadata list.

### Deserialization (`fromMetadataMap`) — missing key rules

Every field's deserialization starts with:

```java
v = map.get("keyName");
```

Then the type-specific code checks `if (v != null)` before setting the field:

| Field category | Missing key behavior |
|---|---|
| Primitive | Field retains JVM default (`0`, `false`, `'\0'`) |
| Reference scalar | Field retains `null` (setter not called) |
| Enum | Field retains `null` |
| Collection | Field retains `null` (no empty collection created) |
| `Optional<T>` | Setter called with `Optional.empty()` |

**Optional is the exception**: when the key is missing, the generated code explicitly calls
`obj.setFoo(Optional.empty())` rather than leaving the field as `null`. This ensures the
field is never `null` — it is always either `Optional.of(value)` or `Optional.empty()`,
matching the `Optional` contract.

```java
// OptionalCodeGen — deserialization
if (v != null) {
    obj.setFoo(Optional.of(/* deserialized value */));
} else {
    obj.setFoo(Optional.empty());
}
```

### Summary of invariants

1. **Null reference → key omitted** on serialization.
2. **Missing key → field untouched** on deserialization (except Optional → `empty()`).
3. **Primitives never null-checked** — JVM guarantees a value.
4. **Null collection elements silently dropped** — no nulls in metadata lists.
5. **Optional fields always set** — never left as raw `null`.

## Alternatives considered

### 1. Store null as a sentinel value (e.g. empty string, 0) (rejected)

Would conflate "field is absent" with "field is empty string" or "field is zero". Cardano
metadata has no native null type, so key omission is the natural representation.

### 2. Throw on null fields during serialization (rejected)

Would make the converter brittle. POJOs commonly have unset fields, especially when only a
subset of metadata is being written. Silent omission is the expected behavior for sparse maps.

### 3. Create empty collections for missing keys (rejected)

Would change the POJO state from "not present" (`null`) to "present but empty" (`[]`).
This distinction matters for round-trip fidelity: if a field was never set, deserializing
should not create an empty collection that wasn't there originally.

### 4. Leave Optional fields as null when key is missing (rejected)

Violates the `Optional` contract — callers expect `Optional.empty()`, not `null`. Returning
`null` from an `Optional` field causes `NullPointerException` on any subsequent `.isPresent()`
or `.orElse()` call.

## Trade-offs

### Positive
- Round-trip safe: serialize → deserialize preserves null/absent semantics.
- No sentinel values — clean metadata maps with only meaningful keys.
- Optional fields always safe to call `.isPresent()` on after deserialization.
- Primitives have zero overhead (no null check branch).

### Negative / Limitations
- **Null collection elements are silently dropped**: a `List` containing `[1, null, 3]`
  serializes as `[1, 3]`. This is intentional but may surprise users who expect null
  preservation.
- **No distinction between "field set to null" and "field never set"**: both result in
  key omission. This is inherent to the key-omission strategy.
- **Collections remain null after deserialization**: callers must null-check collection
  fields, unlike Optional which is always set to `empty()`. A future enhancement could
  initialize missing collections to empty, but this would break round-trip fidelity.

## Consequences

- `MetadataConverterGenerator.needsNullCheck()` is the central dispatch for null guard
  decisions, delegating to each `MetadataTypeCodeGen.needsNullCheck()`.
- `CollectionCodeGen` adds a per-element null guard in the serialization loop.
- `OptionalCodeGen` always emits both the `if` (present) and `else` (empty) branches
  in deserialization, unlike other types which only emit the `if` branch.
- `MetadataFieldAccessor` provides `emitSetOptionalEmpty()` for the `Optional.empty()` case.

## Related

- ADR metadata/0001: Annotation Processor Core Design — key-omission mentioned briefly
- ADR metadata/0005: List Type Support — collection serialization
- ADR metadata/0008: Optional Type Support — Optional absent semantics
- `MetadataConverterGenerator.needsNullCheck()` — null guard dispatch
- `MetadataConverterGenerator.buildToMetadataMapMethod()` — null guard emission
- `CollectionCodeGen` — null element filtering
- `OptionalCodeGen` — Optional.empty() deserialization
- `MetadataFieldAccessor.emitSetOptionalEmpty()` — empty setter emission
