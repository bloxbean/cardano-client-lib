# Class Support and Patterns

The metadata processor supports Java records, POJOs (with or without Lombok), and class inheritance. This document covers each pattern and its requirements.

## Java Records

Records are the simplest way to define metadata types. All record components are automatically included in the generated converter.

```java
@MetadataType(label = 1000)
public record DexLiquidityPool(
        @MetadataField(key = "pool_id", required = true) String poolId,
        @MetadataField(key = "token_a", required = true) TokenInfo tokenA,
        @MetadataField(key = "token_b", required = true) TokenInfo tokenB,
        @MetadataField(key = "reserve_a") BigInteger reserveA,
        @MetadataField(key = "reserve_b") BigInteger reserveB,
        @MetadataField(key = "total_lp") BigInteger totalLpTokens,
        PoolFeeConfig fees,
        @MetadataEncoder(EpochAdapter.class) @MetadataDecoder(EpochAdapter.class) Instant updatedAt,
        @MetadataField(key = "pool_datum", enc = MetadataFieldType.STRING_BASE64) byte[] poolDatum
) {}
```

Records with no custom constructor work out of the box. The processor uses the canonical constructor for deserialization.

### Nested Records

Records can reference other `@MetadataType` records:

```java
@MetadataType
public record TokenInfo(
        @MetadataField(key = "policy_id", required = true) String policyId,
        @MetadataField(key = "asset_name", required = true) String assetName,
        int decimals
) {}

@MetadataType
public record NftFileDetail(
        @MetadataField(required = true) String name,
        String src,
        @MetadataField(key = "media_type") String mediaType
) {}
```

## POJOs with Lombok

Use Lombok's `@Data` and `@NoArgsConstructor` for concise POJOs:

```java
@Data
@NoArgsConstructor
@MetadataType(label = 721)
public class Cip25NftMetadata extends NftBaseMetadata {
    @MetadataField(required = true)
    private String name;

    private String image;

    @MetadataField(key = "media_type", defaultValue = "image/png")
    private String mediaType;

    private Optional<String> description;

    private List<NftFileDetail> files;

    @MetadataField(key = "policy_id", enc = MetadataFieldType.STRING_HEX)
    private byte[] policyId;

    private NftRarity rarity;

    @MetadataEncoder(EpochAdapter.class)
    @MetadataDecoder(EpochAdapter.class)
    private Instant mintedAt;

    private Map<String, String> attributes;

    @MetadataIgnore
    private String internalTrackingId;
}
```

### How Lombok Detection Works

The processor detects Lombok at compile time by checking for these annotations on the class:

- **`@Data`** — detected as Lombok-enabled (generates getters, setters, and other boilerplate)
- **`@Getter` + `@Setter`** (both present) — detected as Lombok-enabled

When Lombok is detected, the processor trusts that `getFieldName()` and `setFieldName()` methods will exist at compile time, even though they're not visible in the source AST during annotation processing.

**Important:** The processor does **not** generate the no-arg constructor for you. Use `@NoArgsConstructor` (or `@Data` combined with `@NoArgsConstructor`) to ensure the generated converter can instantiate your class. Without it, the processor reports a compile-time error.

### Supported Lombok Patterns

```java
// Recommended: @Data + @NoArgsConstructor
@Data
@NoArgsConstructor
@MetadataType
public class MyMetadata { ... }

// Also works: @Getter + @Setter + @NoArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@MetadataType
public class MyMetadata { ... }

// With inheritance: add @EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MetadataType(label = 1902)
public class StakeDelegationBatch extends NftBaseMetadata { ... }
```

## POJOs with Manual Getters/Setters

Standard POJOs work as long as they have a no-arg constructor and follow JavaBeans naming conventions:

```java
@MetadataType
public class PoolFeeConfig {
    private BigInteger swapFee;
    private BigInteger protocolFee;

    public PoolFeeConfig() {}

    public BigInteger getSwapFee() { return swapFee; }
    public void setSwapFee(BigInteger swapFee) { this.swapFee = swapFee; }

    public BigInteger getProtocolFee() { return protocolFee; }
    public void setProtocolFee(BigInteger protocolFee) { this.protocolFee = protocolFee; }
}
```

## Inheritance

The processor includes fields from superclasses. Annotate the subclass with `@MetadataType`; the superclass does not need the annotation.

```java
// Base class — no @MetadataType needed
public class NftBaseMetadata {
    private String version;
    private String author;

    // getters and setters
}

// Subclass — annotated with @MetadataType
@MetadataType(label = 721)
public class Cip25NftMetadata extends NftBaseMetadata {
    @MetadataField(required = true)
    private String name;
    private String image;
    // ...
}
```

The generated `Cip25NftMetadataMetadataConverter` serializes and deserializes both `version` and `author` (from the base class) along with all fields declared in `Cip25NftMetadata`.

## Constructor Validation

The processor requires a public no-arg constructor for POJOs and Lombok classes (records use their canonical constructor). If a class has no public no-arg constructor, the processor reports a **compile-time error**:

```
error: No public no-arg constructor found on 'MyClass'. The generated fromMetadataMap() calls
new MyClass(). Add a public no-arg constructor or use @lombok.NoArgsConstructor.
```

This applies to both the annotated class and any nested `@MetadataType` classes it references. To fix it, add a public no-arg constructor explicitly or use `@NoArgsConstructor` with Lombok.

## Required Fields

Fields annotated with `@MetadataField(required = true)` are validated during deserialization. If the key is missing from the metadata map, an `IllegalArgumentException` is thrown:

```java
@MetadataField(required = true)
private String name;
```

Required validation only affects `fromMetadataMap` — serialization is unchanged.

## Default Values

Fields annotated with `@MetadataField(defaultValue = "...")` use the specified value when the key is absent from the metadata map during deserialization:

```java
@MetadataField(key = "media_type", defaultValue = "image/png")
private String mediaType;
```

If the metadata map does not contain `media_type`, the field is set to `"image/png"`. Default values are only supported on scalar and enum fields.

## Null Handling

- **Serialization**: `null` field values are omitted from the metadata map. They do not produce entries with null values.
- **Deserialization**: Missing keys in the metadata map result in `null` for non-required fields (or `Optional.empty()` for `Optional` fields, or the default value if `defaultValue` is specified).
- **`@MetadataIgnore`** fields are always `null` after deserialization, regardless of what was in the original object.

## Next Steps

- [Advanced Topics](06-advanced-topics.md) — custom adapters, polymorphic types, and real-world examples
- [Supported Types](04-supported-types.md) — complete list of supported Java types
- [Annotations Reference](02-annotations-reference.md) — full attribute reference for all annotations
