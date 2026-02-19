# ADR metadata/0004: @MetadataField(as=…) Type Override Mechanism

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

The default type mapping (ADR 0002) provides a single canonical on-chain representation
for each Java type. However, legitimate use cases exist where a different representation
is preferable:

- An `int` field used as a status code may be more readable in a block explorer as the
  string `"200"` than as the integer `200`.
- A `byte[]` payload may need to be stored as a hex string (`"deadbeef"`) so it is
  human-readable on-chain, or as Base64 for compact transport.
- A `boolean` field may need to be `"true"`/`"false"` for interoperability with metadata
  produced by other tools.

The mechanism must be type-safe, compile-time validated, and minimise API surface.

## Decision

Add a `MetadataFieldType as()` attribute to `@MetadataField` backed by an enum with
four values. The generator inspects `as` alongside the Java type and emits different
serialization/deserialization code accordingly.

```java
public @interface MetadataField {
    String key() default "";
    MetadataFieldType as() default MetadataFieldType.DEFAULT;
}
```

```java
public enum MetadataFieldType {
    DEFAULT,       // natural mapping per ADR 0002
    STRING,        // force to Cardano text
    STRING_HEX,    // byte[] → hex string (HexUtil)
    STRING_BASE64  // byte[] → Base64 string (java.util.Base64)
}
```

### Semantics per value

#### DEFAULT
Use the canonical mapping from ADR 0002. No annotation needed for this case.

#### STRING
Force the field to Cardano **text**, regardless of the natural mapping.

| Java type | toMetadataMap | fromMetadataMap |
|---|---|---|
| `int`/`Integer`, `long`/`Long` | `String.valueOf(v)` | `Integer.parseInt` / `Long.parseLong` |
| `short`/`Short`, `byte`/`Byte` | `String.valueOf(v)` | `Short.parseShort` / `Byte.parseByte` |
| `boolean`/`Boolean` | `String.valueOf(v)` → `"true"`/`"false"` | `Boolean.parseBoolean` |
| `BigInteger` | `v.toString()` | `new BigInteger((String) v)` |
| `BigDecimal` | same as DEFAULT (already text) | same as DEFAULT |
| `double`/`float`/`char` | same as DEFAULT (already text) | same as DEFAULT |
| `String` | same as DEFAULT (64-byte chunking still applies) | same as DEFAULT |

Deserialization failures (e.g., `"abc"` parsed as int) throw a runtime exception.
This is acceptable: malformed on-chain data is a data integrity issue, not a code bug.

#### STRING_HEX
Encode `byte[]` as a lowercase hex string using `HexUtil.encodeHexString`;
decode using `HexUtil.decodeHexString`.

Only valid on `byte[]` fields.

#### STRING_BASE64
Encode `byte[]` using `Base64.getEncoder().encodeToString`;
decode using `Base64.getDecoder().decode`.

Only valid on `byte[]` fields.

### Compile-time validation

The processor enforces valid combinations at compile time:

| Combination | Result |
|---|---|
| `STRING` on `byte[]` | **Compile error**: "ambiguous — use STRING_HEX or STRING_BASE64" |
| `STRING_HEX` on non-`byte[]` | **Compile error**: "only valid for byte[] fields" |
| `STRING_BASE64` on non-`byte[]` | **Compile error**: "only valid for byte[] fields" |
| `STRING` on `double`/`float`/`char` | Accepted (no-op; already text) |

### Example usage

```java
@MetadataType
public class Transfer {
    private String recipient;                          // DEFAULT — text

    @MetadataField(key = "ref")
    private int referenceId;                           // DEFAULT — integer

    @MetadataField(as = MetadataFieldType.STRING)
    private int statusCode;                            // → text "200"

    @MetadataField(key = "payload", as = MetadataFieldType.STRING_HEX)
    private byte[] payloadBytes;                       // → hex text

    @MetadataField(key = "sig", as = MetadataFieldType.STRING_BASE64)
    private byte[] signatureBytes;                     // → Base64 text

    @MetadataField(key = "active", as = MetadataFieldType.STRING)
    private boolean enabled;                           // → "true"/"false"
}
```

## Alternatives considered

### 1. Separate `@MetadataEncoding` annotation (rejected)

A dedicated annotation (`@MetadataEncoding(HEX)`) alongside `@MetadataField`.

**Why rejected:**
- Two annotations per field for a common use case is verbose.
- Combining key renaming and encoding override into one annotation is more ergonomic.

### 2. String `algorithm` attribute on `@MetadataField` (rejected)

```java
@MetadataField(key = "payload", algorithm = "hex")
```

**Why rejected:**
- Stringly typed — typos are not caught at compile time.
- No IDE auto-completion for valid values.
- Requires string-matching logic in the processor.

### 3. Separate annotations per encoding (rejected)

```java
@MetadataFieldHex  // for hex encoding
@MetadataFieldBase64  // for Base64 encoding
```

**Why rejected:**
- Combinatorial explosion as more representations are added.
- Does not compose with `key` renaming without further annotation stacking.

## Why `STRING` on `byte[]` is a compile error

Both hex and Base64 are valid text encodings of a byte array. There is no universally
"obvious" default — hex is common for hashes and keys, Base64 for opaque blobs. Silently
picking one would hide intent and produce data that other tools might misinterpret.

Forcing the developer to choose (`STRING_HEX` or `STRING_BASE64`) makes the on-chain
encoding unambiguous both in code and in the stored metadata.

## Consequences

### Positive
- Type-safe: `MetadataFieldType` values are checked by the compiler; no stringly-typed mistakes.
- Invalid combinations (`STRING` on `byte[]`, `STRING_HEX` on `int`) produce compile errors,
  not silent data corruption.
- Single annotation covers key renaming and encoding override.
- Bidirectional: the same `as` value governs both serialization and deserialization, ensuring
  round-trip correctness.

### Neutral
- `as=STRING` on types whose DEFAULT is already text (double, float, char, BigDecimal) is
  accepted but is a no-op. The processor does not warn about this; the generated code is
  identical to DEFAULT.

### Negative
- Runtime exceptions on deserialization when on-chain data is malformed (e.g., a string that
  cannot be parsed as an int). This is a deliberate trade-off: the alternative (silently
  returning a default value) would hide data corruption bugs.

## Related

- ADR metadata/0001: Annotation Processor Core Design
- ADR metadata/0002: Java-to-Cardano Metadata Type Mapping
- ADR metadata/0003: 64-Byte String Chunking Ownership
- `MetadataFieldType` — `metadata/src/main/java/.../annotation/MetadataFieldType.java`
- `MetadataField` — `metadata/src/main/java/.../annotation/MetadataField.java`
- `MetadataAnnotationProcessor.isValidAs()` — compile-time validation
