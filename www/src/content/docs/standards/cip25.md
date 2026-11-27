---
title: "CIP-25 — NFT metadata"
description: "NFT Metadata Standard implementation"
---

CIP25 (Cardano Improvement Proposal 25) defines a standard for NFT metadata on the Cardano blockchain. The Cardano Client Library provides builders for the CIP25 metadata map and NFT/file helpers. You assemble NFT entries, group them under a policy with `NFTMetadata`, and serialize to CBOR for use as transaction metadata.

## Key Features

- **Standard Compliance**: CIP25 metadata map (label 721)
- **NFT Builder**: `NFT` for asset fields and attributes
- **File Support**: `NFTFile` for rich file entries
- **Policy Map**: `NFTMetadata` for policy -> asset metadata layout

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip25
- **Dependencies**: metadata

## Usage Examples

### Creating NFT Metadata

Create the NFT entry with images, descriptions, and files, then insert it under your policy id.

```java
// Create CIP25 NFT entry
NFT nft = NFT.create()
        .assetName("MyAwesomeNFT")
        .name("MyAwesomeNFT")
        .image("https://example.com/image.png")
        .description("A sample CIP25 NFT")
        .addFile(NFTFile.create()
                .mediaType("image/png")
                .name("image.png")
                .src("https://example.com/image.png"))
        .property("color", "blue")
        .property("rarity", "legendary");

// Wrap under policy id and label 721
NFTMetadata nftMetadata = NFTMetadata.create()
        .addNFT(policyId, nft);

byte[] cborBytes = nftMetadata.serialize();
```

### Minting NFT with CIP25 Metadata

Pass the CBOR metadata into your transaction so wallets and indexers can read it later.

Attach the CBOR metadata when building your transaction.

```java
// mintingScript is your NativeScript policy, and policyId its getPolicyId()
Asset nftAsset = new Asset("MyAwesomeNFT", BigInteger.ONE);

Tx tx = new Tx()
        .mintAssets(mintingScript, List.of(nftAsset), senderAddress)
        .attachMetadata(nftMetadata)
        .from(senderAddress);
```

For a complete example, see [Minting an NFT](/learn/minting-an-nft/).

### Working with Files

Use `NFTFile` to attach one or more files (e.g., media, thumbnails) to the NFT entry.

```java
NFTFile file = NFTFile.create()
        .mediaType("image/png")
        .name("thumbnail.png")
        .src("https://example.com/thumbnail.png");

NFT nft = NFT.create()
        .assetName("MultiFile")
        .name("Multi-file NFT")
        .description("NFT with multiple files")
        .addFile(file);
```

### Custom Attributes

Store additional fields on the NFT or file objects via the shared `NFTProperties` helpers.

`NFT` and `NFTFile` inherit from `NFTProperties`, so you can add arbitrary fields.

```java
nft.property("power", "100");
nft.property("tags", List.of("gaming", "collectible"));
```

## API Reference

### NFTMetadata

```java
// Create new instance
public static NFTMetadata create()

// Create from CBOR bytes
public static NFTMetadata create(byte[] cborBytes)

public NFTMetadata addNFT(String policyId, NFT nft)
public NFT getNFT(String policyId, String assetName)
public NFTMetadata removeNFT(String policyId, String assetName)
public NFTMetadata version(int version) // default 1
public byte[] serialize()
```

### NFT

```java
public static NFT create()
public NFT assetName(String assetName)
public String getAssetName()

public NFT name(String name)
public String getName()

public NFT image(String imageUri) // a URI over 64 bytes is split into chunks
public List<String> getImages()

public NFT mediaType(String mediaType)
public String getMediaType()

public NFT description(String description) // supports multiple
public List<String> getDescriptions()

public NFT addFile(NFTFile nftFile)
public List<NFTFile> getFiles()

// Additional properties
public NFT property(String name, String value)
public NFT property(String name, Map<String, Object> values)
public NFT property(String name, List<String> values)
```

### NFTFile

```java
public static NFTFile create()
public NFTFile name(String name)
public String getName()

public NFTFile mediaType(String mediaType)
public String getMediaType()

public NFTFile src(String uri) // call multiple times to add more
public List<String> getSrcs()

// Additional properties
public NFTFile property(String name, String value)
public NFTFile property(String name, Map<String, Object> values)
public NFTFile property(String name, List<String> values)
```

## CIP25 Specification Details

- **Metadata Label**: 721
- **Structure**: `{ "721": { "<policy_id>": { "<asset_name>": { ... } } } }`
- **Files**: support `mediaType`, `name`, and one-or-many `src` values

## Best Practices

1. Use HTTPS or IPFS links for images/files.
2. Keep metadata concise to reduce transaction size.
3. Set one `image` per NFT: CIP-25 reads an `image` array as the chunks of one URI, so a second call would corrupt it. Use `addFile` for more files.
4. Set `assetName` on the `NFT` before adding it to `NFTMetadata`.
5. Validate URLs and lengths in your application code (no built-in validators are present).

## Integration Examples

Combine CIP25 metadata with other metadata using `CBORMetadata.merge(...)` if needed.

```java
CBORMetadata combined = (CBORMetadata) new CBORMetadata().merge(nftMetadata);
combined.put(BigInteger.valueOf(100), "extra-field");
```

For more information about CIP25, refer to the [official CIP25 specification](https://cips.cardano.org/cips/cip25/).
