---
title: "CIP67 API"
description: "Asset Name Label Registry implementation"
sidebar_position: 7
---

# CIP67 API

CIP67 (Cardano Improvement Proposal 67) defines a standard for asset name labels on the Cardano blockchain. The Cardano Client Library provides utilities for creating, validating, and working with CIP67-compliant asset names.

## Key Features

- **Label Encoding**: Convert decimal labels to CIP67 asset name prefixes
- **Label Decoding**: Extract labels from CIP67 asset names
- **Validation**: Verify CIP67 compliance and checksum validation
- **CRC8 Checksum**: Built-in CRC8 checksum calculation and verification
- **Standard Compliance**: Full CIP67 specification support

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip67
- **Dependencies**: crypto

## Usage Examples

### Converting Labels to Asset Name Prefixes

```java
// Convert a decimal label to CIP67 asset name prefix
int label = 222; // Label range: [0, 65535]
byte[] prefixBytes = CIP67AssetNameUtil.labelToPrefix(label);

// Convert to hex string for display
String prefixHex = HexUtil.encodeHexString(prefixBytes);
System.out.println("Label " + label + " -> Prefix: " + prefixHex);
// Output: Label 222 -> Prefix: 000de140
```

### Extracting Labels from Asset Names

```java
// Extract label from CIP67 asset name prefix
byte[] prefixBytes = HexUtil.decodeHexString("000de140");
int extractedLabel = CIP67AssetNameUtil.prefixToLabel(prefixBytes);

System.out.println("Prefix 000de140 -> Label: " + extractedLabel);
// Output: Prefix 000de140 -> Label: 222
```

### Validating CIP67 Asset Names

```java
// Validate asset name from bytes
byte[] assetNameBytes = HexUtil.decodeHexString("000de140");
boolean isValidBytes = CIP67AssetNameUtil.isValidAssetName(assetNameBytes);

System.out.println("Asset name valid (bytes): " + isValidBytes);
// Output: Asset name valid (bytes): true

// Validate asset name from integer
int assetNameInt = 0x000de140;
boolean isValidInt = CIP67AssetNameUtil.isValidAssetName(assetNameInt);

System.out.println("Asset name valid (int): " + isValidInt);
// Output: Asset name valid (int): true
```

### Working with Asset Names in Transactions

```java
// Create asset name with CIP67 label
int label = 222; // NFT label
byte[] labelPrefix = CIP67AssetNameUtil.labelToPrefix(label);

// Create full asset name by combining prefix with unique suffix
byte[] uniqueSuffix = "MyNFT".getBytes(StandardCharsets.UTF_8);
byte[] fullAssetName = new byte[labelPrefix.length + uniqueSuffix.length];
System.arraycopy(labelPrefix, 0, fullAssetName, 0, labelPrefix.length);
System.arraycopy(uniqueSuffix, 0, fullAssetName, labelPrefix.length, uniqueSuffix.length);

// Create asset with CIP67-compliant name
Asset nftAsset = Asset.builder()
    .name(HexUtil.encodeHexString(fullAssetName))
    .value(BigInteger.ONE)
    .build();
```

### Batch Processing of Asset Names

```java
// Process multiple asset names
List<String> assetNameHexes = Arrays.asList(
    "000de140", // Valid CIP67 asset name
    "000de141", // Invalid padding
    "100de140", // Invalid padding
    "000de130"  // Invalid checksum
);

for (String assetNameHex : assetNameHexes) {
    byte[] assetNameBytes = HexUtil.decodeHexString(assetNameHex);
    boolean isValid = CIP67AssetNameUtil.isValidAssetName(assetNameBytes);
    
    if (isValid) {
        int label = CIP67AssetNameUtil.prefixToLabel(assetNameBytes);
        System.out.println(assetNameHex + " -> Valid, Label: " + label);
    } else {
        System.out.println(assetNameHex + " -> Invalid");
    }
}
```

### Common Label Examples

```java
// Common CIP67 labels
Map<Integer, String> commonLabels = new HashMap<>();
commonLabels.put(222, "NFT Standard");
commonLabels.put(333, "Fungible Token Standard");
commonLabels.put(444, "Rich Fungible Token Standard");

for (Map.Entry<Integer, String> entry : commonLabels.entrySet()) {
    int label = entry.getKey();
    String description = entry.getValue();
    
    byte[] prefix = CIP67AssetNameUtil.labelToPrefix(label);
    String prefixHex = HexUtil.encodeHexString(prefix);
    
    System.out.println("Label " + label + " (" + description + ") -> " + prefixHex);
}
```

## API Reference

### CIP67AssetNameUtil Class

Utility class for CIP67 asset name operations.

#### Static Methods

##### labelToPrefix(int label)
Converts a decimal label to CIP67 asset name prefix.

```java
public static byte[] labelToPrefix(int label)
```

**Parameters:**
- `label` - Decimal label in range [0, 65535]

**Returns:** 4-byte prefix array

##### prefixToLabel(byte[] labelBytes)
Extracts the label from CIP67 asset name prefix.

```java
public static int prefixToLabel(byte[] labelBytes)
```

**Parameters:**
- `labelBytes` - 4-byte prefix array

**Returns:** Decimal label

##### isValidAssetName(int assetName)
Validates a CIP67 asset name from integer value.

```java
public static boolean isValidAssetName(int assetName)
```

**Parameters:**
- `assetName` - Asset name as integer

**Returns:** true if valid CIP67 asset name

##### isValidAssetName(byte[] assetName)
Validates a CIP67 asset name from byte array.

```java
public static boolean isValidAssetName(byte[] assetName)
```

**Parameters:**
- `assetName` - Asset name as byte array

**Returns:** true if valid CIP67 asset name

## CIP67 Specification Details

### Asset Name Structure
CIP67 asset names have the following structure:
- **Bits 0-3**: Reserved (must be 0000)
- **Bits 4-11**: CRC8 checksum
- **Bits 12-31**: Label (20 bits, range 0-1048575, but typically 0-65535)
- **Bits 32+**: Asset-specific data

### Label Constraints
- **Range**: 0 to 65535 (16-bit range commonly used)
- **Encoding**: Big-endian format
- **Checksum**: CRC8 validation required

### Validation Rules
1. **Padding Check**: Bits 0-3 must be 0000
2. **Checksum Verification**: CRC8 checksum must match calculated value
3. **Label Range**: Label must be within valid range

## Common Label Registry

| Label | Description | Prefix (Hex) |
|-------|-------------|--------------|
| 222 | NFT Standard | 000de140 |
| 333 | Fungible Token Standard | 0014d140 |
| 444 | Rich Fungible Token Standard | 001bc140 |

## Best Practices

1. **Use Standard Labels**: Use established labels (222, 333, 444) for common asset types
2. **Validate Asset Names**: Always validate asset names before using them
3. **Handle Errors**: Check validation results before processing asset names
4. **Preserve Checksums**: Don't modify asset name prefixes without recalculating checksums
5. **Document Custom Labels**: Document any custom labels used in your application

## Integration with Other Standards

### With CIP68 (Datum Metadata)
```java
// Create CIP68-compliant asset name
int label = 222; // NFT label for CIP68
byte[] labelPrefix = CIP67AssetNameUtil.labelToPrefix(label);

// Combine with asset-specific data
String assetName = HexUtil.encodeHexString(labelPrefix) + "MyUniqueNFT";
```

### With CIP25 (NFT Metadata)
```java
// Create CIP25 NFT with CIP67-compliant name
int label = 222;
byte[] labelPrefix = CIP67AssetNameUtil.labelToPrefix(label);
String assetName = HexUtil.encodeHexString(labelPrefix) + "MyNFT";

CIP25NFT nft = CIP25NFT.create()
    .name("My CIP67 NFT")
    .image("https://example.com/image.png");

Asset nftAsset = Asset.builder()
    .name(assetName)
    .value(BigInteger.ONE)
    .build();
```

For more information about CIP67, refer to the [official CIP67 specification](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0067).
