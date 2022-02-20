# cardano-client-lib 

A client library for Cardano in Java. This library simplifies the interaction with Cardano blockchain from a Java application.

**Latest Beta**: [0.2.0-beta2](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.2.0-beta2) (Pure Java)

**Latest Stable Version** : [0.1.5](https://github.com/bloxbean/cardano-client-lib/releases/tag/v0.1.5)

**Note:** In 0.1.5 or earlier versions, it uses a rust module though JNI. The library
bundles the platform specific binaries of this rust module. You can check the supported operating systems below.

## Supported Operating Systems (For 0.1.5 or earlier and 0.2.0-beta1)
The library has been tested on the following Operating Systems. 

- Apple MacOS (Intel and Apple Silicon)
- Linux (x86_64) (Ubuntu 18.04 and above or compatible ...)
- Windows 64bits (x86_64)
- Android (both x86 and ARM in 32-bit and 64-bit)

**Posts**
- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537)
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e)
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb)

**Examples**

[Cardano-client-lib examples repository](https://github.com/bloxbean/cardano-client-examples/)

[JavaDoc](https://javadoc.io/doc/com.bloxbean.cardano/cardano-client-lib/0.2.0-beta2/index.html)

**Features**

#### Address Generation

- Address Generation (Base Address, Enterprise Address)
- Generate Address from Mnemonic phase

#### Transaction Serialization & Signing
- API to build Payment transaction (ADA & Native Tokens) 
- CBOR serialization of transaction
- Transaction signing

#### Metadata Builder
- Helper to build Metadata
- Converter to conver JSON (No Schema) to Metadata format

#### Token Minting
- Token Minting transaction builder
- Native script (ScriptAll, ScriptAny, ScriptAtLeast, ScriptPubKey, RequireTimeAfter, RequireTimeBefore)
- Policy Id generation

#### Backend Integration
The plugin also provides integration with Cardano node through different backend services. 
Out of box, the library currently supports integration with [Blockfrost](https://blockfrost.io) through the Backend api. 

For cardano-graphql backend, check [cardano-client-backend-gql](https://github.com/bloxbean/cardano-client-backend-gql) extension.

Other backend like Cardano-wallet will be added in future release.


**Following Backend apis are currently available**
- TransactionService (Submit transaction, Get transaction)
- AddressService (Get address details)
- UtxoService (Get utxos for an address)
- AssetService
- BlockService
- NetworkInfoService
- EpochService
- MetadataService

## Use as a library in a Java Project

### Add dependency

- For Maven, add the following dependency to project's pom.xml
```
        <dependency>
            <groupId>com.bloxbean.cardano</groupId>
            <artifactId>cardano-client-lib</artifactId>
            <version>0.2.0-beta2</version>
        </dependency>
```

- For Gradle, add the following dependency to build.gradle

```
compile 'com.bloxbean.cardano:cardano-client-lib:0.1.5'
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
                BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, "<Blockfrost_Project_id>");

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

You can create a custom implementation of UtxoSelectionStrategy to change the default utxo selection behaviour. The custom UtxoSelectionStrategy impl can then be set in the UtxoTransactionBuilder through TransactionHelperService.

```
UtxoSelectionStrategy customUtxoSelectionStrategy = new CustomUtxoSelectionStrategyImpl(utxoService); //Your custom impl
transactionHelperService.getUtxoTransactionBuilder().setUtxoSelectionStrategy(customUtxoSelectionStrategy);
```

# Build

```
git clone https://github.com/bloxbean/cardano-client-lib.git

./gradlew build fatJar
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
