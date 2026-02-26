---
description: CIP68 Datum Metadata Api
sidebar_label: CIP68 Datum Metadata Api
sidebar_position: 2
---

# CIP68 Datum Metadata Api

**Version:** **0.5.1** and later

CIP68 defines a metadata standard for native assets using output datums. Cardano Client Lib provides builders for CIP68 NFT, FT, RFT tokens and their reference tokens so you can create the datum payloads, asset names, and reference tokens without manual Plutus data handling.

## Key Features

- **Datum-Based Metadata** for NFTs, FTs, and RFTs
- **Reference Tokens** (label 100) derived from each token
- **CIP67 Labels** baked into asset names (222 / 333 / 444)
- **Plutus Data Output** via `getDatumAsPlutusData()`

## Dependencies

- **Group Id   :** com.bloxbean.cardano
- **Artifact Id:** cardano-client-cip68

## Usage Examples

### CIP68 NFT Datum Metadata Api

Build the on-chain datum metadata and related assets for an NFT that follows the 222 label convention.

```java
CIP68NFT nft = CIP68NFT.create()
        .name("MyCIP68NFT")
        .image("https://xyz.com/image.png")
        .description("A sample CIP-68 NFT")
        .addFile(CIP68File.create()
                .mediaType("image/png")
                .name("image1.png")
                .src("https://xyz.com/image.png"));

// Reference token (label 100) and user token assets
CIP68ReferenceToken referenceToken = nft.getReferenceToken();
Asset referenceAsset = referenceToken.getAsset(); // quantity 1 enforced
Asset userAsset = nft.getAsset(BigInteger.ONE);

PlutusData datumMetadata = referenceToken.getDatumAsPlutusData();
```

### CIP68 FT Datum Metadata Api

Create fungible token (333) metadata in datum form with ticker/decimals helpers.

```java
CIP68FT ft = CIP68FT.create()
        .name("SampleFungibleToken")
        .ticker("SFT")
        .url("https://xyz.com")
        .logo("https://xyz.com/logo.png")
        .decimals(6)
        .description("Sample CIP-68 FT");
```

### CIP68 RFT Datum Metadata Api

Create rich fungible token (444) metadata where you can attach images/files and decimals.

```java
CIP68RFT rft = CIP68RFT.create()
        .name("SampleRichFungibleToken")
        .image("https://xyz.com/image.png")
        .description("Sample CIP-68 RFT")
        .decimals(2)
        .addFile(CIP68File.create()
                .mediaType("image/png")
                .name("image.png")
                .src("https://xyz.com/image.png"))
        .property("category", "art");
```

### Building a Transaction

Reference tokens carry the datum; user tokens carry value. Include both mints in the same script transaction.

```java
PlutusData datum = referenceToken.getDatumAsPlutusData();
String referenceTokenReceiver = AddressProvider.getEntAddress(mintingScript, Networks.preprod()).toBech32();

ScriptTx scriptTx = new ScriptTx()
        .mintAsset(mintingScript, List.of(referenceAsset), PlutusData.unit(), referenceTokenReceiver, datum)
        .mintAsset(mintingScript, List.of(userAsset), PlutusData.unit(), userTokenReceiver);
```

## API Reference

### CIP68NFT

```java
public static CIP68NFT create()
public CIP68NFT name(String name)
public CIP68NFT description(String description)
public CIP68NFT image(String image)
public CIP68NFT addFile(CIP68File file)
public CIP68NFT property(String key, String value)
public CIP68ReferenceToken getReferenceToken()
public Asset getAsset(BigInteger quantity)
public PlutusData getDatumAsPlutusData()
public static CIP68NFT fromDatum(byte[] datumBytes)
```

### CIP68FT

```java
public static CIP68FT create()
public CIP68FT name(String name)
public CIP68FT description(String description)
public CIP68FT ticker(String ticker)
public CIP68FT decimals(int decimals)
public CIP68FT url(String url)
public CIP68FT logo(String logo)
public CIP68ReferenceToken getReferenceToken()
public Asset getAsset(BigInteger quantity)
public PlutusData getDatumAsPlutusData()
public static CIP68FT fromDatum(byte[] datumBytes)
```

### CIP68RFT

```java
public static CIP68RFT create()
public CIP68RFT name(String name)
public CIP68RFT description(String description)
public CIP68RFT image(String image)
public CIP68RFT decimals(int decimals)
public CIP68RFT addFile(CIP68File file)
public CIP68RFT property(String key, String value)
public CIP68ReferenceToken getReferenceToken()
public Asset getAsset(BigInteger quantity)
public PlutusData getDatumAsPlutusData()
public static CIP68RFT fromDatum(byte[] datumBytes)
```

### CIP68ReferenceToken

```java
public CIP68ReferenceToken(CIP68TokenTemplate tokenTemplate)
public Asset getAsset() // quantity 1
public Asset getAsset(BigInteger quantity) // throws if quantity > 1
public PlutusData getDatumAsPlutusData()
```

### CIP68File

```java
public static CIP68File create()
public CIP68File mediaType(String mediaType)
public CIP68File name(String name)
public CIP68File src(String src)
public PlutusData toPlutusData()
public static CIP68File create(MapPlutusData map)
```

## CIP68 Specification Details

- **Asset Labels**: 222 (NFT), 333 (FT), 444 (RFT) via CIP67 prefixes
- **Reference Token Label**: 100
- **Datum Structure**: ConstrPlutusData with metadata map, version, and extra field (see `CIP68Datum`)
- **Payload**: Stored in datum, not transaction metadata

## Best Practices

1. Use reference tokens for storing metadata on-chain.
2. Keep datum payloads small to reduce costs.
3. Validate URLs and sizes before serialization.
4. Ensure asset names (prefix + name) stay within 32 bytes (`getAsset` enforces this).

For more information about CIP68, refer to the [official CIP68 specification](https://developers.cardano.org/docs/governance/cardano-improvement-proposals/cip-0068/).
