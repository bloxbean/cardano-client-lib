# Supported Types

This document lists every Java type the metadata processor can serialize and deserialize, along with how each is represented in Cardano transaction metadata.

## Scalar Types

| Java Type      | On-chain Representation | Notes |
|----------------|------------------------|-------|
| `String`       | Metadata string        | Auto-chunked at 64 bytes (see [String Chunking](#string-chunking)) |
| `BigInteger`   | Metadata integer       | Supports negative values |
| `int` / `Integer` | Metadata integer    | Widened to `BigInteger` on chain |
| `long` / `Long`   | Metadata integer    | Widened to `BigInteger` on chain |
| `short` / `Short`  | Metadata integer   | Widened to `BigInteger` on chain |
| `byte` / `Byte`    | Metadata integer   | Widened to `BigInteger` on chain |
| `boolean` / `Boolean` | Metadata integer | `1` for `true`, `0` for `false` |
| `BigDecimal`   | Metadata string        | Stored via `toString()`, parsed back via constructor |
| `Double` / `double` | Metadata string   | Stored via `String.valueOf()` |
| `Float` / `float`   | Metadata string   | Stored via `String.valueOf()` |
| `Character` / `char` | Metadata string  | Single-character string |

All scalar types can be overridden with `@MetadataField(enc = MetadataFieldType.STRING)` to force string encoding for numeric types.

## Binary Data (byte[])

`byte[]` fields support three encoding modes controlled by `@MetadataField(enc = ...)`:

| Encoding       | On-chain Format | Example |
|----------------|----------------|---------|
| `DEFAULT`      | Raw bytes (Cardano metadata bytes) | Native CBOR bytes |
| `STRING_HEX`   | Hex string (e.g., `"aabbccdd"`) | `@MetadataField(enc = MetadataFieldType.STRING_HEX)` |
| `STRING_BASE64` | Base64 string | `@MetadataField(enc = MetadataFieldType.STRING_BASE64)` |

```java
// Stored as raw bytes (default)
private byte[] rawData;

// Stored as hex string
@MetadataField(key = "policy_id", enc = MetadataFieldType.STRING_HEX)
private byte[] policyId;

// Stored as Base64 string
@MetadataField(key = "pool_datum", enc = MetadataFieldType.STRING_BASE64)
private byte[] poolDatum;
```

## Collections

### List

`List<E>` is serialized as a metadata list (array). The element type `E` must be a supported type.

```java
private List<NftFileDetail> files;        // List of nested @MetadataType
private List<String> tags;                // List of strings
private List<BigInteger> amounts;         // List of integers
```

### Set and SortedSet

`Set<E>` and `SortedSet<E>` are also serialized as metadata lists. `Set` supports the same element types as `List`. `SortedSet` requires `Comparable` elements, so `byte[]`, `URL`, `Currency`, and `Locale` are not allowed as element types.

```java
private Set<String> categories;
private SortedSet<Integer> ranks;
```

## Maps

`Map<K, V>` is serialized as a metadata map. Both key and value types must be supported.

### Supported Key Types

| Key Type      | On-chain Key |
|---------------|-------------|
| `String`      | String key  |
| `Integer`     | Integer key |
| `Long`        | Integer key |
| `BigInteger`  | Integer key |
| `byte[]`      | Bytes key   |

### Examples

```java
// String keys, String values
private Map<String, String> attributes;

// String keys, nested type values
private Map<String, TokenInfo> tokens;

// String keys, BigInteger values
private Map<String, BigInteger> providerShares;

// String keys, List values
private Map<String, List<String>> tagGroups;
```

## Optional

`Optional<T>` fields are omitted from the metadata map when empty, and serialized normally when present. During deserialization, a missing key produces `Optional.empty()`.

```java
private Optional<String> description;
```

- `Optional.of("A test NFT")` — serialized as the string `"A test NFT"` under its key
- `Optional.empty()` — key is omitted from the metadata map entirely

## Enums

Enum fields are stored as their `name()` string.

```java
public enum NftRarity {
    COMMON, UNCOMMON, RARE, LEGENDARY
}

// Stored as e.g. "RARE"
private NftRarity rarity;
```

## Nested @MetadataType

Fields whose type is annotated with `@MetadataType` are serialized as sub-`MetadataMap` values. The nested type's converter handles its own serialization.

```java
@MetadataType
public record TokenInfo(
        @MetadataField(key = "policy_id", required = true) String policyId,
        @MetadataField(key = "asset_name", required = true) String assetName,
        int decimals
) {}

@MetadataType(label = 1000)
public record DexLiquidityPool(
        @MetadataField(key = "token_a", required = true) TokenInfo tokenA,
        @MetadataField(key = "token_b", required = true) TokenInfo tokenB,
        // ...
) {}
```

Here `tokenA` is serialized as a nested metadata map containing `policy_id`, `asset_name`, and `decimals`.

## Time Types

| Java Type        | On-chain Representation | Format |
|------------------|------------------------|--------|
| `Instant`        | Metadata string        | ISO-8601 (`2024-01-15T10:30:00Z`) |
| `LocalDate`      | Metadata string        | ISO-8601 (`2024-01-15`) |
| `LocalDateTime`  | Metadata string        | ISO-8601 (`2024-01-15T10:30:00`) |
| `Date`           | Metadata integer       | Epoch millis via `BigInteger.valueOf(date.getTime())`. With `enc = STRING`: ISO-8601 via `toInstant().toString()` |

To use a custom representation (e.g., epoch seconds), use a [custom type adapter](06-advanced-topics.md#custom-type-adapters):

```java
@MetadataField(adapter = EpochAdapter.class)
private Instant mintedAt;
```

## String-Coercible Types

These types are stored as their string representation and parsed back during deserialization:

| Java Type  | Serialized As | Parsing Method |
|------------|--------------|----------------|
| `URI`      | String       | `URI.create()` |
| `URL`      | String       | `new URL()`    |
| `UUID`     | String       | `UUID.fromString()` |
| `Currency` | String       | `Currency.getInstance()` |
| `Locale`   | String       | `Locale.forLanguageTag()` |

## String Chunking

Cardano transaction metadata limits individual string values to 64 bytes. The processor automatically chunks longer strings into metadata lists of 64-byte segments during serialization, and reassembles them during deserialization. This is transparent to user code — you work with normal `String` values.

For example, a 150-byte IPFS URL:
```
ipfs://QmXyZ123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789end
```

is split into two chunks on chain and reassembled into the original string on read-back.

## Next Steps

- [Class Support and Patterns](05-class-support.md) — records, POJOs, Lombok, and inheritance
- [Advanced Topics](06-advanced-topics.md) — custom adapters, polymorphic types, and real-world examples
- [Annotations Reference](02-annotations-reference.md) — full details on `@MetadataField` encoding options
