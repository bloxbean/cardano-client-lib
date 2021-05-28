# cardano-client-lib 

A client library for Cardano in Java. 
For some features like transaction signing and address generation, it currently uses [cardano-serialization-lib](https://github.com/Emurgo/cardano-serialization-lib) rust library though JNI. The library
bundles the platform specific binaries of cardano-serialization-lib. You can check the currently supported operating systems below. This dependency will be removed in the future release.

## Supported Operating Systems
The library has been tested on the following Operating Systems. 

- Apple MacOS (Intel and Apple Silicon)
- Linux (x86_64) (Ubuntu 18.04 and above or compatible ...)
- Windows 64bits (x86_64)

For anyother platform, please create a request [here](https://github.com/bloxbean/cardano-client-lib/issues)

**Posts**
- [Cardano-client-lib : A Java Library to interact with Cardano - Part I](https://medium.com/p/83fba0fee537)
- [Cardano-client-lib: Transaction with Metadata in Java - Part II](https://medium.com/p/fa34f403b90e)
- [Cardano-client-lib: Minting a new Native Token in Java - Part III](https://medium.com/p/1a94a21cfeeb)

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

#### Backend Integration (Blockfrost)
The plugin also provides integration with Cardano node through different backend services. 
The library currently only supports integration with [Blockfrost](https://blockfrost.io) through the Backend api. But other backend like Cardano-wallet
will be added in future release.

- Transaction Submission (Payment & Token Minting)

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
            <version>0.1.0</version>
        </dependency>
```

- For Gradle, add the following dependency to build.gradle

```
compile 'com.bloxbean.cardano:cardano-client-lib:0.1.0'
```

### Account API Usage

- Create a New Account

```aidl
Account account = new Account();   //Create a Mainnet account

Account account = new Account(Networks.mainnet());   //Create a Mainnet account

Account account = new Account(Networks.testnet());  //Create a Testnet account
```
- Get base address, enterprise address, mnemonic
```aidl
String baseAddress = account.baseAddress();  //Base address at index=0

String enterpriseAddress = account.account.enterpriseAddress();  //Enterprise address at index = 0

String mnemonic = account.mnemonic();  //Get Mnemonic
```

- Get Account from Mnemonic

```aidl
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
```aidl
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
```aidl
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
```aidl
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
```aidl
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

## Use as a standalone application
The library also provides some CLI utilities. Download `cardano-client-lib-all-<version>.jar` from release section.

```aidl
$> java -jar cardano-client-lib-all-<version>.jar  account generate [-ea] [-n mainnet|testnet] [-t total_no_of_accounts]
$> java -jar cardano-client-lib-all-<version>.jar  account from-mnemonic [-mn mnemonic] [-ea] [-n <mainnet|testnet>] [-t total_no_of_accounts]
   
   -ea : Also generate Enterprise address
```

Examples:
```aidl
- java -jar cardano-client-lib-all-<version>.jar account generate  //Generate a new mainnet account
- java -jar cardano-client-lib-all-<version>.jar account generate -n testnet  //Generate a new testnet account
- java -jar cardano-client-lib-all-<version>.jar account generate -ea  //Generate a new account and both Base Address and Enterprise address
- java -jar cardano-client-lib-all-<version>.jar account generate -ea -t 5  //Generate a new account and show first 5 Base Addresses and Ent Addresses
- java -jar cardano-client-lib-all-<version>.jar account from-mnemonic -mn "chimney proof dismiss ..." -t 5 //Generate first 5 mainnet addresses from the mnemonic
- java -jar cardano-client-lib-all-<version>.jar account from-mnemonic -mn "chimney proof dismiss ..." -t 5 -n testnet //Testnet accounts
```
- Generate a new Mainnet account

```aidl
$> java -jar cardano-client-lib-all-0.0.1.jar account generate

Output: 
Mnemonic  : stable fade square ...
Base Address-0: addr1q9nj6uysd93x ...
```
- Generate a new Testnet account
```aidl
$> java -jar cardano-client-lib-all-0.0.1.jar account generate -n testnet

Output:
Mnemonic  : gauge side mandate sight evoke ...
Base Address-0: addr_test1qqyc4rcuz0wwy...

```


# Build

```
git clone https://github.com/bloxbean/cardano-client-lib.git
git submodule update --init --recursive

. script/build-<os>-<arch>.sh

./gradlew build farJar
```
