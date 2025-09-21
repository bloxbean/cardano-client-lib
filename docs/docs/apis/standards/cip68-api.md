---
description: CIP68 Datum Metadata Api
sidebar_label: CIP68 Datum Metadata Api
sidebar_position: 2
---

# CIP68 Datum Metadata Api

**Version:** **0.5.1** and later

CIP68 defines a metadata standard for native assets making use of output datums not only for NFTs but any asset class.
Cardano Client Lib provides a simple API to build CIP68 compatible datum metadata and simplify the process of building transactions
to mint CIP68 compatible native tokens.

## Key Features

- **Datum-Based Metadata**: Uses output datums for metadata storage
- **Multiple Asset Types**: Support for NFTs, Fungible Tokens, and Rich Fungible Tokens
- **Reference Tokens**: Automatic reference token creation
- **Standard Compliance**: Full CIP68 specification support
- **Plutus Integration**: Native Plutus data support

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

## Usage Examples

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

## API Reference

### CIP68NFT Class
Main class for creating CIP68 NFT metadata.

#### Constructor
```java
// Create new instance
public static CIP68NFT create()
```

#### Methods
```java
// Set NFT name
public CIP68NFT name(String name)

// Set NFT description
public CIP68NFT description(String description)

// Set NFT image URL
public CIP68NFT image(String image)

// Add file to NFT
public CIP68NFT addFile(CIP68File file)

// Add property to NFT
public CIP68NFT property(String key, String value)

// Get reference token
public CIP68ReferenceToken getReferenceToken()

// Get asset with quantity
public Asset getAsset(BigInteger quantity)

// Convert to Plutus data
public PlutusData toPlutusData()
```

### CIP68FT Class
Main class for creating CIP68 Fungible Token metadata.

#### Methods
```java
// Set token name
public CIP68FT name(String name)

// Set token description
public CIP68FT description(String description)

// Set token ticker
public CIP68FT ticker(String ticker)

// Set token decimals
public CIP68FT decimals(int decimals)

// Set token URL
public CIP68FT url(String url)

// Set token logo
public CIP68FT logo(String logo)

// Convert to Plutus data
public PlutusData toPlutusData()
```

### CIP68RFT Class
Main class for creating CIP68 Rich Fungible Token metadata.

#### Methods
```java
// Set token name
public CIP68RFT name(String name)

// Set token description
public CIP68RFT description(String description)

// Set token image
public CIP68RFT image(String image)

// Set token decimals
public CIP68RFT decimals(int decimals)

// Add file to token
public CIP68RFT addFile(CIP68File file)

// Add property to token
public CIP68RFT property(String key, String value)

// Convert to Plutus data
public PlutusData toPlutusData()
```

### CIP68ReferenceToken Class
Represents a CIP68 reference token.

#### Methods
```java
// Get asset
public Asset getAsset()

// Get datum as Plutus data
public PlutusData getDatumAsPlutusData()
```

### CIP68File Class
Represents a file in CIP68 metadata.

#### Constructor
```java
// Create new instance
public static CIP68File create()
```

#### Methods
```java
// Set media type
public CIP68File mediaType(String mediaType)

// Set file name
public CIP68File name(String name)

// Set file source
public CIP68File src(String src)
```

## Policy Creation for CIP68 Tokens

To mint CIP68 tokens, you need to create a minting policy. Here's an example of how to create a simple minting policy:

```java
// Create account for policy
Account policyAccount = Account.createFromMnemonic(Networks.testnet(), "your mnemonic words");

// Create verification key from account
VerificationKey verificationKey = new VerificationKey(policyAccount.hdKeyPair().getPublicKey().getKeyData());

// Create script pubkey
ScriptPubkey scriptPubkey = new ScriptPubkey(verificationKey);

// Create script all (requires signature from the key)
ScriptAll scriptAll = new ScriptAll(List.of(scriptPubkey));

// Create policy
Policy policy = new Policy(scriptAll);

// Create PlutusV2Script from policy
PlutusV2Script mintingScript = PlutusV2Script.builder()
        .type("PlutusScriptV2")
        .cborHex(policy.getCborHex())
        .build();
```

## CIP68 Specification Details

### Datum Structure
CIP68 uses output datums to store metadata with the following structure:
- **Version**: Metadata version (typically "1.0")
- **Type**: Asset type (NFT, FT, RFT)
- **Metadata**: Asset-specific metadata as Plutus data

### Asset Labels
CIP68 uses CIP67-compliant asset labels:
- **222**: NFT Standard
- **333**: Fungible Token Standard  
- **444**: Rich Fungible Token Standard

### Reference Token Pattern
- **Reference Token**: Contains metadata in datum
- **User Token**: Contains actual asset value
- **Separation**: Metadata and value are stored separately

### Datum Hash
- **Purpose**: Reference tokens use datum hash as unique identifier
- **Storage**: Metadata stored in datum, not transaction metadata
- **Retrieval**: Use datum hash to fetch metadata from blockchain

## Best Practices

1. **Use Reference Tokens**: Always create reference tokens for metadata storage
2. **Validate Datums**: Ensure datum structure follows CIP68 specification
3. **Use CIP67 Labels**: Apply appropriate CIP67 asset name labels
4. **Optimize Metadata**: Keep datum metadata size reasonable
5. **Handle Errors**: Validate datum deserialization and structure

## Advanced CIP68 Features

### CIP68 Utilities

The CIP68 module provides utility classes for working with datum metadata:

#### CIP68Util
```java
// Utility functions for CIP68 operations
CIP68Util util = new CIP68Util();

// Convert between different datum formats
PlutusData convertedDatum = util.convertToPlutusData(metadataMap);

// Validate datum structure
boolean isValid = util.validateDatum(datum);
```

#### CIP68TokenTemplate
```java
// Create token template for consistent token creation
CIP68TokenTemplate template = CIP68TokenTemplate.create()
    .label(222) // NFT label
    .name("MyToken")
    .description("Token description");

// Apply template to create token
CIP68NFT token = template.applyToNFT();
```

#### DatumProperties
```java
// Create datum properties
DatumProperties properties = DatumProperties.create()
    .version("1.0")
    .type("NFT")
    .metadata(metadataMap);

// Get properties from existing datum
DatumProperties extracted = DatumProperties.fromDatum(datum);
String version = extracted.getVersion();
String type = extracted.getType();
```

## Integration with Other Standards

CIP68 works seamlessly with other Cardano standards:

### With CIP67 (Asset Name Labels)
```java
// Create CIP68 NFT with CIP67-compliant asset name
CIP68NFT nft = CIP68NFT.create()
    .name("MyCIP68NFT")
    .description("NFT with CIP67 asset naming");

// The asset name will automatically include CIP67 label 222
String assetName = nft.getAssetNameAsHex();
```

### With CIP25 (NFT Metadata)
```java
// Combine CIP68 datum metadata with CIP25 transaction metadata
CIP68NFT cip68NFT = CIP68NFT.create()
    .name("MyNFT")
    .description("Combined metadata NFT");

CIP25NFT cip25NFT = CIP25NFT.create()
    .name("MyNFT")
    .image("https://example.com/image.png");

// Use CIP68 for datum, CIP25 for transaction metadata
CBORMetadata transactionMetadata = new CBORMetadata();
transactionMetadata.putAll(cip25NFT);
```
