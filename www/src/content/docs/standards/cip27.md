---
title: "CIP-27 — Royalties"
description: "CNFT Community Royalties Standard implementation"
---

CIP27 (Cardano Improvement Proposal 27) defines a standard for NFT royalties on the Cardano blockchain. The Cardano Client Library provides models for the royalty metadata under label 777. You set an address and rate on `RoyaltyToken`, wrap it with `RoyaltyTokenMetadata`, and attach it when you mint the policy's royalty token.

Royalties in CIP27 are purely descriptive metadata; enforcement is handled off-chain by marketplaces that honor the standard.

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

### Mint the Royalty Token, Then the NFTs

CIP-27 stores the royalty in an unnamed token, with the metadata under label 777. It must be minted before any other asset of the policy. Here `policy` is your `NativeScript` policy, and `policyKeys` its keys:

```java
// 1. The royalty token: quantity 1, and an empty asset name
Tx royaltyTx = new Tx()
        .mintAssets(policy, new Asset("", BigInteger.ONE), senderAddress)
        .attachMetadata(royaltyMetadata)
        .from(senderAddress);

quickTxBuilder.compose(royaltyTx)
        .withSigner(SignerProviders.signerFrom(sender))
        .withSigner(SignerProviders.signerFrom(policyKeys.getSkey()))
        .completeAndWait();

// 2. Then the NFTs, with their CIP-25 metadata
NFT nft = NFT.create()
        .assetName("RoyaltyNFT")
        .name("My Royalty NFT")
        .image("https://example.com/image.png");

Tx nftTx = new Tx()
        .mintAssets(policy, new Asset("RoyaltyNFT", BigInteger.ONE), senderAddress)
        .attachMetadata(NFTMetadata.create().addNFT(policy.getPolicyId(), nft))
        .from(senderAddress);

quickTxBuilder.compose(nftTx)
        .withSigner(SignerProviders.signerFrom(sender))
        .withSigner(SignerProviders.signerFrom(policyKeys.getSkey()))
        .completeAndWait();
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
- **Address Format**: Not validated. An address over 64 bytes is stored as an array of chunks, which CIP-27 allows

## Best Practices

1. Validate royalty addresses before storing.
2. Keep rates within 0.0-1.0 (the setter enforces this range).
3. Mint the royalty token before any other asset of the policy.

For more information about CIP27, refer to the [official CIP27 specification](https://cips.cardano.org/cips/cip27/).
