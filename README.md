<div align="center">
        <img src="static/logo_no_bg.svg" width="600">
       
[![Clean, Build](https://github.com/bloxbean/cardano-client-lib/actions/workflows/build.yml/badge.svg)](https://github.com/bloxbean/cardano-client-lib/actions/workflows/build.yml)
[![CodeQL](https://github.com/bloxbean/cardano-client-lib/actions/workflows/codeql.yml/badge.svg)](https://github.com/bloxbean/cardano-client-lib/actions/workflows/codeql.yml)
[![License](https://img.shields.io:/github/license/bloxbean/cardano-client-lib?color=blue&label=license)](https://github.com/bloxbean/cardano-client-lib/blob/master/LICENSE)

</div>

A client library for Cardano in Java. This library simplifies the interaction with Cardano blockchain from a Java application.

### **Latest Stable Version**: [0.6.2](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.6.2)

### More details --> [Documentation](https://cardano-client.dev/)

### **Recent Posts**
- [Introducing QuickTx API to build transactions](https://satran004.medium.com/introducing-new-quicktx-api-in-cardano-client-lib-0-5-0-beta1-5beb491282ce)
- [Composable functions to build transactions](https://medium.com/coinmonks/cardano-client-lib-new-composable-functions-to-build-transaction-in-java-part-i-be3a8b4da835)
- [Demo:- Test Aiken Smart Contract Using Java Offchain Code with Yaci DevKit](https://youtu.be/PTnSc85t0Nk?si=44uK6KFrTIH3m06A)

### **Tutorials**
- [Simple Ada transfer](https://cardano-client.dev/docs/gettingstarted/simple-transfer)
- [Multisig transfer using Native Script](https://cardano-client.dev/docs/gettingstarted/multisig-quickstart)
- [Governance Api](https://cardano-client.dev/docs/apis/governance-api)

### **Old Posts**

**Note:** Some of the APIs mentioned in the below posts are deprecated. Please refer to the latest documentation for the latest APIs.

- [Call Plutus V2 contract from off-chain Java code](https://satran004.medium.com/call-plutus-v2-contract-from-off-chain-java-code-using-cardano-client-lib-e2b7e1b27c4)
- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537)
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e)
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb)

### **Examples**

[Cardano-client-lib examples repository](https://github.com/bloxbean/cardano-client-examples/tree/main/src/test/java/com/bloxbean/cardano/client/example)

### [JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-core/latest/index.html)

### **Modules**

In 0.4.0 and later, the library has been divided into smaller modules. These modules can be added to a project based on requirement.

**Group Id:** com.bloxbean.cardano

For **simple** setup, you can use **cardano-client-lib** and one of the backend provider as dependency.

| Modules                                          | Artifact Id                       | Description                                                                                                                                                                                       |
|--------------------------------------------------|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| cardano-client-lib                               | cardano-client-lib                | This is a top level module which includes all other modules except backend provider modules.<br/> **(Recommended for most applications)**                                                          | 
| [Backend Api](backend)                           | cardano-client-backend            | Defines backend apis which are implemented by provider specific module.                                                                                                                           |
 | [Blockfrost Backend](backend-modules/blockfrost) | cardano-client-backend-blockfrost | Provides integration with [Blockfrost](https://blockfrost.io/)                                                                                                                                    |  
| [Koios Backend](backend-modules/koios)           | cardano-client-backend-koios      | Provides integration with [Koios](https://www.koios.rest/)                                                                                                                                        |  
| [Ogmios/Kupo Backend](backend-modules/ogmios)    | cardano-client-backend-ogmios     | Provides integration with [Ogmios](https://ogmios.dev/) and [Kupo](https://cardanosolutions.github.io/kupo/). <br> **Supported Apis :** submitTransaction, evaluateTx, Kupo support (UtxoService) |  


For fine-grained dependency management, add one or more below modules as required.

| Modules                                      | Artifact Id                         | Description                                                                                                                                                                                                                                         |
|----------------------------------------------|-------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [common](common)                             | cardano-client-common               | Contains common utilities (HexUtil, JsonUtil, Cbor Utils etc). This module doesn't depend on any other module. <br/>  **Dependencies:** None                                                                                                        | 
 | [crypto](crypto)                             | cardano-client-crypto               | Provides implementation for standards like Bip32, Bip39, CIP1852 and other crypto primitives <br> **Dependencies:** common                                                                                                                          |
 | [address](address)                           | cardano-client-address              | Supports derivation of various types of Cardano addresses (Base, Enterprise, Pointer, Stake etc) <br> **Dependencies:** common, crypto, common-spec                                                                                                 |
| [metadata](metadata)                         | cardano-client-metadata             | Provides simple api to generate and serialize generic metadata <br/> **Dependencies:** common, crypto                                                                                                                                               |
 | [common-spec](common-spec)                   | cardano-client-common-spec          | A small module with some common serialization classes (CDDL spec) <br/> **Dependencies:** common, crypto                                                                                                                                            | 
 | [transaction-spec](transaction-spec)         | cardano-client-transaction-spec     | Provides cbor serialization code for transactions (CDDL spec). <br> **Dependencies:** common, common-spec, crypto, address, metadata, plutus                                                                                                        |
| [core](core)                                 | cardano-client-core                 | Cointains few common api like Account.<br> Also contains high-level api like PaymentTransaction for backward compatibility.<br> **Dependencies:** common, crypto, common-spec, address, metadata, transaction-spec, core-api, plutus, coinselection |
 | [core-api](core-api)                         | cardano-client-core-api             | Contains core apis, model classes and utils <br> **Dependencies:** common, transaction-spec                                                                                                                                                         |
| [plutus](plutus)                             | cardano-client-plutus               | Provides plutus specific apis and serialization implementation. <br> **Dependencies:** common, common-spec                                                                                                                                          |
| [annotation-processor](annotation-processor) | cardano-client-annotation-processor | Annotation processor for POJO <--> PlutusData. <br> **Dependencies:** plutus                                                                                                                                                                        |
| [quicktx](quicktx)                           | cardano-client-quicktx              | Provides QuickTx TX builder APIs. A declarative API to build and submit transactions. <br> **Dependencies:** core, function, backend                                                                                                                |
 | [function](function)                         | cardano-client-function             | Provides Composable Function Apis. A simple, flexible way to build transactions through re-usable functions. <br> **Dependencies:** core                                                                                                            |
 | [coinselection](coinselection)               | cardano-client-coinselection        | Provides Coin selection algorithms. <br> **Dependencies:** common, core-api                                                                                                                                                                         |
| [cip](cip)                                   | cardano-client-cip                  | A umbrella module which provides a simple way to get available cip implementations (cip25, cip8 etc.) <br> **Dependencies:** cip8, cip20, cip25, cip27, cip30                                                                                       |
| [cip8](cip/cip8)                             | cardano-client-cip8                 | [CIP 8 - Message Signing](https://cips.cardano.org/cips/cip8/) <br> **Dependencies:** common, crypto                                                                                                                                                |
| [cip20](cip/cip20)                           | cardano-client-cip20                | [CIP 20 - Transaction message/comment metadata](https://cips.cardano.org/cips/cip20/) <br> **Dependencies:** metadata                                                                                                                               |
| [cip25](cip/cip25)                           | cardano-client-cip25                | [CIP 25 - Media NFT Metadata Standard](https://cips.cardano.org/cips/cip25/) <br> **Dependencies:** metadata                                                                                                                                        |
| [cip27](cip/cip27)                           | cardano-client-cip27                | [CIP 27 - CNFT Community Royalties Standard](https://cips.cardano.org/cips/cip27/) <br> **Dependencies:** cip25                                                                                                                                     |
| [cip30](cip/cip30)                           | cardano-client-cip30                | [CIP 30 - Cardano dApp-Wallet Web Bridge](https://cips.cardano.org/cips/cip30/) <br> **Dependencies:** cip8, core    <br/>                                                                                                                          |
| [cip67](cip/cip67)                           | cardano-client-cip67                | [CIP 67 - Asset Name Label Registry](https://cips.cardano.org/cips/cip67/) <br> **Dependencies:** crypto, common                                                                                                              | 
| [cip68](cip/cip68)                           | cardano-client-cip68                | [CIP 68 - Datum Metadata Standard](https://cips.cardano.org/cips/cip68/) <br> **Dependencies:** cip67, cip25, plutus, transaction-spec                                                                                                              | 
| [ogmios-supplier](supplier/ogmios-supplier)           | cardano-client-ogmios-supplier      | Provides Ogmios specific implementation for ProtocolParamsSupplier and TransactionProcessor.                                                                                                                                                        |
| [kupo-supplier](supplier/kupo-supplier)               | cardano-client-kupo-supplier        | Provides Kupo specific implementation for UtxoSupplier.                                                                                                                                                                                             |

## Use as a library in a Java Project

### For release binaries

**For Maven, add the following dependencies to project's pom.xml**
  
- Core module

```xml
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>0.5.1</version>
        </dependency>
```
- Backend modules
    - For backend support, use one of the following supported backend module

```xml
        <!-- For Blockfrost backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>0.5.1</version>
        </dependency>
        
         <!-- For Koios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-koios</artifactId>
            <version>0.5.1</version>
        </dependency>
        
         <!-- For Ogmios / Kupo backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-ogmios</artifactId>
            <version>0.5.1</version>
        </dependency>
```

**For Gradle, add the following dependencies to build.gradle**

- Core Module
```
implementation 'com.bloxbean.cardano:cardano-client-lib:0.5.1'
```
- Backend modules
    - For backend support, use one of the following supported backend module

```groovy
//For Blockfrost
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.5.1'

//For Koios
implementation 'com.bloxbean.cardano:cardano-client-backend-koios:0.5.1'

//For Ogmios / Kupo
implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:0.5.1'

```


### For snapshot binaries

**SNAPSHOT_VERSION :** 0.5.2-SNAPSHOT (Please verify the latest snapshot version in gradle.properties)

- For Maven, add the following dependencies and repository to project's pom.xml
```
    <dependencies>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>{SNAPSHOT_VERSION}</version>
        </dependency>
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>{SNAPSHOT_VERSION}</version>
        </dependency>
    </dependencies>
    
    <repositories>
        <repository>
            <id>snapshots-repo</id>
            <url>https://oss.sonatype.org/content/repositories/snapshots</url>
            <releases>
                <enabled>false</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```
- For Gradle, add the following dependencies and repository to build.gradle

```
repositories {
    ...
    maven {
        url "https://oss.sonatype.org/content/repositories/snapshots"
    }
}

implementation 'com.bloxbean.cardano:cardano-client-lib:{SNAPSHOT_VERSION}'
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:{SNAPSHOT_VERSION}'
```

### Usages
This section highlights few key apis in the library. For detailed documentation, please visit  [Cardano Client Doc site](https://cardano-client.dev/).

#### Account API Usage

- Create a New Account

```
Account account = new Account();   //Create a Mainnet account

Account account = new Account(Networks.mainnet());   //Create a Mainnet account

Account account = new Account(Networks.testnet());  //Create a Testnet account
```
- Get base address, enterprise address, mnemonic
```
String baseAddress = account.baseAddress();  //Base address at index=0

String enterpriseAddress = account.account.enterpriseAddress();  //Enterprise address at index = 0

String mnemonic = account.mnemonic();  //Get Mnemonic
```

- Get Account from Mnemonic

```
String mnemonic = "...";
Account account = new Account(mnemonic);  //Create a Mainnet account from Mnemonic

Account account = new Account(Networks.testnet(), mnemonic); //Create a Testnet account from Mnemonic
```

#### Create Backend Service
```
//For Blockfrost
BackendService backendService =
                new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, <BF_PROJECT_ID>);               

// For Koios
// BackendService backendService = new KoiosBackendService(KOIOS_TESTNET_URL);
        
FeeCalculationService feeCalculationService = backendService.getFeeCalculationService();
TransactionHelperService transactionHelperService = backendService.getTransactionHelperService();
TransactionService transactionService = backendService.getTransactionService();
BlockService blockService = backendService.getBlockService();
AssetService assetService = backendService.getAssetService();
UtxoService utxoService = backendService.getUtxoService();
MetadataService metadataService = backendService.getMetadataService();
EpochService epochService = backendService.getEpochService();
AddressService addressService = backendService.getAddressService();
```

#### Simple ADA Payment using QuickTx Api

```java
        Tx tx1 = new Tx()
                .payToAddress(receiver1Addr, Amount.ada(1.5))
                .payToAddress(receiver2Addr, Amount.ada(2.5))
                .attachMetadata(MessageMetadata.create().add("This is a test message 2"))
                .from(sender1Addr);

        Tx tx2 = new Tx()
                .payToAddress(receiver2Addr, Amount.ada(4.5))
                .from(sender2Addr);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Result<String> result = quickTxBuilder
                .compose(tx1, tx2)
                .feePayer(sender1Addr)
                .withSigner(SignerProviders.signerFrom(sender1))
                .withSigner(SignerProviders.signerFrom(sender2))
                .completeAndWait(System.out::println);
        
```

#### Simple ADA Payment using Composable Functions Api

```java
        // Define expected Outputs
        Output output1 = Output.builder()
                .address(receiverAddress1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(10))
                .build();

        Output output2 = Output.builder()
                .address(receiverAddress2)
                .assetName(LOVELACE)
                .qty(adaToLovelace(20))
                .build();

        // Create a CIP20 message metadata
        MessageMetadata metadata = MessageMetadata.create()
                .add("First transfer transaction");

        // Define TxBuilder
        TxBuilder txBuilder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(metadataProvider(metadata))
                .andThen(balanceTx(senderAddress, 1));
        
        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

        //Build and sign the transaction
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(txBuilder, signerFrom(senderAccount));

        //Submit the transaction
        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
```

#### ScriptHash
```
Example: 1

ScriptPubkey scriptPubkey = new ScriptPubkey("ad7a7b87959173fc9eac9a85891cc93892f800dd45c0544128228884")
String policyId = scriptPubkey.getPolicyId();

Example: 2

ScriptPubkey scriptPubkey1 = ...;
SecretKey sk1 = ...;

ScriptPubkey scriptPubkey2 = ...;
SecretKey sk2 = ...;

ScriptPubkey scriptPubkey3 = ...;
SecretKey sk3 = ...;

ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

String policyId = scriptAtLeast.getPolicyId();

```

#### Metadata
```
MetadataMap productDetailsMap = MetadataBuilder.createMap()
                .put("code", "PROD-800")
                .put("slno", "SL20000039484");

MetadataList tagList = MetadataBuilder.createList()
                .add("laptop")
                .add("computer");

Metadata metadata = MetadataBuilder.createMetadata()
                .put(new BigInteger("670001"), productDetailsMap)
                .put(new BigInteger("670001"), productDetailsMap);
```

#### UtxoSelectionStrategy in High Level Api
The utxo selection strategy can be changed by providing a custom implementation of ``UtxoSelectionStrategy`` interface. By default,
the transaction builder apis use ``DefaultUtxoSelectionStrategyImpl`` which finds all required utxos sequentially. But it may not be efficient for some use cases.

You can use a custom or different implementation of ``UtxoSelectionStrategy`` to change the default utxo selection behaviour. 
Out-of-box, the library provides two additional implementations of UtxoSelectionStrategy 
   - LargestFirstUtxoSelectionStrategy 
   - RandomImproveUtxoSelectionStrategy


#### UtxoSupplier, ProtocolPramsSupplier

You can get UtxoSupplier and ProtocolParamsSupplier from the backend service.
Alternatively, you can create your own UtxoSupplier, ProtocolParamsSupplier and use it in the transaction builder api.

```
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(epochService);
```

## Build from source and run integration tests

Prerequisites: Java 11 

#### Build 
```
git clone https://github.com/bloxbean/cardano-client-lib.git

export JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
./gradlew clean build
```

#### Integration Tests
```
export BF_PROJECT_ID=<Blockfrost Preprod network Project Id>
./gradlew :integration-test:integrationTest -PBF_PROJECT_ID=${BF_PROJECT_ID}
```

# Used by
* [EASY1 Stake Pool Raffles](https://raffles.easystaking.online)
* [Cardanotales](https://cardanotales.com)
* [Cardano Fans Raffles](https://cardano.fans)
* [Cardano Fans Donation App](https://github.com/Cardano-Fans/crfa-cardano-donation-app)
* [adadomains.io](https://www.adadomains.io/)
* [ADAM - ADA Monitor APP](https://play.google.com/store/apps/details?id=com.esodot.andro.monitor)
* [ISR - Israeli Cardano Community](https://www.cardano-israel.com/)
* [MusicBox - CNFT Project](https://www.musicboxnft.com/)
* [Realfi.info - Portfolio Viewer](https://realfi.info)
* [nftio.io - NFT Explorer](https://nftio.io)

# Any questions, ideas or issues?

- Create a Github [Discussion](https://github.com/bloxbean/cardano-client-lib/discussions)
- Create a Github [Issue](https://github.com/bloxbean/cardano-client-lib/issues)
- [Discord Server](https://discord.gg/JtQ54MSw6p)

# Sponsors :sparkling_heart:
<p align="center">
 </p>

# Previous Sponsors :sparkling_heart:

<p >&nbsp;
  <a href="https://github.com/blockfrost"><img src="https://avatars.githubusercontent.com/u/70073210?s=45&v=4" width=45 height=45 /></a>
  <a href="https://github.com/KtorZ"><img src="https://avatars.githubusercontent.com/u/5680256?s=45&v=4" width=45 height=45 /></a>
  <a href="https://github.com/djcyr"><img src="https://avatars.githubusercontent.com/u/9329514?s=70&v=4" width="45" height="45" /></a>
</p>

##### If this project helps you reduce time to develop on Cardano or if you just want to support this project :

[Support this project](https://cardano-client.dev/docs/support-this-project)

# Support from YourKit

YourKit has generously granted the BloxBean projects an Open Source licence to use their excellent Java Profiler.

![YourKit](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.
