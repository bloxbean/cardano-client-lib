---
description: CIP68 Datum Metadata Api
sidebar_label: CIP68 Datum Metadata Api
sidebar_position: 2
---

# CIP68 Datum Metadata Api

**Version:** **0.5.1** and later

## Introduction

CIP68 defines a metadata standard for native assets making use of output datums not only for NFTs but any asset class.
Cardano Client Lib provides a simple API to build CIP68 compatible datum metadata and simplify the process of building transactions
to mint CIP68 compatible native tokens.

[CIP68 - Datum Metadata Standard](https://github.com/cardano-foundation/CIPs/tree/master/CIP-0068)

## Dependencies

- **Group Id   :** com.bloxbean.cardano
- **Artifact Id:** cardano-client-cip68

## CIP68 Apis
Cardano Client Lib provides the following 3 main APIs to build CIP68 compatible datum metadata for different types of assets.
1. **CIP68NFT** : For NFTs (222 NFT Standard)
2. **CIP68FT**  : For Fungible Tokens (333 Fungible Token Standard)
3. **CIP68RFT** : For Rich (444 Rich-FT Standard)

Apart from the above APIs, there is an API **CIP68ReferenceToken** which is used for reference tokens. A reference token 
instance can be created from the above 3 APIs.

### CIP68 NFT Datum Metadata Api

The following example shows how to create a CIP68NFT instance and use it to mint a CIP68 NFT.

In **Line 8**, create a CIP68NFT instance by calling the static method `CIP68NFT.create()`. Then set the name, description, 
image and other properties of the NFT according to the CIP68 standard. 

In **Line 19**, get the corresponding CIP68ReferenceToken instance from CIP68NFT instance. 

In **Line 22**, get the Asset instance from CIP68ReferenceToken instance. This Asset instance can be used to mint the reference token.

In **Line 25**, get the Asset instance from CIP68NFT instance. This Asset instance can be used to mint the user token.

In **Line 28**, get the Datum from CIP68ReferenceToken instance. This Datum instance can be used to mint the reference token with CIP68 metadata
in datum.

In **Line 37**, create a ScriptTx instance and define to mint the reference token and user token.

```java showLineNumbers
 //Minting Script
PlutusV2Script mintingScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("...")
                .build();

//Define CIP-68 compatible NFT metadata
CIP68NFT nft = CIP68NFT.create()
                .name("MyCIP68NFT")
                .image("https://xyz.com/image.png")
                .description("A sample CIP-68 NFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image1.png")
                        .src("https://xyz.com/image.png")
                );

//Get CIP68ReferenceToken from CIP68NFT
CIP68ReferenceToken referenceToken = nft.getReferenceToken();
        
//Create Reference Token Asset with quantity 1
Asset referenzToken = referenceToken.getAsset();
        
//Create User Token Asset with quantity 1
Asset userToken = nft.getAsset(BigInteger.valueOf(1));

//Get Datum from CIP68ReferenceToken
PlutusData datumMetadata = referenceToken.getDatumAsPlutusData();

//Receiver of the user token
String userTokenReceiver = receiverAddr;
        
//Receiver of the reference token which is the minting script address
String referenceTokenReceiver = AddressProvider.getEntAddress(mintingScript, Networks.preprod()).toBech32();

//Define ScriptTx to mint Reference Token and User Token
ScriptTx scriptTx = new ScriptTx()
        .mintAsset(mintingScript, List.of(referenzToken), PlutusData.unit(), referenceTokenReceiver, datumMetadata)
        .mintAsset(mintingScript, List.of(userToken),PlutusData.unit(), userTokenReceiver);
        
Result<String> result = quickTxBuilder.compose(scriptTx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait(System.out::println);
```

### CIP68 FT Datum Metadata Api

CIP68FT is used to mint CIP68 compatible Fungible Tokens (333). The following example shows how to create a CIP68FT instance.
From CIP68FT instance, you can get a CIP68ReferenceToken instance. The remaining steps are similar to previous section (CIP68NFT).

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

CIP68RFT is used to mint CIP68 compatible Rich Fungible Tokens (444). The following example shows how to create a CIP68RFT instance.
From CIP68RFT instance, you can get a CIP68ReferenceToken instance. The remaining steps are similar to CIP68NFT section.

```java
CIP68RFT rft = CIP68RFT.create()
                .name("SampleRichFungibleToken")
                .image("https://xyz.com/image.png")
                .description("Sample CIP-68 RFT")
                .addFile(CIP68File.create()
                        .mediaType("image/png")
                        .name("image.png")
                        .src("https://xyz.com/image.png")
                )
                .property("<key1>", "<key1Value>");
```
