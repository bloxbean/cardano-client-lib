---
description: Key apis of Cardano Client Lib
sidebar_label: Key Apis
sidebar_position: 8
---

# Key Apis

## Account API Usage

### Create a New Account

```
Account account = new Account();   //Create a Mainnet account

Account account = new Account(Networks.mainnet());   //Create a Mainnet account

Account account = new Account(Networks.testnet());  //Create a Testnet account
```
### Get base address, enterprise address, mnemonic
```
String baseAddress = account.baseAddress();  //Base address at index=0

String enterpriseAddress = account.account.enterpriseAddress();  //Enterprise address at index = 0

String mnemonic = account.mnemonic();  //Get Mnemonic
```

### Get Account from Mnemonic

```
String mnemonic = "...";
Account account = new Account(mnemonic);  //Create a Mainnet account from Mnemonic

Account account = new Account(Networks.testnet(), mnemonic); //Create a Testnet account from Mnemonic
```

## Create Blockfrost Backend Service and get other services
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

## Simple ADA Payment (Composable functions)
```java
//Define expected outputs
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

## Simple ADA Payment transaction (High Level Api)
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
## Native Token transfer
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

## ScriptHash
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
## Token Minting transaction
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
## Metadata
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
