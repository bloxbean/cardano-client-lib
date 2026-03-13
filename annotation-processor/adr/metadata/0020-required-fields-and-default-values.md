# ADR 0020: Required Fields and Default Values

## Status
Accepted

## Context
During deserialization, missing metadata keys silently result in `null` (or zero for primitives). Users needed two complementary mechanisms: (1) fail-fast validation when critical fields are absent, and (2) fallback defaults for optional fields with known default states.

## Decision
Add `required` and `defaultValue` attributes to `@MetadataField`. Both only affect deserialization — serialization is unchanged.

### Annotation API
```java
@MetadataField(required = true)
private String name;

@MetadataField(defaultValue = "UNKNOWN")
private String status;

@MetadataField(defaultValue = "42")
private int count;
```

### Required Fields
When `required = true`, the generated `fromMetadataMap` emits a null check that throws `IllegalArgumentException`:

```java
v = map.get("name");
if (v == null) {
    throw new IllegalArgumentException("Required metadata key 'name' is missing");
}
```

### Default Values
When `defaultValue` is set, the generated code injects a fallback before type conversion. The string value is parsed into the field's on-chain representation:

| Java Type | Default Expression |
|-----------|-------------------|
| `String`, time types, `URI`, `UUID`, etc. | `"value"` (string literal) |
| `int`, `short`, `byte`, `long` | `BigInteger.valueOf(NL)` |
| `BigInteger` | `new BigInteger("value")` |
| `boolean` | `BigInteger.valueOf(1L)` or `BigInteger.valueOf(0L)` |
| Enum types | `"VALUE"` (string literal) |

```java
v = map.get("status");
if (v == null) {
    v = "UNKNOWN";
}
// ... normal deserialization follows ...
```

### Validation Rules
- **Mutual exclusivity**: `required` + `defaultValue` on the same field is a compile-time ERROR (contradictory semantics)
- **Type restriction**: `defaultValue` only works on scalar and enum fields. Collections, maps, Optional, nested types, and byte[] emit a compile-time ERROR
- **Optional warning**: `required = true` on an `Optional<T>` field emits a WARNING (contradicts Optional semantics)
- **Adapter exclusivity**: `defaultValue` + `adapter` on the same field is a compile-time ERROR

### Record Support
Both features work identically in record mode — the null check / default injection happens before the local variable assignment, before the canonical constructor call.

### Example
```java
@MetadataType
public class Event {
    @MetadataField(required = true)
    private String name;

    @MetadataField(defaultValue = "PENDING")
    private String status;

    @MetadataField(defaultValue = "0")
    private int priority;

    @MetadataField(defaultValue = "true")
    private boolean active;
}
```

## Consequences
- Serialization is completely unaffected — no null checks or defaults during `toMetadataMap`
- Required fields provide clear error messages with the metadata key name
- Default values are injected as on-chain types (BigInteger for numbers, String for strings) so they flow through existing deserialization logic unchanged
- The `defaultValue` string is validated at code-generation time (e.g., `Long.parseLong` for integer types) — invalid values cause build-time failures
