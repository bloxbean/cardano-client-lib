# Advanced Topics

This document covers advanced features of the metadata processor: custom type adapters, polymorphic types, complex map structures, and a complete real-world example.

## Custom Type Adapters

When the built-in type mappings are not sufficient, implement `MetadataTypeAdapter<T>` to control serialization and deserialization entirely.

The interface is in `com.bloxbean.cardano.client.metadata.annotation`:

```java
import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;
```

```java
public interface MetadataTypeAdapter<T> {
    Object toMetadata(T value);
    T fromMetadata(Object metadata);
}
```

- **`toMetadata`** must return a metadata-compatible value: `String`, `BigInteger`, `byte[]`, `MetadataMap`, or `MetadataList`.
- **`fromMetadata`** receives the raw metadata value and converts it back.

### Example: EpochAdapter

Store `Instant` as epoch seconds (a `BigInteger`) instead of an ISO-8601 string:

```java
public class EpochAdapter implements MetadataTypeAdapter<Instant> {
    @Override
    public Object toMetadata(Instant value) {
        return BigInteger.valueOf(value.getEpochSecond());
    }

    @Override
    public Instant fromMetadata(Object metadata) {
        return Instant.ofEpochSecond(((BigInteger) metadata).longValue());
    }
}
```

Apply it with `@MetadataField(adapter = ...)`:

```java
@MetadataField(adapter = EpochAdapter.class)
private Instant mintedAt;
```

The adapter class must have a public no-arg constructor. When an adapter is specified, the `enc` attribute of `@MetadataField` is ignored.

### When to Use Adapters

- Types not natively supported by the processor
- Custom on-chain representations (e.g., epoch seconds instead of ISO strings)
- Complex transformations that go beyond simple type mapping

### Example: CompactUuidAdapter

Store `UUID` as a compact hex string (no dashes) for minimal on-chain footprint:

```java
public class CompactUuidAdapter implements MetadataTypeAdapter<UUID> {
    @Override
    public Object toMetadata(UUID value) {
        return value.toString().replace("-", "");
    }

    @Override
    public UUID fromMetadata(Object metadata) {
        String hex = (String) metadata;
        return UUID.fromString(
            hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" +
            hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" +
            hex.substring(20));
    }
}
```

### Constraints

- The adapter class must have a **public no-arg constructor** (unless a custom `MetadataAdapterResolver` is used — see below).
- `adapter` and `defaultValue` are **mutually exclusive** — the processor reports a compile-time error if both are set.
- When `adapter` is specified, the `enc` attribute is ignored.

## Separate Encoder and Decoder

When you need different classes for serialization and deserialization — or only want to customize one direction — use `@MetadataEncoder` and `@MetadataDecoder` instead of `@MetadataField(adapter = ...)`.

Each annotation takes a class that implements `MetadataTypeAdapter<T>`. Only the relevant method is called:
- `@MetadataEncoder` → calls `toMetadata()`
- `@MetadataDecoder` → calls `fromMetadata()`

### Encoder-only

The field is encoded with custom logic but decoded using built-in type handling:

```java
@MetadataEncoder(SlotToEpochEncoder.class)
@MetadataField(key = "epoch")
private long slot;  // Stored as epoch number on-chain, but POJO holds slot number
```

During deserialization, the built-in `long` handler reads the value as-is.

### Decoder-only

```java
@MetadataDecoder(EpochToSlotDecoder.class)
@MetadataField(key = "slot")
private long slot;  // Serialized as plain long, but custom decoding on read
```

### Both encoder and decoder

Use different classes for each direction:

```java
@MetadataEncoder(SlotToEpochEncoder.class)
@MetadataDecoder(EpochToSlotDecoder.class)
@MetadataField(key = "epoch")
private long slot;  // Encodes slot→epoch, decodes epoch→slot
```

### Constraints

- `@MetadataEncoder`/`@MetadataDecoder` are **mutually exclusive** with `@MetadataField(adapter = ...)`.
- The encoder/decoder class must implement `MetadataTypeAdapter<T>`.
- When an encoder is present, the `enc` attribute of `@MetadataField` is ignored for serialization.

## Adapter Resolver (Dependency Injection)

By default, the generated converter instantiates adapter, encoder, and decoder classes using their **public no-arg constructor**. When your adapter needs constructor arguments (e.g., configuration, services, or stateful dependencies), use a `MetadataAdapterResolver`.

### How it works

When any adapter, encoder, or decoder is present, the generated converter has **two constructors**:

```java
// No-arg: uses DefaultAdapterResolver (calls new AdapterClass())
var converter = new OrderMetadataConverter();

// Resolver: adapter instances are obtained from the resolver
var converter = new OrderMetadataConverter(myResolver);
```

### Custom resolver

```java
MetadataAdapterResolver resolver = new MetadataAdapterResolver() {
    @Override
    public <T> T resolve(Class<T> adapterClass) {
        if (adapterClass == SlotToEpochEncoder.class) {
            return (T) new SlotToEpochEncoder(cardanoConverters);
        }
        // Fallback to no-arg constructor
        return adapterClass.getDeclaredConstructor().newInstance();
    }
};

var converter = new OrderMetadataConverter(resolver);
```

### Spring Boot integration

```java
@Configuration
public class MetadataConfig {
    @Bean
    MetadataAdapterResolver adapterResolver(ApplicationContext ctx) {
        return new MetadataAdapterResolver() {
            @Override
            public <T> T resolve(Class<T> adapterClass) {
                return ctx.getBean(adapterClass);
            }
        };
    }
}

// Usage in a service:
@Service
public class OrderService {
    private final OrderMetadataConverter converter;

    public OrderService(MetadataAdapterResolver resolver) {
        this.converter = new OrderMetadataConverter(resolver);
    }
}
```

### Real-world example: slot-to-epoch conversion

Using the [cf-cardano-conversions-java](https://github.com/cardano-foundation/cf-cardano-conversions-java) library:

```java
public class SlotToEpochEncoder implements MetadataTypeAdapter<Long> {
    private final CardanoConverters converters;

    // No no-arg constructor — requires resolver
    public SlotToEpochEncoder(CardanoConverters converters) {
        this.converters = converters;
    }

    @Override
    public Object toMetadata(Long slot) {
        return BigInteger.valueOf(converters.slot().slotToEpoch(slot));
    }

    @Override
    public Long fromMetadata(Object metadata) {
        throw new UnsupportedOperationException("Use EpochToSlotDecoder");
    }
}
```

```java
// Create resolver with network-specific converters
var converters = ClasspathConversionsFactory.createConverters(NetworkType.MAINNET);
MetadataAdapterResolver resolver = adapterClass -> {
    if (adapterClass == SlotToEpochEncoder.class) {
        return (SlotToEpochEncoder) new SlotToEpochEncoder(converters);
    }
    return adapterClass.getDeclaredConstructor().newInstance();
};

var converter = new BlockInfoMetadataConverter(resolver);
```

## Polymorphic Types

The `@MetadataDiscriminator` annotation enables serialization of interfaces and abstract classes with multiple concrete implementations. A discriminator key in the metadata map determines which subtype to use.

### Defining the Interface

```java
@MetadataDiscriminator(key = "type", subtypes = {
        @MetadataSubtype(value = "image", type = ImageContent.class),
        @MetadataSubtype(value = "audio", type = AudioContent.class)
})
public interface MediaContent {}
```

### Defining the Subtypes

Each subtype must be annotated with `@MetadataType` and implement the interface:

```java
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

### Using Polymorphic Fields

Reference the interface type in your model:

```java
@MetadataType(label = 721)
public class Cip25NftMetadata {
    private String name;
    private MediaContent displayMedia;  // Polymorphic field
}
```

### Serialized Structure

When `displayMedia` is an `ImageContent`, the metadata map looks like:

```
{
  "name": "MyNFT",
  "displayMedia": {
    "type": "image",       // discriminator key
    "url": "ipfs://Qm...",
    "width": 1920,
    "height": 1080
  }
}
```

When `displayMedia` is an `AudioContent`:

```
{
  "name": "MyNFT",
  "displayMedia": {
    "type": "audio",       // discriminator key
    "url": "ipfs://Qm...",
    "duration": 180,
    "codec": "mp3"
  }
}
```

During deserialization, the value of the `"type"` key determines which converter is used: `"image"` dispatches to `ImageContentMetadataConverter`, `"audio"` to `AudioContentMetadataConverter`.

## Nested Map Structures

The processor supports complex nested map types.

### Map with List Values

```java
private Map<String, List<String>> tagGroups;
```

Serialized as a metadata map where each value is a metadata list of strings.

### Map with Nested @MetadataType Values

```java
private Map<String, TokenInfo> tokens;
```

Each value is serialized as a sub-`MetadataMap` using `TokenInfoMetadataConverter`.

### byte[] as Map Keys

`byte[]` can be used as a map key type:

```java
private Map<byte[], String> policyNames;
```

The `byte[]` key is stored as raw bytes in the metadata map.

### byte[] in Collections

`byte[]` elements in `List` or `Set` are serialized using the default encoding (raw bytes) or according to field-level `enc` settings:

```java
private List<byte[]> signatures;
```

## Labels and Transaction Integration

### When to Use Labels

Use `@MetadataType(label = N)` when your metadata type represents a top-level entry in the transaction metadata map. Common Cardano labels include:

| Label | Standard |
|-------|----------|
| 721   | CIP-25 NFT metadata |
| 20    | CIP-20 transaction message |
| 674   | CIP-674 transaction message |

Nested types that are only used as fields within other types typically do not need a label.

### Full Transaction Flow

```java
// 1. Create your metadata object
Cip25NftMetadata nft = new Cip25NftMetadata();
nft.setName("MyNFT#001");
nft.setImage("ipfs://QmXyZ...");
nft.setMediaType("image/png");
nft.setPolicyId(HexUtil.decodeHexString("aabbccdd11223344"));
nft.setRarity(NftRarity.RARE);
nft.setMintedAt(Instant.ofEpochSecond(1700000000L));
nft.setDescription(Optional.of("A rare collectible"));
nft.setAttributes(Map.of("background", "blue", "eyes", "green"));

// 2. Convert to Metadata (wraps under label 721)
var converter = new Cip25NftMetadataMetadataConverter();
Metadata metadata = converter.toMetadata(nft);

// 3. Attach to transaction
Tx tx = new Tx()
        .payToAddress(receiverAddr, Amount.ada(1.5))
        .attachMetadata(metadata)
        .from(senderAddr);

Result<String> result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

// 4. Retrieve and deserialize from chain
var jsonResult = backendService.getMetadataService()
        .getJSONMetadataByTxnHash(result.getValue());

for (MetadataJSONContent entry : jsonResult.getValue()) {
    if ("721".equals(entry.getLabel())) {
        MetadataMap chainMap = JsonNoSchemaToMetadataConverter
                .parseObjectNode((ObjectNode) entry.getJsonMetadata());
        Cip25NftMetadata restored = converter.fromMetadataMap(chainMap);
    }
}
```

## Negative BigInteger Handling

Negative `BigInteger` values are fully supported. They are stored as Cardano metadata negative integers and round-trip correctly:

```java
@MetadataType
public record BalanceChange(
        String account,
        BigInteger amount  // Can be negative
) {}
```

## Complete Real-World Example

This example demonstrates most features together using a CIP-25 NFT metadata model:

### Model Classes

```java
// Base class with inherited fields
public class NftBaseMetadata {
    private String version;
    private String author;
}

// Main model with label, inheritance, and all field types
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MetadataType(label = 721)
public class Cip25NftMetadata extends NftBaseMetadata {
    @MetadataField(required = true)
    private String name;                                      // Required scalar

    private String image;                                     // Auto-chunked string

    @MetadataField(key = "media_type", defaultValue = "image/png")
    private String mediaType;                                 // Default value

    private Optional<String> description;                     // Optional field

    private List<NftFileDetail> files;                        // Nested record list

    private MediaContent displayMedia;                        // Polymorphic field

    @MetadataField(key = "policy_id", enc = MetadataFieldType.STRING_HEX)
    private byte[] policyId;                                  // Hex-encoded bytes

    private NftRarity rarity;                                 // Enum

    @MetadataField(adapter = EpochAdapter.class)
    private Instant mintedAt;                                 // Custom adapter

    private Map<String, String> attributes;                   // Map

    @MetadataIgnore
    private String internalTrackingId;                        // Ignored field
}

// Nested record
@MetadataType
public record NftFileDetail(
        @MetadataField(required = true) String name,
        String src,
        @MetadataField(key = "media_type") String mediaType
) {}

// Polymorphic interface
@MetadataDiscriminator(key = "type", subtypes = {
        @MetadataSubtype(value = "image", type = ImageContent.class),
        @MetadataSubtype(value = "audio", type = AudioContent.class)
})
public interface MediaContent {}

@Data @NoArgsConstructor
@MetadataType
public class ImageContent implements MediaContent {
    private String url;
    private int width;
    private int height;
}

// Enum
public enum NftRarity { COMMON, UNCOMMON, RARE, LEGENDARY }

// Custom adapter
public class EpochAdapter implements MetadataTypeAdapter<Instant> {
    @Override
    public Object toMetadata(Instant value) {
        return BigInteger.valueOf(value.getEpochSecond());
    }

    @Override
    public Instant fromMetadata(Object metadata) {
        return Instant.ofEpochSecond(((BigInteger) metadata).longValue());
    }
}
```

### Usage

```java
// Build the object
Cip25NftMetadata nft = new Cip25NftMetadata();
nft.setVersion("1.0");
nft.setAuthor("my-app");
nft.setName("DevnetNFT#001");
nft.setImage("ipfs://QmXyZ123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789end");
nft.setMediaType("image/png");
nft.setDescription(Optional.of("A test NFT for devnet integration"));
nft.setFiles(List.of(
        new NftFileDetail("thumbnail.png", "ipfs://QmThumb1", "image/png"),
        new NftFileDetail("high-res.png", "ipfs://QmHighRes1", "image/png")
));
nft.setDisplayMedia(new ImageContent("ipfs://QmDisplay1", 1920, 1080));
nft.setPolicyId(HexUtil.decodeHexString("aabbccdd11223344aabbccdd11223344aabbccdd11223344aabbccdd"));
nft.setRarity(NftRarity.RARE);
nft.setMintedAt(Instant.ofEpochSecond(1700000000L));
nft.setAttributes(Map.of("background", "blue", "eyes", "green"));
nft.setInternalTrackingId("tracking-123");  // Will be ignored

// Convert, submit, and retrieve
var converter = new Cip25NftMetadataMetadataConverter();
Metadata metadata = converter.toMetadata(nft);

Tx tx = new Tx()
        .payToAddress(receiverAddr, Amount.ada(1.5))
        .attachMetadata(metadata)
        .from(senderAddr);

Result<String> result = new QuickTxBuilder(backendService)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(account))
        .completeAndWait(System.out::println);

// After retrieval from chain:
// - name, image, version, author: preserved
// - description: Optional.of("A test NFT...")
// - displayMedia: instanceof ImageContent with url, width, height
// - policyId: decoded from hex back to byte[]
// - rarity: NftRarity.RARE
// - mintedAt: Instant at epoch 1700000000
// - internalTrackingId: null (ignored)
// - mediaType: "image/png" (or default if absent)
```

## Next Steps

- [Getting Started](01-getting-started.md) — quick setup and first example
- [Annotations Reference](02-annotations-reference.md) — full attribute reference
- [Supported Types](04-supported-types.md) — complete type compatibility table
