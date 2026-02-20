# ADR metadata/0009: String-Coercible Scalar Types (URI, URL, UUID, Currency, Locale)

- **Status**: Accepted
- **Date**: 2026-02-20
- **Deciders**: Cardano Client Lib maintainers

## Context

The initial scalar type mapping (ADR 0002) covers Java primitives, boxed wrappers, `String`,
`BigInteger`, `BigDecimal`, and `byte[]`. Many common Java domain types are not on that list
but have a well-defined, unambiguous **canonical string representation** that fits naturally
into Cardano **text** values:

| Java type | Canonical string form | Parse-back method |
|---|---|---|
| `java.net.URI` | `toString()` → e.g. `"https://example.com/path"` | `URI.create(String)` |
| `java.net.URL` | `toString()` → e.g. `"https://example.com/path"` | `new URL(String)` |
| `java.util.UUID` | `toString()` → e.g. `"550e8400-e29b-41d4-a716-446655440000"` | `UUID.fromString(String)` |
| `java.util.Currency` | `getCurrencyCode()` → ISO 4217, e.g. `"USD"` | `Currency.getInstance(String)` |
| `java.util.Locale` | `toLanguageTag()` → BCP 47, e.g. `"en-US"` | `Locale.forLanguageTag(String)` |

Without explicit support these fields receive a compile-time WARNING and are silently
skipped, forcing users to expose a `String` adapter field instead.

## Decision

Add all five types to `isSupportedScalarType()` as text-backed scalars. Each type is
stored as Cardano **text** using its canonical string form; the canonical string length is
always well below 64 bytes so no chunking is needed.

### On-chain representation

All five map to Cardano **text**. The serialization is one `map.put(key, stringForm)` call
with no branching.

### Serialization (toMetadataMap)

| Type | Generated expression |
|---|---|
| `URI` | `map.put(key, v.toString())` |
| `URL` | `map.put(key, v.toString())` |
| `UUID` | `map.put(key, v.toString())` |
| `Currency` | `map.put(key, v.getCurrencyCode())` |
| `Locale` | `map.put(key, v.toLanguageTag())` |

### Deserialization (fromMetadataMap)

All five require the on-chain value to be `instanceof String`. Deserialization errors
(malformed value) throw a runtime exception per ADR 0004 policy.

| Type | Generated expression | Error type |
|---|---|---|
| `URI` | `URI.create((String) v)` | `IllegalArgumentException` (unchecked) |
| `URL` | `new URL((String) v)` | wrapped in try-catch → `IllegalArgumentException` |
| `UUID` | `UUID.fromString((String) v)` | `IllegalArgumentException` (unchecked) |
| `Currency` | `Currency.getInstance((String) v)` | `IllegalArgumentException` (unchecked) |
| `Locale` | `Locale.forLanguageTag((String) v)` | never throws (returns `Locale.ROOT`) |

### URL and the checked MalformedURLException

`new URL(String)` is the only constructor that throws a **checked** `MalformedURLException`.
The generated `fromMetadataMap` method does not declare checked exceptions, so the call is
wrapped in a try-catch that re-throws as `IllegalArgumentException`:

```java
if (v instanceof String) {
    try {
        obj.setWebsite(new URL((String) v));
    } catch (MalformedURLException _e) {
        throw new IllegalArgumentException("Malformed URL: " + v, _e);
    }
}
```

This is consistent with the runtime-exception-on-bad-data policy established in ADR 0004.

### enc= override

`enc=STRING` on any of these types is accepted (they are already text) and is a no-op —
the generated code is identical to `enc=DEFAULT`. `enc=STRING_HEX` and `enc=STRING_BASE64`
produce compile errors (only valid for `byte[]`, enforced by `isValidEnc()`).

### Collection and Optional support

Adding these types to `isSupportedScalarType()` automatically unlocks
`List<URI>`, `Set<UUID>`, `Optional<Currency>`, etc. through the existing collection
and Optional machinery.

**SortedSet exclusion** — `URL`, `Currency`, and `Locale` do not implement `Comparable`
and are excluded from `SortedSet<T>` with a compile-time WARNING. `URI` and `UUID` both
implement `Comparable` and are permitted in `SortedSet<T>`.

### Example

```java
@MetadataType
public class Transfer {
    private URI          callbackUri;   // → text "https://callback.example.com"
    private URL          documentUrl;   // → text "https://docs.example.com/v1.pdf"
    private UUID         correlationId; // → text "550e8400-e29b-41d4-a716-446655440000"
    private Currency     feeCurrency;   // → text "ADA"
    private Locale       userLocale;    // → text "en-US"
}
```

## Alternatives considered

### 1. Treat URI/URL/UUID/Currency/Locale as String fields at the user level (rejected)

Users can always add a `String` field with a custom getter/setter that converts. This is
verbose, requires extra fields in the domain model, and pushes conversion boilerplate into
application code. Native support is a strict improvement.

### 2. Support only URI and UUID (rejected)

URI and UUID are the most common of the five, but Currency and Locale are equally
well-defined and the implementation cost is identical. Partial support creates an
inconsistent API surface.

### 3. Require `enc=STRING` explicitly for these types (rejected)

These types have only one sensible on-chain representation: their canonical string. Making
`enc=DEFAULT` produce the same result as `enc=STRING` makes the common case frictionless.

## Trade-offs

### Positive
- Domain types appear directly in `@MetadataType` classes with no adapter boilerplate.
- On-chain format is human-readable in block explorers (currency codes, language tags, UUIDs).
- Zero new runtime dependencies.
- Consistent treatment: all five types serialized/deserialized via their canonical string API.

### Negative / Limitations
- **URL carries a checked exception**: the generated try-catch adds a few lines of noise to
  the generated converter. This is unavoidable without a wrapper utility class.
- **`Locale.forLanguageTag` never throws**: an invalid BCP 47 tag silently returns
  `Locale.ROOT` instead of signalling an error. This is a JDK behaviour that the generated
  code inherits.
- **SortedSet<URL> / SortedSet<Currency> / SortedSet<Locale>** are rejected with a WARNING;
  users expecting them to work by analogy with `SortedSet<URI>` may be surprised.

## Consequences

- `MetadataAnnotationProcessor.isSupportedScalarType()` now includes five new `case` labels.
- `MetadataAnnotationProcessor.isSupportedType()` excludes `URL`, `Currency`, and `Locale`
  from the `SortedSet<T>` guard.
- `MetadataConverterGenerator` gains new `case` branches in `emitToMapPutDefault()`,
  `emitFromMapGetDefault()`, `emitListElementAdd()`, `emitListElementRead()`, and
  `emitFromMapGetOptional()`, plus the `emitFromMapGetUrl()` private helper.
- `elementTypeName()` maps the five fully-qualified names to their JavaPoet `TypeName`.

## Related

- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0004: @MetadataField(enc=…) Type Override Mechanism
- ADR metadata/0005: List\<T\> Field Support
- ADR metadata/0008: Optional\<T\> Field Support
- `MetadataConverterGeneratorTest$UriFields` / `UuidFields` / `UrlFields` / `CurrencyFields` / `LocaleFields` — unit tests
