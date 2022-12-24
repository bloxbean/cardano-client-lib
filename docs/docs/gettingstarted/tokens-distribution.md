---
description: Token Distribution example.
sidebar_label: Simple Token Distribution
sidebar_position: 6

---
# Simple Token Distribution

# Overview

In this section, we will go through the steps required to do a simple token distribution from a
sender account to 5 receiver addresses.

## Select a Network and Provider

First we need to select a network for our transaction. You can choose one of the available public test network.

- **Preprod**
- **Preview**

Similarly, choose a backend provider to interact with Cardano blockchain. You can select either Koios or Blockfrost as
backend provider.

Please check [dependencies](dependencies.md) page to find the required dependency for your selected backend provider.

:::info
For **Blockfrost** as backend provider, you need to first create an account on [blockfrost.io](https://blockfrost.io) and get
a ``Project Id`` for the selected network.

For **Koios** backend provider, you don't need any registration.

:::

## Create a Sender account

We need only one sender account for this example. As we are going to use one of the test
network, the following code will generate one testnet address.

```java
Account senderAccount = new Account(Networks.testnet());
String senderAddress = senderAccount.baseAddress();
String senderMnemonic = senderAccount.mnemonic();
```

If you already have mnemonic for an existing account, you can create a sender account from the mnemonic. For this example,
we just need sender account's mnemonic.

```java
String senderMnemonic = "<24 words mnemonic>";
Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
```

The Account created need to have some Fungible Tokens so we can distribute them to other addresses.
In This Example let's assume we have some Charlie3 Tokens already in possession in our Sender Account Base Address.
Unlike other account-based blockchains, Cardano supports multiple outputs in a single transaction.
So let's define a csv file with our receiving addresses and amount of tokens per address.

```csv
addr_test1qxz3zrldxkkwlawrdx2w8ycsnz578r5nrrj96d7e2na9n32cd7gh928plcchqknd3duaxpl5zu86g5gqkd3npv58vvgs8tgtk0,114.0
addr_test1q8ggrexl20slswsnlrct7wm4uhl48eu563rkl75sv3453murys96l34r0lvxd5576q7w806fd8qq3swv45hka0uehkls4vxjka,547.2
addr_test1qyx6htpm9smwvg2w3f4eldtpq3p5ty38perhaafw86gfhgqa4dnr2pglwk0wgejy788uss82tkfxs78qnd0uleds7a9qkadf08,91.2
addr_test1qxzgdkjhepeytjyls3u2ed2x9cfvd4pm40lwsjgk53hm0n7m9j088cqfhvm934lnlsprhwq2c3ha4hl72cs3ul057p2swdlz5u,15.2
addr_test1q9hp9mja7fjyh9hy8mkstn6uuxtn98z3fgryy75qh8hpmhp3hdmcfrjy06m5f7ht8mecgegjt8jerm6l8jwdxcvjuxgsl907rj,15.2
```

## Create Backend Service

**For Blockfrost :**

Use the correct Blockfrost url for the selected network and project id to create an instance of BackendService.

```java
String bfProjectId = "preprod...";
BackendService backendService =
        new BFBackendService(Constants.BLOCKFROST_PREPROD_URL, bfProjectId);
```
**Note:** You can find Blockfrost urls for the supported networks in ``com.bloxbean.cardano.client.backend.blockfrost.common.Constants``.

or,

**For Koios :**

```java
BackendService backendService = new KoiosBackendService(KOIOS_TESTNET_URL);
```
**Note:** You can find other Koios urls in ``com.bloxbean.cardano.client.backend.koios.Constants``

## Simple Transfer - Using Composable Functions

Let's start with a brief introduction about composable functions.

### Composable function ?

A set of ``FunctionalInterface`` which can be used to implement composable functions. These functions
can be used to build various different types of transactions. The library provides many useful out-of-box implementations of these functions
to reduce boilerplate code. You can also write your own function and use it with existing functions.

The followings are the main FunctionalInterface

- TxBuilder
- TxOutputBuilder
- TxInputBuilder
- TxSigner

**TxBuilder :** This functional interface helps to transform a transaction object. The ``apply`` method in this interface takes
a ``TxBuilderContext`` and a ``Transaction`` object as input parameters. The role of this function is to transform the input transaction
object with additional attributes or update existing attributes.

**TxOutputBuilder :** This functional interface helps to build a ``TransactionOutput`` object and add that to the transaction output list.
The accept method in this interface takes a ``TxBuilderContext`` and a list of ``TransactionOutput``.

**TxInputBuilder :** This functional interface is responsible to build inputs from the expected outputs.

**TxSigner :** This interface is responsible to provide transaction signing capability.

**Now we have everything to build and submit our first transfer transaction.**

### Define Expected Outputs

First we need to define the expected output. Let's define our TxOutputBuilder to read our csv file and add all accumulate all outputs.

```java
TxOutputBuilder txOutputBuilder = (txBuilderContext, list) -> {};
Scanner scanner = new Scanner(getFileFromResource("file1.csv"));
String policyId = "8e51398904a5d3fc129fbf4f1589701de23c7824d5c90fdb9490e15a";
String assetName = "434841524c4933";
while (scanner.hasNextLine()) {
	String line = scanner.nextLine();
	String[] parts = line.split(",");
	System.out.println(parts[0]);
	System.out.println(parts[1]);

	Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(policyId+assetName);
	Output output = Output.builder()
	.address(parts[0])
	.qty((new BigDecimal(parts[1]).multiply(BigDecimal.valueOf(1000000.0))).toBigInteger())
	.policyId(policyAssetName._1)
	.assetName(policyAssetName._2).build();

	txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
}
```

:::info
Note:
We Multiplied the quantity Value by the amount of decimals this specific token is registered under.
in this case, for Charlie3 Token it is 6 zeros after the decimal point, Hence, we multiplied the quantity by a million.
:::

### Define transaction using TxBuilder and out-of-box composable functions

**Line-1** Create ``TxBuilder`` from ``txOutputBuilder``.

**Line-2,** Invoke ``TxOutputBuilder.buildInputs`` with a ``TxInputBuilder`` function. ``TxInputBuilder`` function builds required
inputs based on the expected outputs.

You can select an appropriate composable function from ``InputBuilders`` helper class to get a ``TxInputBuilder``. In the below example,
``InputBuilders.createFromSender(String sender, String changeAddress)`` out-of-box composable function is used.

**Line-3,** Use ``FeeCalculators.feeCalculator(senderAddress, 1)`` composable function to calculate fee needed in this transaction.

**Line-4,** Use ``ChangeOutputAdjustments.adjustChangeOutput(senderAddress, 1)`` composable function to adjust change output in a Transaction to meet min ada requirement.

```java showLineNumbers
TxBuilder builder = txOutputBuilder
				.buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
				.andThen(FeeCalculators.feeCalculator(senderAddress, 1))
				.andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress, 1));
```

### Build and Sign the transaction

**Line-1 & Line-2,** Create ``UtxoSupplier`` & ``ProtocolParamsSupplier`` from the ``BackendService`` instance.

**Line-4,** Initialize ``TxBuilderContext`` using ``UtxoSupplier`` and ``ProtocolParamsSupplier``.

:::info

Using ``TxBuilderContext`` you can customize few behaviors during transaction building.

**For example:** Select a different ``UtxoSelectionStrategy`` implementation

:::

**Line-5,** Create ``TxSigner`` function using ``SignerProviders.signerFrom(Account... signers)`` and use it to build
and sign the transaction.

```java showLineNumbers
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                                    .buildAndSign(txBuilder, signerFrom(senderAccount));
```

### Submit the transaction to Cardano network

Now we are ready to submit the transaction to the network. In this example, we are going to submit this transaction through
``BackendService``. Alternatively, you can submit the generated transaction using your own ``TransactionProcessor`` implementation.

```java
Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);
```

If successful, ``result.isSuccessful()`` will return true.

### Full Source Code

```java showLineNumbers
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.*;
import com.bloxbean.cardano.client.function.helper.ChangeOutputAdjustments;
import com.bloxbean.cardano.client.function.helper.FeeCalculators;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.Tuple;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;

public class Main {

    private final BackendService backendService;
    private final TransactionService transactionService;
    private final UtxoSupplier utxoSupplier;
    private final ProtocolParams protocolParams;

    public Main() throws ApiException {
        backendService = new KoiosBackendService(Constants.KOIOS_MAINNET_URL);
        transactionService = backendService.getTransactionService();
        utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
    }

    public static void main(String[] args) throws CborSerializationException, ApiException, FileNotFoundException, URISyntaxException {
        Main mai = new Main();
        mai.transferMultiAssetMultiPayments_whenSingleSender_multipleToken();
    }

    public void transferMultiAssetMultiPayments_whenSingleSender_multipleToken() throws FileNotFoundException, URISyntaxException, CborSerializationException, ApiException {
        String senderMnemonic = "mnemonic"; //TODO Seed Phrase
        Account sender = new Account(Networks.mainnet(), senderMnemonic);
        String senderAddress = sender.baseAddress();
        TxOutputBuilder txOutputBuilder = (txBuilderContext, list) -> {};

        //System.out.println(senderAddress);

        Scanner scanner = new Scanner(getFileFromResource("file1.txt"));
        String policyId = "8e51398904a5d3fc129fbf4f1589701de23c7824d5c90fdb9490e15a";
        String assetName = "434841524c4933";
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] parts = line.split(",");
            System.out.println(parts[0]);
            System.out.println(parts[1]);

            Tuple<String, String> policyAssetName = AssetUtil.getPolicyIdAndAssetName(policyId+assetName);
            Output output = Output.builder()
                    .address(parts[0])
                    .qty((new BigDecimal(parts[1]).multiply(BigDecimal.valueOf(1000000.0))).toBigInteger())
                    .policyId(policyAssetName._1)
                    .assetName(policyAssetName._2).build();

            txOutputBuilder = txOutputBuilder.and(output.outputBuilder());
        }

        TxBuilder builder = txOutputBuilder
                .buildInputs(InputBuilders.createFromSender(senderAddress, senderAddress))
                .andThen(FeeCalculators.feeCalculator(senderAddress, 1))
                .andThen(ChangeOutputAdjustments.adjustChangeOutput(senderAddress, 1));

        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier,protocolParams)
                .buildAndSign(builder, signerFrom(sender));

        System.out.println(signedTransaction);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

        waitForTransaction(result);
    }

    public void waitForTransaction(Result<String> result) {
        try {
            if (result.isSuccessful()) { //Wait for transaction to be mined
                int count = 0;
                while (count < 60) {
                    Result<TransactionContent> txnResult = transactionService.getTransaction(result.getValue());
                    if (txnResult.isSuccessful()) {
                        System.out.println(JsonUtil.getPrettyJson(txnResult.getValue()));
                        break;
                    } else {
                        System.out.println("Waiting for transaction to be mined ....");
                    }

                    count++;
                    Thread.sleep(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getFileFromResource(String fileName) throws URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            // failed if files have whitespaces or special characters
            //return new File(resource.getFile());
            return new File(resource.toURI());
        }
    }
}
```

## Simple Token Distribution - Using High Level Api

//TODO

## Simple Token Distribution - Using Low Level Api

//TODO
