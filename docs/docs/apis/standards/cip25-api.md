---
title: "CIP25 API"
description: "NFT Metadata Standard implementation"
sidebar_position: 1
---

# CIP25 API

CIP25 (Cardano Improvement Proposal 25) defines a standard for NFT metadata on the Cardano blockchain. The Cardano Client Library provides APIs to create and manage CIP25 compliant NFT metadata.

## Key Features

- **Standard Compliance**: Full CIP25 specification support
- **Metadata Creation**: Easy creation of NFT metadata
- **Validation**: Built-in validation for CIP25 compliance
- **Flexible**: Support for various NFT attributes

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip25
- **Dependencies**: plutus, metadata

## Usage Examples

### Creating NFT Metadata

The following example shows how to create CIP25 compliant NFT metadata with various attributes and files.

```java
// Create CIP25 NFT metadata
CIP25NFT cip25NFT = CIP25NFT.create()
        .name("MyAwesomeNFT")
        .image("https://example.com/image.png")
        .description("A sample CIP25 NFT")
        .addFile(CIP25File.create()
                .mediaType("image/png")
                .name("image.png")
                .src("https://example.com/image.png")
        )
        .addAttribute("color", "blue")
        .addAttribute("rarity", "legendary");

// Get metadata as PlutusData
PlutusData metadata = cip25NFT.getMetadataAsPlutusData();
```

### Minting NFT with CIP25 Metadata

The following example shows how to mint an NFT using a Plutus script with CIP25 metadata attached.

```java
// Create minting script
PlutusV2Script mintingScript = PlutusV2Script.builder()
        .type("PlutusScriptV2")
        .cborHex("...")
        .build();

// Create NFT asset
Asset nftAsset = Asset.builder()
        .name("MyAwesomeNFT")
        .value(BigInteger.ONE)
        .build();

// Mint NFT with metadata
Tx tx = new Tx()
        .mintAsset(mintingScript, List.of(nftAsset), metadata, receiverAddress)
        .from(senderAddress);
```

### Validating Metadata

The following example shows how to validate NFT metadata for CIP25 compliance and retrieve any validation errors.

```java
// Validate CIP25 compliance
boolean isValid = CIP25NFT.isValidMetadata(metadata);

// Get validation errors
List<String> errors = CIP25NFT.getValidationErrors(metadata);
```

### Working with Files

The following example shows how to add multiple files to NFT metadata.

```java
CIP25NFT nft = CIP25NFT.create()
    .name("Multi-file NFT")
    .description("NFT with multiple files")
    .addFile(CIP25File.create()
        .mediaType("image/png")
        .name("thumbnail.png")
        .src("https://example.com/thumbnail.png"))
    .addFile(CIP25File.create()
        .mediaType("video/mp4")
        .name("animation.mp4")
        .src("https://example.com/animation.mp4"));
```

### Adding Custom Attributes

The following example shows how to add custom attributes to NFT metadata.

```java
CIP25NFT nft = CIP25NFT.create()
    .name("Custom Attribute NFT")
    .description("NFT with custom attributes")
    .addAttribute("color", "blue")
    .addAttribute("rarity", "legendary")
    .addAttribute("power", "100")
    .addAttribute("category", "gaming");
```

## API Reference

### CIP25NFT Class

Main class for creating and managing CIP25 NFT metadata.

#### Constructor
```java
// Create new instance
public static CIP25NFT create()
```

#### Methods

##### name(String name)
Sets the name of the NFT.

```java
public CIP25NFT name(String name)
```

##### description(String description)
Sets the description of the NFT.

```java
public CIP25NFT description(String description)
```

##### image(String image)
Sets the image URL of the NFT.

```java
public CIP25NFT image(String image)
```

##### addFile(CIP25File file)
Adds a file to the NFT metadata.

```java
public CIP25NFT addFile(CIP25File file)
```

##### addAttribute(String key, String value)
Adds a custom attribute to the NFT.

```java
public CIP25NFT addAttribute(String key, String value)
```

##### getMetadataAsPlutusData()
Converts the metadata to PlutusData format.

```java
public PlutusData getMetadataAsPlutusData()
```

##### isValidMetadata(PlutusData metadata)
Validates metadata for CIP25 compliance.

```java
public static boolean isValidMetadata(PlutusData metadata)
```

##### getValidationErrors(PlutusData metadata)
Gets validation errors for metadata.

```java
public static List<String> getValidationErrors(PlutusData metadata)
```

### CIP25File Class

Represents a file in CIP25 metadata.

#### Constructor
```java
// Create new instance
public static CIP25File create()
```

#### Methods
```java
// Set media type
public CIP25File mediaType(String mediaType)

// Set file name
public CIP25File name(String name)

// Set file source URL
public CIP25File src(String src)
```

## CIP25 Specification Details

### Metadata Label
CIP25 uses metadata label **721** for NFT metadata.

### Metadata Structure
NFT metadata follows this structure:
```json
{
  "721": {
    "policy_id": {
      "asset_name": {
        "name": "NFT Name",
        "image": "https://example.com/image.png",
        "description": "NFT Description",
        "files": [
          {
            "mediaType": "image/png",
            "name": "image.png",
            "src": "https://example.com/image.png"
          }
        ],
        "attributes": [
          {
            "trait_type": "color",
            "value": "blue"
          }
        ]
      }
    }
  }
}
```

### File Structure
Files in CIP25 metadata support:
- **mediaType**: MIME type of the file
- **name**: Display name of the file
- **src**: URL or IPFS hash of the file

### Attributes
Custom attributes support:
- **trait_type**: The type of attribute
- **value**: The value of the attribute

## Best Practices

1. **Use HTTPS URLs**: Always use HTTPS URLs for images and files
2. **Optimize Images**: Use appropriate image sizes and formats
3. **Validate Metadata**: Always validate metadata before minting
4. **Use IPFS**: Consider using IPFS for decentralized file storage
5. **Document Attributes**: Document custom attributes for better discoverability

## Integration Examples

### With CIP20 (Transaction Messages)
```java
// Create CIP25 NFT metadata
CIP25NFT nftMetadata = CIP25NFT.create()
    .name("MyNFT")
    .image("https://example.com/image.png")
    .description("An NFT with transaction messages");

// Create CIP20 message metadata
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("NFT purchase")
    .add("Collection: Art Gallery");

// Combine metadata
CBORMetadata combinedMetadata = new CBORMetadata();
combinedMetadata.putAll(nftMetadata);
combinedMetadata.putAll(messageMetadata);
```

### With CIP67 (Asset Name Labels)
```java
// Create CIP67-compliant asset name
int label = 222; // NFT label
byte[] labelPrefix = CIP67AssetNameUtil.labelToPrefix(label);
String assetName = HexUtil.encodeHexString(labelPrefix) + "MyNFT";

// Create CIP25 NFT with CIP67-compliant name
CIP25NFT nft = CIP25NFT.create()
    .name("My CIP67 NFT")
    .image("https://example.com/image.png");

Asset nftAsset = Asset.builder()
    .name(assetName)
    .value(BigInteger.ONE)
    .build();
```

For more information about CIP25, refer to the [official CIP25 specification](https://cips.cardano.org/cips/cip25/).
