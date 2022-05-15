<div align="center">
        <h1 align="center" style="border-bottom: none">Cardano Client Lib</h1>

[![Clean, Build](https://github.com/bloxbean/cardano-client-lib/actions/workflows/build.yml/badge.svg)](https://github.com/bloxbean/cardano-client-lib/actions/workflows/build.yml)
[![CodeQL](https://github.com/bloxbean/cardano-client-lib/actions/workflows/codeql.yml/badge.svg)](https://github.com/bloxbean/cardano-client-lib/actions/workflows/codeql.yml)
[![License](https://img.shields.io:/github/license/bloxbean/cardano-client-lib?color=blue&label=license)](https://github.com/bloxbean/cardano-client-lib/blob/master/LICENSE)

</div>

A client library for Cardano in Java. This library simplifies the interaction with Cardano blockchain from a Java application.

**Latest Stable Version**: [0.2.0](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.2.0)

**Posts**
- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537)
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e)
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb)
- [Composable functions to build transactions](https://medium.com/coinmonks/cardano-client-lib-new-composable-functions-to-build-transaction-in-java-part-i-be3a8b4da835)

**Examples**

[Cardano-client-lib examples repository](https://github.com/bloxbean/cardano-client-examples/)

[JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-lib/0.2.0-beta3/index.html)

**Features**

#### Address Generation

- Address Generation (Base Address, Enterprise Address)
- Generate Address from Mnemonic phase

#### Transaction Serialization & Signing
- API to build Payment transaction (ADA & Native Tokens)
- CBOR serialization of transaction
- Transaction signing

#### High Level api 
- To build and submit 
    -  Payment transaction
    - Token Minting and token transfer transaction
    
#### Composable Functions
- To build and submit
    - Payment transaction
    - Token Minting and token transfer
    - Plutus smart contract call
    - Token minting with Plutus contract

[Examples with Composable Functions](https://github.com/bloxbean/cardano-client-examples/tree/main/src/main/java/com/bloxbean/cardano/client/examples/function)

#### CIP Implementations
- [CIP20 - Transaction Message/Comment metada](https://cips.cardano.org/cips/cip20/)
- [CIP25 - NFT Metadata Standard](https://cips.cardano.org/cips/cip25/)
- [CIP8  - Message Signing](https://cips.cardano.org/cips/cip8/)
- [CIP30  - dApp signData & verify](https://cips.cardano.org/cips/cip30/)

#### Metadata Builder
- Helper to build Metadata
- Converter to conver JSON (No Schema) to Metadata format

#### Token Minting
- Token Minting transaction builder
- Native script (ScriptAll, ScriptAny, ScriptAtLeast, ScriptPubKey, RequireTimeAfter, RequireTimeBefore)
- Policy Id generation

#### Backend Integration
The library also provides integration with Cardano node through different backend services.
Out of box, the library currently supports integration with following providers through the Backend api.

- [Blockfrost](https://blockfrost.io) 
    - **Module :** cardano-client-backend-blockfrost [README](backend-modules/blockfrost/README.md)
    - **Status :** Stable 
- [Koios](https://www.koios.rest/)
    - **Module :** cardano-client-backend-koios [README](backend-modules/koios/README.md)
    - **Status :** Beta
- [Ogmios](https://ogmios.dev/)
    - **Module :** cardano-client-backend-koios [README](backend-modules/ogmios/README.md)
    - **Status :** Experimental
    - **Supported Apis :** submitTransaction, evaluateTx
- [cardano-graphql](https://github.com/input-output-hk/cardano-graphql)
    - **Module :** cardano-client-backend-gql [README](backend-modules/cardano-graphql/README.md)
    - **Status :** Deprecated

**Following Backend apis are currently available**
- TransactionService (Submit transaction, Get transaction, Evaluate ExUnits for Script Txn)
- AddressService (Get address details)
- UtxoService (Get utxos for an address)
- AssetService
- BlockService
- NetworkInfoService
- EpochService
- MetadataService

## Use as a library in a Java Project

### For release binaries

**For Maven, add the following dependencies to project's pom.xml**
  
- Core module

```xml
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>0.2.0</version>
        </dependency>
```
- Backend modules
    - For backend support, use one of the following supported backend module

```xml
        <!-- For Blockfrost backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-blockfrost</artifactId>
            <version>0.2.0</version>
        </dependency>
        
         <!-- For Koios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-koios</artifactId>
            <version>0.2.0</version>
        </dependency>
        
         <!-- For Ogmios backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-ogmios</artifactId>
            <version>0.2.0</version>
        </dependency>

        <!-- For Cardano Graphql backend -->
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-backend-gql</artifactId>
            <version>0.2.0</version>
        </dependency>
```

**For Gradle, add the following dependencies to build.gradle**

- Core Module
```
implementation 'com.bloxbean.cardano:cardano-client-lib:0.2.0'
```
- Backend modules
    - For backend support, use one of the following supported backend module

```groovy
//For Blockfrost
implementation 'com.bloxbean.cardano:cardano-client-backend-blockfrost:0.2.0'

//For Koios
implementation 'com.bloxbean.cardano:cardano-client-backend-koios:0.2.0'

//For Ogmios
implementation 'com.bloxbean.cardano:cardano-client-backend-ogmios:0.2.0'

//For Cardano Graphql
implementation 'com.bloxbean.cardano:cardano-client-backend-gql:0.2.0'

```


### For snapshot binaries

**SNAPSHOT_VERSION :** 0.3.0-SNAPSHOT (Please verify the latest snapshot version in gradle.properties)

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

### Account API Usage

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

### Create Blockfrost Backend Service and get other services
```
BackendService backendService =
                new BFBackendService(Constants.BLOCKFROST_TESTNET_URL, <BF_PROJECT_ID>);               

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

### Simple ADA Payment transaction
```
  PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                                            .sender(sender)
                                            .receiver(receiver)
                                            .amount(BigInteger.valueOf(1500000))
                                            .unit("lovelace")
                                            .build();
          
  //Calculate Time to Live        
  long ttl = blockService.getLastestBlock().getValue().getSlot() + 1000;
  TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(ttl)
                        .build();

  //Calculate fee
  BigInteger fee
          = feeCalculationService.calculateFee(paymentTransaction, detailsParams, null);
  paymentTransaction.setFee(fee);                                        

  Result<String> result = 
                    transactionHelperService.transfer(paymentTransaction, detailsParam);

  if(result.isSuccessful())
      System.out.println("Transaction Id: " + result.getValue());
```
### Native Token transfer
```
 PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .amount(BigInteger.valueOf(12))
                        .unit("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e")
                        .build();

 //Calculate Time to Live        
  long ttl = blockService.getLastestBlock().getValue().getSlot() + 1000;
  TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(ttl)
                        .build();

  //Calculate fee
  BigInteger fee
          = feeCalculationService.calculateFee(paymentTransaction, detailsParams, null);
  paymentTransaction.setFee(fee); 
                                            
 Result<String> result = transactionHelperService.transfer(paymentTransaction, detailsParam);

 if(result.isSuccessful())
     System.out.println("Transaction Id: " + result.getValue());
```

### ScriptHash
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
### Token Minting transaction
```
MultiAsset multiAsset = new MultiAsset();
multiAsset.setPolicyId(policyId);

Asset asset = new Asset("testtoken"), BigInteger.valueOf(250000));
multiAsset.getAssets().add(asset);

MintTransaction mintTransaction = MintTransaction.builder()
                        .sender(sender)
                        .receiver(receiver)
                        .mintAssets(Arrays.asList(multiAsset))
                        .policyScript(scriptAtLeast)
                        .policyKeys(Arrays.asList(sk2, sk3))
                        .build();
                        
//Calculate Time to Live        
long ttl = blockService.getLastestBlock().getValue().getSlot() + 1000;
TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(ttl)
                        .build();

//Calculate fee
BigInteger fee
          = feeCalculationService.calculateFee(mintTransaction, detailsParams, null);
mintTransaction.setFee(fee);

Result<String> result = transactionHelperService.mintToken(mintTransaction,
                TransactionDetailsParams.builder().ttl(getTtl()).build());
```
### Metadata
```
CBORMetadataMap productDetailsMap
                = new CBORMetadataMap()
                .put("code", "PROD-800")
                .put("slno", "SL20000039484");

CBORMetadataList tagList
                = new CBORMetadataList()
                .add("laptop")
                .add("computer");

Metadata metadata = new CBORMetadata()
                .put(new BigInteger("670001"), productDetailsMap)
                .put(new BigInteger("670002"), tagList);
PaymentTransaction paymentTransaction =
                PaymentTransaction.builder()
                        ...
                        .build();

long ttl = blockService.getLastestBlock().getValue().getSlot() + 1000;
TransactionDetailsParams detailsParams =
                TransactionDetailsParams.builder()
                        .ttl(ttl)
                        .build();

//Also add metadata for fee calculation
BigInteger fee = feeCalculationService.calculateFee(paymentTransaction, detailsParams, metadata);
paymentTransaction.setFee(fee);

//Send metadata as 3rd parameter
Result<String> result
                = transactionHelperService.transfer(paymentTransaction, detailsParams, metadata);
```

## UtxoSelectionStrategy
The utxo selection strategy can be changed by providing a custom implementation of "UtxoSelectionStrategy" interface. By default, the high level api like TransactionHelperService uses a default out-of-box implementation "DefaultUtxoSelectionStrategyImpl". The default strategy is too simple and finds all required utxos sequentially. But it may not be efficient for some usecases.

You can use a custom or different implementation of UtxoSelectionStrategy to change the default utxo selection behaviour. 

```
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(utxoService);
ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(epochService);

//Create TransactionHelperService with LargestFirst
TransactionBuilder transactionBuilder = new TransactionBuilder(new LargestFirstUtxoSelectionStrategy(utxoSupplier), protocolParamsSupplier);
TransactionHelperService transactionHelperService = new TransactionHelperService(transactionBuilder, new DefaultTransactionProcessor(transactionService));

//Get FeeCalculationService using the above TransactionHelperService
FeeCalculationService feeCalculationService = backendService.getFeeCalculationService(transactionHelperService);
```

# Build

```
git clone https://github.com/bloxbean/cardano-client-lib.git

export JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8
./gradlew clean build
```

# Run Integration Tests
```
export BF_PROJECT_ID=<Blockfrost Project Id>
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
---

##### If this project helps you reduce time to develop on Cardano or if you just want to support this project, you can delegate to our pool:

[BLOXB](https://www.bloxbean.com/cardano-staking/)

[Support this project](https://cardano-client.bloxbean.com/support-this-project)
