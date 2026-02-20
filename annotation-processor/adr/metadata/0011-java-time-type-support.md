# ADR metadata/0011: Date and Time Type Support (Instant, LocalDate, LocalDateTime, Date)

- **Status**: Accepted
- **Date**: 2026-02-20
- **Deciders**: Cardano Client Lib maintainers

## Context

Timestamps and dates are among the most common non-primitive fields in domain models that
get attached to Cardano metadata — transaction timestamps, validity periods, settlement
dates. Without explicit support, `java.time.Instant`, `java.time.LocalDate`,
`java.time.LocalDateTime`, and `java.util.Date` fields receive a compile-time WARNING and
are silently skipped.

Each type has a different relationship with timezones and precision:

| Type | Has timezone? | Natural integer form | Natural text form |
|---|---|---|---|
| `Instant` | Yes (UTC point in time) | Epoch seconds | ISO-8601 `"2024-01-15T10:30:00Z"` |
| `LocalDate` | No | Epoch day | ISO-8601 `"2024-01-15"` |
| `LocalDateTime` | No | None (ambiguous) | ISO-8601 `"2024-01-15T10:30:00"` |
| `java.util.Date` | Yes (UTC, millisecond precision) | Epoch milliseconds | ISO-8601 via `toInstant()` |

`Instant`, `LocalDate`, and `Date` each have two meaningful on-chain representations.
`LocalDateTime` has only one: ISO-8601 text.

## Decision

Add all four types to `isSupportedScalarType()`. The `enc=` attribute (ADR 0004) selects
between representations where multiple exist. The existing `MetadataFieldType` enum values
(`DEFAULT` and `STRING`) are sufficient — no new enum values are needed.

### Instant

| `enc=` | Cardano type | Serialize | Deserialize |
|---|---|---|---|
| `DEFAULT` | integer | `BigInteger.valueOf(v.getEpochSecond())` | `Instant.ofEpochSecond(((BigInteger) v).longValue())` |
| `STRING` | text | `v.toString()` | `Instant.parse((String) v)` |

**Why DEFAULT = epoch seconds, not ISO-8601?**
An integer is more compact in CBOR, more interoperable with other Cardano tooling that
inspects raw metadata, and consistent with how the Cardano ecosystem represents timestamps
(slot numbers, POSIX time). The ISO-8601 option is available via `enc=STRING`.

**Why epoch seconds, not epoch milliseconds?**
Cardano slot resolution is 1 second (Shelley and later). Millisecond precision provides no
practical benefit in Cardano metadata and doubles the integer size for large timestamps.
An `enc=EPOCH_MILLI` value could be added to `MetadataFieldType` in a future revision if
millisecond precision is required.

**Round-trip precision**: epoch seconds truncates sub-second precision. An `Instant` with
nanoseconds serialized as epoch seconds and deserialized loses the nanosecond component.
This is a deliberate trade-off for compactness; use `enc=STRING` if sub-second precision
must be preserved.

### LocalDate

| `enc=` | Cardano type | Serialize | Deserialize |
|---|---|---|---|
| `DEFAULT` | integer | `BigInteger.valueOf(v.toEpochDay())` | `LocalDate.ofEpochDay(((BigInteger) v).longValue())` |
| `STRING` | text | `v.toString()` | `LocalDate.parse((String) v)` |

**Why DEFAULT = epoch day?**
Consistent with `Instant`'s DEFAULT being an integer: compact, timezone-free (epoch day
does not depend on timezone), and unambiguous. The ISO-8601 string alternative is available
via `enc=STRING`.

### LocalDateTime

| `enc=` | Cardano type | Serialize | Deserialize |
|---|---|---|---|
| `DEFAULT` | text | `v.toString()` | `LocalDateTime.parse((String) v)` |
| `STRING` | text | same as DEFAULT (no-op) | same as DEFAULT |

**Why no integer DEFAULT for LocalDateTime?**
`LocalDateTime` carries date and time but **no timezone**. Converting to epoch seconds
requires choosing a timezone (typically UTC), which is an implicit assumption that cannot
be recovered at deserialization time. If the user's JVM timezone is not UTC, the round-trip
would silently corrupt the value. ISO-8601 text is the only representation that faithfully
preserves a `LocalDateTime` value without external assumptions.

The `enc=STRING` value is accepted but is a no-op — the generated code is identical to
`DEFAULT`. This mirrors the behaviour of other text-native types (`double`, `float`, `char`)
where `enc=STRING` is accepted without error and generates the same output.

### java.util.Date

| `enc=` | Cardano type | Serialize | Deserialize |
|---|---|---|---|
| `DEFAULT` | integer | `BigInteger.valueOf(v.getTime())` | `new Date(((BigInteger) v).longValue())` |
| `STRING` | text | `v.toInstant().toString()` | `Date.from(Instant.parse((String) v))` |

**Why DEFAULT = epoch milliseconds, not epoch seconds?**
`Date.getTime()` is specified to return milliseconds since the Unix epoch — this is the
type's native precision. Unlike `Instant` (where epoch seconds is a deliberate compactness
trade-off justified by Cardano's 1-second slot resolution), `Date` carries no nanosecond
component to truncate. Using milliseconds preserves the full precision of the value and
avoids a lossy conversion that could corrupt sub-second timestamps in existing domain models.

**Why STRING = `toInstant().toString()` (ISO-8601 with `Z` suffix)?**
`Date` has no `format()` method and its `toString()` output is locale/timezone-dependent
(not round-trippable). `date.toInstant().toString()` always produces a stable ISO-8601
string in UTC (e.g. `"2024-01-15T10:30:00.123Z"`), which `Date.from(Instant.parse(str))`
can reconstruct exactly.

**Comparison with `Instant`:** `java.util.Date` is the legacy date-time type. New code
should prefer `java.time.Instant`. The processor supports `Date` for compatibility with
existing domain models that cannot be migrated.

### Generated examples

```java
@MetadataType
public class Event {
    private Instant       createdAt;                            // DEFAULT: epoch seconds
    @MetadataField(enc = MetadataFieldType.STRING)
    private Instant       expiresAt;                            // STRING: ISO-8601
    private LocalDate     settlementDate;                       // DEFAULT: epoch day
    @MetadataField(enc = MetadataFieldType.STRING)
    private LocalDate     announcedOn;                          // STRING: ISO-8601 date
    private LocalDateTime scheduledAt;                          // DEFAULT (ISO-8601 text)
    private Date          legacyTimestamp;                      // DEFAULT: epoch millis
    @MetadataField(enc = MetadataFieldType.STRING)
    private Date          legacyExpiry;                         // STRING: ISO-8601
}
```

`toMetadataMap` fragments:
```java
if (event.getCreatedAt() != null) {
    map.put("createdAt", BigInteger.valueOf(event.getCreatedAt().getEpochSecond()));
}
if (event.getExpiresAt() != null) {
    map.put("expiresAt", event.getExpiresAt().toString());
}
if (event.getSettlementDate() != null) {
    map.put("settlementDate", BigInteger.valueOf(event.getSettlementDate().toEpochDay()));
}
if (event.getAnnouncedOn() != null) {
    map.put("announcedOn", event.getAnnouncedOn().toString());
}
if (event.getScheduledAt() != null) {
    map.put("scheduledAt", event.getScheduledAt().toString());
}
if (event.getLegacyTimestamp() != null) {
    map.put("legacyTimestamp", BigInteger.valueOf(event.getLegacyTimestamp().getTime()));
}
if (event.getLegacyExpiry() != null) {
    map.put("legacyExpiry", event.getLegacyExpiry().toInstant().toString());
}
```

### enc= validation

`enc=STRING_HEX` and `enc=STRING_BASE64` on any of these types produce a compile error
(only valid for `byte[]`, enforced by `isValidEnc()`).

### Collection and Optional support

All four types are added to `isSupportedScalarType()`, which automatically enables
`List<Instant>`, `Set<LocalDate>`, `Optional<LocalDateTime>`, `Optional<Date>`, etc.
Collection elements always use the **DEFAULT** encoding regardless of any `enc=` on the
containing field — consistent with how collections handle all other types.

`Instant`, `LocalDate`, `LocalDateTime`, and `Date` all implement `Comparable`, so all
four are permitted as `SortedSet<T>` element types.

### Generating code with two `$T` placeholders

Deserialization for `Instant`, `LocalDate`, and `Date` with DEFAULT encoding requires two
type references in one statement (e.g. `Instant.ofEpochSecond(((BigInteger) v).longValue())`
or `new Date(((BigInteger) v).longValue())`).
JavaPoet's `addStatement` accepts variadic `$T` arguments, so these are emitted inline
rather than via the single-arg `addSetterStatement()` helper:

```java
builder.addStatement("obj.$L($T.ofEpochSecond((($T) v).longValue()))",
        field.getSetterName(), Instant.class, BigInteger.class);

builder.addStatement("obj.$L(new $T((($T) v).longValue()))",
        field.getSetterName(), Date.class, BigInteger.class);
```

## Alternatives considered

### 1. Support only Instant (rejected)

`Instant` is the most common of the three java.time types, but `LocalDate` and
`LocalDateTime` appear in domain models that deal with settlement dates, business dates,
and scheduled events. `java.util.Date` is needed for compatibility with legacy code.
Implementing all types together is no more complex than implementing one.

### 2. DEFAULT = ISO-8601 text for all types (rejected for Instant, LocalDate, Date)

A uniform DEFAULT of ISO-8601 text would be simpler but wastes on-chain space for
`Instant` (25+ chars vs 4–5 bytes for a BigInteger), `LocalDate` (10 chars vs 3 bytes),
and `Date` (24+ chars vs 6–8 bytes for epoch millis).
Cardano metadata fees are proportional to transaction size; compact representation matters.

### 3. Add EPOCH_MILLI to MetadataFieldType (deferred)

Millisecond-precision timestamps for `Instant` would require a new enum value. `Date`
uses milliseconds natively as its DEFAULT. The use case for `enc=EPOCH_MILLI` on `Instant`
in Cardano metadata is not clear enough to justify extending the public API at this stage.

### 4. Support ZonedDateTime / OffsetDateTime (deferred)

These types carry timezone information but are rarely needed in metadata schemas where
`Instant` (UTC point in time) suffices. Adding them follows the same pattern as
`LocalDateTime` but is deferred to keep this revision focused.

### 5. Use epoch seconds for Date DEFAULT (rejected)

Using `date.getTime() / 1000` would make `Date` consistent with `Instant`'s DEFAULT
(epoch seconds) but would silently truncate millisecond precision. Since `Date.getTime()`
is defined to return milliseconds, using milliseconds is the natural, lossless choice.
Users who need epoch-second semantics should prefer `java.time.Instant`.

## Trade-offs

### Positive
- All common date-time types — both modern (`java.time`) and legacy (`java.util.Date`) —
  are supported with sensible defaults.
- Compact integer DEFAULT for `Instant`, `LocalDate`, and `Date` reduces CBOR payload size.
- ISO-8601 STRING alternative is available for human-readable on-chain data.
- `LocalDateTime`'s timezone-free semantics are preserved — no implicit UTC assumption.
- `Date` DEFAULT uses full millisecond precision — no silent data loss.
- All four types implement `Comparable` so `SortedSet<T>` works without exclusions.

### Negative / Limitations
- **`Instant` DEFAULT truncates sub-second precision** to epoch seconds. Users requiring
  nanosecond fidelity must use `enc=STRING`.
- **`LocalDateTime` DEFAULT is ISO-8601 text**, making it less compact than the integer
  representations used for `Instant`, `LocalDate`, and `Date`. This is unavoidable without
  a timezone assumption.
- **`enc=STRING` on `LocalDateTime` is a no-op** (no warning emitted). This is
  consistent with other text-native types (`double`, `char`) but may be surprising.
- **Collection elements always use DEFAULT** encoding. A `List<Instant>` always stores
  epoch seconds; there is no way to request ISO-8601 elements.
- **`java.util.Date` is a legacy type**. New code should prefer `java.time.Instant`.
  Support is provided for compatibility with existing domain models.

## Consequences

- `MetadataAnnotationProcessor.isSupportedScalarType()` gains four new `case` labels.
- `MetadataConverterGenerator` gains `case` branches in `emitToMapPutDefault()`,
  `emitToMapPutAsString()`, `emitFromMapGetDefault()`, `emitFromMapGetAsString()`,
  `emitListElementAdd()`, `emitListElementRead()`, `emitFromMapGetOptional()`, and
  `elementTypeName()`.
- `Instant`, `LocalDate`, and `Date` DEFAULT deserialization is emitted inline (two `$T`
  args) rather than via the single-arg `addSetterStatement()` helper.
- `Date` STRING deserialization references both `Date` and `Instant` in one statement
  and is also emitted inline.

## Related

- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0004: @MetadataField(enc=…) Type Override Mechanism
- ADR metadata/0005: List\<T\> Field Support
- ADR metadata/0008: Optional\<T\> Field Support
- `MetadataConverterGeneratorTest$InstantFields` / `LocalDateFields` / `LocalDateTimeFields` / `DateFields` — unit tests
