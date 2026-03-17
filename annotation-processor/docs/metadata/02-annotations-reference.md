# Annotations Reference

This document covers every annotation in the metadata processor and their attributes.

## @MetadataType

Marks a class or record for metadata converter generation. Applied at the type level.

```java
@MetadataType
public record TokenInfo(String policyId, String assetName, int decimals) {}
```

### Attributes

| Attribute | Type   | Default | Description |
|-----------|--------|---------|-------------|
| `label`   | `long` | `-1`    | Transaction metadata label (top-level key). When >= 0, the generated converter also implements `LabeledMetadataConverter<T>`. |

### With Label

When a label is specified, the converter gains `toMetadata`/`fromMetadata` methods that wrap the metadata map under that label:

```java
@MetadataType(label = 721)
public class Cip25NftMetadata {
    // ...
}
```

The generated `Cip25NftMetadataMetadataConverter` implements both `MetadataConverter<Cip25NftMetadata>` and `LabeledMetadataConverter<Cip25NftMetadata>`.

### Without Label

When no label is specified (or `label = -1`), only `MetadataConverter<T>` is implemented. You get `toMetadataMap`/`fromMetadataMap` and must place the map under a label yourself.

## @MetadataField

Customizes how a field is serialized to/from Cardano metadata. Applied at the field level.

```java
@MetadataField(key = "media_type", defaultValue = "image/png")
private String mediaType;
```

### Attributes

| Attribute      | Type                    | Default              | Description |
|----------------|-------------------------|----------------------|-------------|
| `key`          | `String`                | `""` (field name)    | Override the metadata map key. When empty, the Java field name is used. |
| `enc`          | `MetadataFieldType`     | `DEFAULT`            | Override the on-chain encoding. See [MetadataFieldType](#metadatafieldtype-enum) below. |
| `required`     | `boolean`               | `false`              | When `true`, deserialization throws `IllegalArgumentException` if this key is missing. Mutually exclusive with `defaultValue`. |
| `defaultValue` | `String`                | `""` (none)          | Fallback value (as a string) when this key is absent during deserialization. Only supported on scalar and enum fields. Mutually exclusive with `required`. |
| `adapter`      | `Class<? extends MetadataTypeAdapter<?>>` | `NoAdapter.class` | Custom adapter class for serialization/deserialization. When specified, `enc` is ignored. See [Custom Type Adapters](06-advanced-topics.md#custom-type-adapters). |

### Constraints

- `required` and `defaultValue` are mutually exclusive — the processor reports a compile-time error if both are set.
- `adapter` and `defaultValue` are mutually exclusive — the processor reports a compile-time error if both are set.
- `required = true` on `Optional` fields emits a compile-time warning (contradicts `Optional` semantics).
- `defaultValue` only works on scalar and enum fields — not on collections, maps, `Optional`, `byte[]`, or nested `@MetadataType` fields.
- `enc` on collection, map, `Optional`, or nested fields is silently reset to `DEFAULT` with a warning — except `STRING_HEX`/`STRING_BASE64` on `List<byte[]>` / `Set<byte[]>` which are supported.
- When `adapter` is specified, the `enc` attribute is ignored.

### Examples

```java
// Override the map key
@MetadataField(key = "pool_id")
private String poolId;

// Mark as required during deserialization
@MetadataField(required = true)
private String name;

// Provide a default value when absent
@MetadataField(key = "media_type", defaultValue = "image/png")
private String mediaType;

// Encode byte[] as hex string
@MetadataField(key = "policy_id", enc = MetadataFieldType.STRING_HEX)
private byte[] policyId;

// Use a custom adapter
@MetadataField(adapter = EpochAdapter.class)
private Instant mintedAt;
```

## @MetadataIgnore

Excludes a field from metadata serialization and deserialization. The field will not appear in the generated converter code.

```java
@MetadataType(label = 721)
public class Cip25NftMetadata extends NftBaseMetadata {
    private String name;
    private String image;

    @MetadataIgnore
    private String internalTrackingId;  // Not serialized to metadata
}
```

## @MetadataDiscriminator and @MetadataSubtype

Enable polymorphic serialization for sealed interfaces or abstract classes. When a field's declared type carries `@MetadataDiscriminator`, the generated converter uses a discriminator key to determine which concrete subtype to serialize/deserialize.

### @MetadataDiscriminator

Applied to the interface or abstract class that serves as the polymorphic base.

| Attribute  | Type                | Description |
|------------|---------------------|-------------|
| `key`      | `String`            | The metadata map key used as the discriminator (e.g., `"type"`). |
| `subtypes` | `MetadataSubtype[]` | The set of concrete subtypes and their discriminator values. |

### @MetadataSubtype

Used inside `@MetadataDiscriminator.subtypes()` to map discriminator values to concrete classes.

| Attribute | Type       | Description |
|-----------|------------|-------------|
| `value`   | `String`   | The discriminator value that identifies this subtype in the metadata map. |
| `type`    | `Class<?>` | The concrete `@MetadataType`-annotated class for this subtype. |

### Example

```java
// The polymorphic interface
@MetadataDiscriminator(key = "type", subtypes = {
        @MetadataSubtype(value = "image", type = ImageContent.class),
        @MetadataSubtype(value = "audio", type = AudioContent.class)
})
public interface MediaContent {}

// Concrete subtype — must have @MetadataType
@Data @NoArgsConstructor
@MetadataType
public class ImageContent implements MediaContent {
    private String url;
    private int width;
    private int height;
}

@Data @NoArgsConstructor
@MetadataType
public class AudioContent implements MediaContent {
    private String url;
    private int duration;
    private String codec;
}
```

The annotated type itself does **not** need `@MetadataType` — each concrete subtype listed in `subtypes` must have it instead. When serialized, the discriminator key (`"type"`) is added to the metadata map with the matching value (`"image"` or `"audio"`). During deserialization, the value of the discriminator key determines which converter is used.

## MetadataFieldType Enum

Controls how a field value is encoded in Cardano metadata. Used as the `enc` attribute of `@MetadataField`.

| Value          | Description | Valid Java Types |
|----------------|-------------|------------------|
| `DEFAULT`      | Natural Cardano type mapping. Integers stay as integers, strings as strings, `byte[]` as bytes. | All types |
| `STRING`       | Store as a UTF-8 string. Numeric types are converted via `String.valueOf()`. Parsed back on read. | `String`, `int`/`Integer`, `long`/`Long`, `BigInteger` |
| `STRING_HEX`   | Encode `byte[]` as a lowercase hex string (e.g., `"deadbeef"`). | `byte[]` only |
| `STRING_BASE64` | Encode `byte[]` as a Base64 string. | `byte[]` only |

### Valid Combinations

| Java Type       | `DEFAULT` | `STRING` | `STRING_HEX` | `STRING_BASE64` |
|-----------------|-----------|----------|---------------|-----------------|
| `String`        | Yes       | Yes (no-op) | No         | No              |
| `int` / `Integer` | Yes     | Yes      | No            | No              |
| `long` / `Long` | Yes       | Yes      | No            | No              |
| `BigInteger`    | Yes       | Yes      | No            | No              |
| `byte[]`        | Yes       | No       | Yes           | Yes             |

Invalid combinations are reported as compile-time errors.

## Next Steps

- [Understanding Generated Converters](03-generated-converters.md) — how to use the generated converter classes
- [Supported Types](04-supported-types.md) — complete list of supported Java types
- [Class Support and Patterns](05-class-support.md) — records, POJOs, Lombok, and inheritance
- [Advanced Topics](06-advanced-topics.md) — custom adapters, polymorphic types, and real-world examples
