# ADR metadata/0010: Enum Type Support

- **Status**: Accepted
- **Date**: 2026-02-20
- **Deciders**: Cardano Client Lib maintainers

## Context

Java `enum` types are ubiquitous in domain models — status codes, order states, currency
kinds, event types. Without explicit support, any `enum` field causes the processor to emit
a compile-time WARNING and silently skip the field.

Adding enum support requires special handling because enum type names are not fixed: the
fully-qualified name is determined by the user's code. The processor cannot enumerate all
possible enum names in a switch statement the way it does for `java.lang.String` or
`java.math.BigInteger`.

## Decision

Detect enum fields at annotation-processing time using the Java compiler's element model
(`TypeElement.getKind() == ElementKind.ENUM`). Store each constant as its **name string**
on-chain; reconstruct via `EnumType.valueOf(String)`.

### Detection mechanism

The existing `isSupportedScalarType()` switch works on fixed type name strings and cannot
cover arbitrary user-defined enum names. Instead, detection happens one level up in
`extractFields()`:

```java
Element typeEl = processingEnv.getTypeUtils().asElement(ve.asType());
boolean isEnum = typeEl != null && typeEl.getKind() == ElementKind.ENUM;

if (!isEnum && !isSupportedType(typeName)) {
    // emit WARNING and skip
}
```

A `boolean enumType` flag on `MetadataFieldInfo` carries this information through to the
code generator. The fully-qualified enum class name is stored in `javaTypeName` as normal.

### On-chain representation

Enum constants are stored as Cardano **text** using `name()`. This is:
- Stable across JVM versions (unlike `ordinal()`)
- Human-readable in block explorers
- Consistent with how string-like values are stored

### Serialization (toMetadataMap)

```java
if (obj.getStatus() != null) {
    map.put("status", obj.getStatus().name());
}
```

### Deserialization (fromMetadataMap)

```java
v = map.get("status");
if (v instanceof String) {
    obj.setStatus(Status.valueOf((String) v));
}
```

`EnumType.valueOf(String)` throws `IllegalArgumentException` if the string does not match
any constant. This is the correct behaviour per ADR 0004 — malformed on-chain data is a
data integrity issue.

### Generator dispatch

Because `enumType` is a flag on `MetadataFieldInfo` rather than a type name in
`isSupportedScalarType()`, the generator dispatches enum fields **before** the `switch(enc)`
block, alongside the existing Optional and collection dispatches:

```java
if (field.isEnumType()) {
    emitToMapPutEnum(builder, field, getExpr);
    return;
}
```

JavaPoet generates the correct import for the user's enum class using
`ClassName.bestGuess(field.getJavaTypeName())`.

### enc= on enum fields

`enc=DEFAULT` — always produces `name()` / `valueOf()` (the only sensible encoding).
`enc=STRING` — accepted by `isValidEnc()` (enum is not `byte[]`) but the dispatch happens
before the `switch(enc)` block so `enc` is effectively ignored; the generated code is
identical to `DEFAULT`.
`enc=STRING_HEX` / `enc=STRING_BASE64` — rejected with a compile error by `isValidEnc()`
(these are only valid for `byte[]`).

### Enum fields in collections and Optional

Enum elements inside `List<MyEnum>`, `Set<MyEnum>`, `Optional<MyEnum>` are **not
supported** in this revision. The collection and Optional machinery uses element type name
strings to drive deserialization code generation; extending that to arbitrary enum names
requires additional work (passing the enum class name alongside the element type string).

An unsupported `List<MyEnum>` field receives a compile-time WARNING and is skipped. This
is consistent with how other unsupported types are handled.

### Example

```java
public enum OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED }

@MetadataType
public class Order {
    @MetadataField(key = "st")
    private OrderStatus status;   // → text "CONFIRMED"
    private String      note;
}
```

Generated `toMetadataMap` fragment:

```java
if (order.getStatus() != null) {
    map.put("st", order.getStatus().name());
}
```

Generated `fromMetadataMap` fragment:

```java
v = map.get("st");
if (v instanceof String) {
    obj.setStatus(OrderStatus.valueOf((String) v));
}
```

## Alternatives considered

### 1. Add enum to `isSupportedScalarType()` with a wildcard or suffix check (rejected)

`isSupportedScalarType()` uses a `switch` on exact type name strings. There is no way to
match arbitrary user-defined names in a `switch`. A prefix/suffix heuristic (e.g. checking
if the name does not start with `java.`) would be fragile and produce false positives.

### 2. Store the ordinal as a Cardano integer (rejected)

`ordinal()` is fragile: adding or reordering constants in the enum changes all ordinals,
silently corrupting existing on-chain data. The name string is stable across constant
additions as long as constants are not renamed.

### 3. Support `enc=STRING` to force a different representation (rejected / deferred)

Enum has only one meaningful text representation: the constant name. An `enc=ORDINAL` enum
value could be added to `MetadataFieldType` in a future revision if there is a use case
for compact integer storage, but this is explicitly out of scope here.

### 4. Support enum in collections (deferred)

Extending `List<MyEnum>` support requires carrying the concrete enum class through the
element-type pipeline. The element type is currently represented as a plain `String` used
to select code generation paths. Adding a parallel "enum element class" field to
`MetadataFieldInfo` and extending `emitListElementAdd/Read` is straightforward but
deferred to keep this revision minimal.

## Trade-offs

### Positive
- Any user-defined enum type is automatically supported with no code changes to the processor.
- Enum names on-chain are human-readable and stable across ordinal changes.
- Detection via `ElementKind.ENUM` is reliable — it uses the compiler's own type model.
- `EnumType.valueOf()` produces a compile-time error if the enum class is renamed, alerting
  the developer to a potential on-chain compatibility break.

### Negative / Limitations
- **Enum-in-collections not supported**: `List<MyEnum>` fields are silently skipped.
- **Renaming a constant is a breaking on-chain change**: existing metadata with the old
  constant name cannot be deserialized. This is the trade-off of name-based storage vs.
  ordinal-based storage and should be documented at the application level.
- **`enc=` is silently ignored** for enum fields (dispatch happens before the switch).
  A warning could be emitted for non-DEFAULT `enc=` on enum fields, but is not done here
  to keep the implementation minimal.

## Consequences

- `MetadataFieldInfo` gains a `boolean enumType` field.
- `MetadataAnnotationProcessor.extractFields()` performs an `ElementKind.ENUM` check before
  `isSupportedType()`, bypassing the scalar type switch for enum fields.
- `MetadataConverterGenerator.emitToMapPut()` and `emitFromMapGet()` dispatch on
  `field.isEnumType()` before the `switch(enc)` block.
- Two new private methods: `emitToMapPutEnum()` and `emitFromMapGetEnum()`.

## Related

- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0004: @MetadataField(enc=…) Type Override Mechanism
- `MetadataFieldInfo.enumType` — flag set by the annotation processor
- `MetadataConverterGenerator.emitToMapPutEnum()` / `emitFromMapGetEnum()` — code generation
- `MetadataConverterGeneratorTest$EnumFields` — unit tests
