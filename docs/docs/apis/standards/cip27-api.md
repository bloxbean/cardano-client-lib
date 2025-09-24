---
title: "CIP27 API"
description: "CNFT Community Royalties Standard implementation"
sidebar_position: 5
---

# CIP27 API

CIP27 (Cardano Improvement Proposal 27) defines a standard for CNFT (Community NFT) royalties on the Cardano blockchain. The Cardano Client Library provides APIs to create and manage royalty metadata for NFTs.

## Key Features

- **Royalty Management**: Set royalty rates and recipient addresses
- **Standard Compliance**: Full CIP27 specification support
- **Validation**: Built-in validation for royalty rates (0.0-1.0)
- **NFT Integration**: Seamless integration with CIP25 NFT metadata

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip27
- **Dependencies**: cip25

## Usage Examples

### Creating Royalty Metadata

```java
// Create royalty token
RoyaltyToken royaltyToken = RoyaltyToken.create()
    .address("addr1q9...") // Recipient address
    .rate(0.05); // 5% royalty rate

// Create royalty metadata
RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create()
    .royaltyToken(royaltyToken);

// Get metadata as CBOR bytes
byte[] cborBytes = royaltyMetadata.serialize();
```

### Integration with CIP25 NFT Metadata

```java
// Create CIP25 NFT metadata
CIP25NFT nftMetadata = CIP25NFT.create()
    .name("My Royalty NFT")
    .image("https://example.com/image.png")
    .description("An NFT with royalties");

// Create royalty metadata
RoyaltyToken royaltyToken = RoyaltyToken.create()
    .address("addr1q9...")
    .rate(0.1); // 10% royalty

RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create()
    .royaltyToken(royaltyToken);

// Combine metadata
CBORMetadata combinedMetadata = new CBORMetadata();
combinedMetadata.putAll(nftMetadata);
combinedMetadata.putAll(royaltyMetadata);
```

### Minting NFT with Royalties

```java
// Create minting script
PlutusV2Script mintingScript = PlutusV2Script.builder()
    .type("PlutusScriptV2")
    .cborHex("...")
    .build();

// Create NFT asset
Asset nftAsset = Asset.builder()
    .name("RoyaltyNFT")
    .value(BigInteger.ONE)
    .build();

// Create combined metadata
CBORMetadata metadata = new CBORMetadata();
metadata.putAll(nftMetadata);
metadata.putAll(royaltyMetadata);

// Mint NFT with royalty metadata
Tx tx = new Tx()
    .mintAsset(mintingScript, List.of(nftAsset), metadata, receiverAddress)
    .from(senderAddress);
```

### Retrieving Royalty Information

```java
// Deserialize royalty metadata
RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create(cborBytes);

// Get royalty token
RoyaltyToken royaltyToken = royaltyMetadata.getRoyaltyToken();

if (royaltyToken != null) {
    String recipientAddress = royaltyToken.getAddress();
    Double royaltyRate = royaltyToken.getRate();
    
    System.out.println("Royalty recipient: " + recipientAddress);
    System.out.println("Royalty rate: " + (royaltyRate * 100) + "%");
}
```

## API Reference

### RoyaltyTokenMetadata Class

Main class for managing CIP27 royalty metadata.

#### Constructor
```java
// Create new instance
public static RoyaltyTokenMetadata create()

// Create from CBOR bytes
public static RoyaltyTokenMetadata create(byte[] cborBytes)
```

#### Methods
```java
// Set royalty token
public RoyaltyTokenMetadata royaltyToken(RoyaltyToken royaltyToken)

// Get royalty token
public RoyaltyToken getRoyaltyToken()
```

### RoyaltyToken Class

Represents a royalty configuration for an NFT.

#### Constructor
```java
// Create new instance
public static RoyaltyToken create()
```

#### Methods
```java
// Set recipient address
public RoyaltyToken address(String address)

// Set royalty rate (0.0-1.0)
public RoyaltyToken rate(Double rate)

// Get recipient address
public String getAddress()

// Get royalty rate
public Double getRate()
```

## CIP27 Specification Details

### Metadata Label
CIP27 uses metadata label **777** for royalty metadata.

### Royalty Rate Validation
- Must be between 0.0 and 1.0 (inclusive)
- Represents percentage as decimal (0.1 = 10%)
- Throws `IllegalArgumentException` for invalid rates

### Address Format
- Must be a valid bech32 payment address
- Supports both single addresses and address lists

## Best Practices

1. **Validate rates**: Always ensure royalty rates are between 0.0 and 1.0
2. **Use valid addresses**: Ensure recipient addresses are valid bech32 addresses
3. **Combine with CIP25**: Use CIP27 alongside CIP25 for complete NFT metadata
4. **Test thoroughly**: Verify royalty calculations before deployment

## Integration Examples

### With CIP25 (NFT Metadata)
```java
// Create CIP25 NFT metadata
CIP25NFT nftMetadata = CIP25NFT.create()
    .name("Royalty NFT")
    .image("https://example.com/image.png")
    .description("An NFT with royalties");

// Create royalty metadata
RoyaltyToken royaltyToken = RoyaltyToken.create()
    .address("addr1q9...")
    .rate(0.1); // 10% royalty

RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create()
    .royaltyToken(royaltyToken);

// Combine metadata
CBORMetadata combinedMetadata = new CBORMetadata();
combinedMetadata.putAll(nftMetadata);
combinedMetadata.putAll(royaltyMetadata);
```

### With CIP20 (Transaction Messages)
```java
// Create royalty metadata
RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create()
    .royaltyToken(royaltyToken);

// Create transaction message
MessageMetadata messageMetadata = MessageMetadata.create()
    .add("Royalty NFT purchase")
    .add("Collection: Premium Art");

// Combine metadata
CBORMetadata combinedMetadata = new CBORMetadata();
combinedMetadata.putAll(royaltyMetadata);
combinedMetadata.putAll(messageMetadata);
```

For more information about CIP27, refer to the [official CIP27 specification](https://cips.cardano.org/cips/cip27/).
