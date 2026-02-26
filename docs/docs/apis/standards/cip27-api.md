---
title: "CIP27 API"
description: "CNFT Community Royalties Standard implementation"
sidebar_position: 5
---

# CIP27 API

CIP27 (Cardano Improvement Proposal 27) defines a standard for CNFT (Community NFT) royalties on the Cardano blockchain. The Cardano Client Library provides models for the royalty metadata under label 777. You set an address and rate on `RoyaltyToken`, wrap it with `RoyaltyTokenMetadata`, and combine it with CIP25 metadata when minting.\n+\n+Royalties in CIP27 are purely descriptive metadata; enforcement is handled off-chain by marketplaces that honor the standard.

## Key Features

- **Royalty Token Model**: Address + rate
- **Metadata Wrapper**: `RoyaltyTokenMetadata` under label 777
- **CIP25 Friendly**: Uses the same `NFTProperties` base for extensibility

## Dependencies

- **Group ID**: com.bloxbean.cardano
- **Artifact ID**: cardano-client-cip27
- **Dependencies**: cip25

## Usage Examples

### Creating Royalty Metadata

```java
RoyaltyToken royaltyToken = RoyaltyToken.create()
        .address("addr1...")
        .rate(0.05); // 5%

RoyaltyTokenMetadata royaltyMetadata = RoyaltyTokenMetadata.create()
        .royaltyToken(royaltyToken);

byte[] cborBytes = royaltyMetadata.serialize();
```

### Combine with CIP25 NFT Metadata

```java
NFT nft = NFT.create()
        .assetName("RoyaltyNFT")
        .name("My Royalty NFT")
        .image("https://example.com/image.png");

NFTMetadata nftMetadata = NFTMetadata.create().addNFT(policyId, nft);

CBORMetadata combined = (CBORMetadata) nftMetadata.merge(royaltyMetadata);

Tx tx = new Tx()
        .mintAssets(mintingScript, List.of(nftAsset))
        .attachMetadata(combined)
        .from(senderAddress);
```

### Reading Royalty Information

```java
RoyaltyTokenMetadata metadata = RoyaltyTokenMetadata.create(cborBytes);
RoyaltyToken token = metadata.getRoyaltyToken();

String recipient = token.getAddress();
Double rate = token.getRate();
```

## API Reference

### RoyaltyTokenMetadata

```java
public static RoyaltyTokenMetadata create()
public static RoyaltyTokenMetadata create(byte[] cborBytes)

public RoyaltyTokenMetadata royaltyToken(RoyaltyToken royaltyToken)
public RoyaltyToken getRoyaltyToken()
public byte[] serialize()
```

### RoyaltyToken

```java
public static RoyaltyToken create()
public RoyaltyToken address(String address)
public RoyaltyToken rate(Double rate) // 0.0 - 1.0 inclusive
public String getAddress()
public Double getRate()
```

## CIP27 Specification Details

- **Metadata Label**: 777
- **Royalty Rate**: Decimal fraction between 0.0 and 1.0 (validated in setter)
- **Address Format**: Stored as provided (no automatic bech32 validation)

## Best Practices

1. Validate royalty addresses before storing.
2. Keep rates within 0.0–1.0 (the setter enforces this range).
3. Combine with CIP25 metadata for complete NFT records.
4. Use `CBORMetadata.merge` to combine royalty and NFT metadata.

For more information about CIP27, refer to the [official CIP27 specification](https://cips.cardano.org/cips/cip27/).
