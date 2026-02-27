# ADR metadata/0002: Java-to-Cardano Metadata Type Mapping

- **Status**: Accepted
- **Date**: 2026-02-19
- **Deciders**: Cardano Client Lib maintainers

## Context

Cardano transaction metadata (CIP-10) is encoded as CBOR and supports only **five native types**:

| Cardano type | Description |
|---|---|
| **integer** | Arbitrary-precision signed integer |
| **bytes** | Raw byte string (≤ 64 bytes per chunk) |
| **text** | UTF-8 string (≤ 64 bytes per chunk) |
| **list** | Ordered array |
| **map** | Key-value map |

There is no native float, decimal, boolean, or character type. Every Java field type supported
by the processor must be mapped to one of these five Cardano types, with a clear round-trip
(serialize → deserialize returns an equal value).

## Decision

The following canonical mapping is used for `DEFAULT` serialization (see ADR 0004 for
`@MetadataField(enc=…)` overrides):

### Supported type mapping

| Java type(s) | Cardano type | Serialization | Deserialization |
|---|---|---|---|
| `String` | **text** | direct (with 64-byte chunking — see ADR 0003) | instanceof String / MetadataList |
| `BigInteger` | **integer** | direct | instanceof BigInteger |
| `long` / `Long` | **integer** | `BigInteger.valueOf(v)` | `((BigInteger) v).longValue()` |
| `int` / `Integer` | **integer** | `BigInteger.valueOf((long) v)` | `((BigInteger) v).intValue()` |
| `short` / `Short` | **integer** | `BigInteger.valueOf((long) v)` | `((BigInteger) v).shortValue()` |
| `byte` / `Byte` | **integer** | `BigInteger.valueOf((long) v)` | `((BigInteger) v).byteValue()` |
| `boolean` / `Boolean` | **integer** | `BigInteger.ONE` (true) / `BigInteger.ZERO` (false) | `BigInteger.ONE.equals(v)` |
| `double` / `Double` | **text** | `String.valueOf(v)` | `Double.parseDouble((String) v)` |
| `float` / `Float` | **text** | `String.valueOf(v)` | `Float.parseFloat((String) v)` |
| `char` / `Character` | **text** | `String.valueOf(v)` (single char) | `((String) v).charAt(0)` |
| `BigDecimal` | **text** | `v.toPlainString()` | `new BigDecimal((String) v)` |
| `byte[]` | **bytes** | direct | instanceof byte[] |

### Null handling

- **Primitive types** (`int`, `long`, `short`, `byte`, `boolean`, `double`, `float`, `char`):
  no null check — the JVM guarantees a value.
- **Reference types** (all boxed wrappers, `String`, `BigInteger`, `BigDecimal`, `byte[]`):
  wrapped in `if (v != null)` on serialization; field is absent from the map when null.
  On deserialization, absent key → field retains its zero/null default.

## Key design decisions within the mapping

### 1. `byte` (singular) maps to integer, not bytes

`byte` in Java is a **numeric** type (8-bit signed integer, range −128..127). It belongs to
the integer family alongside `short`, `int`, `long`. Mapping it to Cardano **integer** is
consistent with that family.

Mapping `byte` to a one-element Cardano **bytes** value was considered but rejected:
- `byte[]` is the idiomatic Java type for binary data, not bare `byte`.
- A bare `byte` field in a POJO (e.g., `byte version = 1`) is a number, not raw binary.

### 2. `boolean` defaults to integer 0/1, not "true"/"false"

Integer is more compact (one byte in CBOR vs four/five bytes for the string), more
interoperable (widely used in Cardano community schemas), and avoids text-parsing ambiguity.
The string representation ("true"/"false") is available via `@MetadataField(enc=STRING)`.

### 3. `BigDecimal` uses `toPlainString()`, not `toString()`

`BigDecimal.toString()` can produce scientific notation (`"1E+10"`), which is:
- Harder to read in blockchain explorers.
- Less predictable (the same value can have different string representations depending on scale).

`toPlainString()` always produces a canonical decimal form (`"10000000000"`).
`new BigDecimal(str)` correctly parses both plain and scientific notation on read-back,
so round-trips work even for values that were written before this convention.

### 4. `double`/`float`/`char` stored as text

Cardano metadata has no native floating-point or character type. Text is the only reasonable
on-chain representation. Consumers inspecting raw metadata will see strings; this is unavoidable
given Cardano's type system.

### 5. On-chain integer precision is always arbitrary

All integer-family types (`byte`, `short`, `int`, `long`, `BigInteger`) are stored as
Cardano arbitrary-precision integers. The on-chain value `42` carries no information about
whether it originated from a `byte`, `short`, `int`, or `long`. The Java class is the schema.

## Consequences

### Positive
- Clear, predictable mapping for all common Java scalar types.
- No runtime type ambiguity for integer-family types.
- `toPlainString()` for `BigDecimal` avoids scientific-notation surprises in explorers.
- Null safety at both serialize and deserialize boundaries.

### Neutral
- Floating-point values appear as strings on-chain; external tools that inspect raw metadata
  cannot distinguish them from arbitrary text without knowing the schema.
- `byte` fields look like small integers on-chain, which is correct but potentially surprising
  to developers expecting binary output.

### Negative
- No direct on-chain type information — schema fidelity depends entirely on the Java class.

## Related

- ADR metadata/0001: Annotation Processor Core Design
- ADR metadata/0003: 64-Byte String Chunking Ownership
- ADR metadata/0004: @MetadataField(enc=…) Type Override Mechanism
- CIP-10: Transaction Metadata
