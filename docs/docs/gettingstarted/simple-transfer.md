---
description: A simple ada transfer example.
sidebar_label: Simple Ada Transfer
sidebar_position: 4

---
# Simple Ada Transfer 

# Overview

In this section, we will go through the steps required to do a simple Ada transfer from a
sender account to two receiver addresses.

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

## Create Sender and Receiver accounts

We need three accounts for this example, a sender account and two receiver accounts. As we are going to use one of the test
network, the following code will generate three testnet addresses.

```java
Account senderAccount = new Account(Networks.testnet()); 
String senderAddress = senderAccount.baseAddress();
String senderMnemonic = senderAccount.mnemonic();

Account receiverAccount1 = new Account(Networks.testnet());
String receiverAddress1 = receiverAccount1.baseAddress();

Account receiverAccount2 = new Account(Networks.testnet());
String receiverAddress2 = receiverAccount2.baseAddress();

```

If you already have mnemonic for an existing account, you can create a sender account from the mnemonic. For this example,
we just need sender account's mnemonic.

```java
String senderMnemonic = "<24 words mnemonic>";
Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
```

Similarly, we need two receiver addresses to receive some ada. Unlike other account-based blockchains, Cardano supports multiple outputs
in a single transaction. So let's define two receiving addresses.

```java
String receiverAddress1 = "addr_test...";
String receiverAddress2 = "addr_test...";
```

:::info

Two types of address can be generated, mainnet address or testnet address.

To generate a test network address, you can use any of the network constant ``Networks.testnet()``, ``Networks.preprod()`` or
``Networks.preview()``. The generated testnet address can be used on any of the test network. 
**(The address generation depends on the NetworkId in Network object not protocol magic. These public test networks have same network id (0))**

For mainnet address, you need to use ``Networks.mainnet()``

:::


## Topup sender account with test Ada

Based on your selected network, get some test Ada from one of the below faucet. You need to provide ``senderAddress`` 
generated in the previous section to get some test Ada.

https://docs.cardano.org/cardano-testnet/tools/faucet

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

First we need to define the expected output. Let's say we want to send 10 Ada to **receiverAddress1** and 20 Ada to **receiverAddress2**.

```java 
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
```

Imports:

```java
import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
```

### Create a transaction message metadata
Let's create a [CIP20](https://cips.cardano.org/cips/cip20/) complaint metadata to add some random message to the transaction.
This is not mandatory, but we will use this opportunity to see how a metadata can be added to a transaction.

```java
MessageMetadata metadata = MessageMetadata.create()
                .add("First transfer transaction");
```


### Define transaction using TxBuilder and out-of-box composable functions

**Line-1, Line-2 ** Create ``TxOutputBuilder`` from ``output1`` and compose it with another ``TxOutputBuilder`` generated from ``output2``.

:::info
**Note:** Check out various helper methods in ``com.bloxbean.cardano.client.function.helper.OutputBuilders`` to create ``TxOutputBuilder``.

:::

**Line-3,** Invoke ``TxOutputBuilder.buildInputs`` with a ``TxInputBuilder`` function. ``TxInputBuilder`` function builds required
inputs based on the expected outputs. 

You can select an appropriate composable function from ``InputBuilders`` helper class to get a ``TxInputBuilder``. In the below example, 
``InputBuilders.createFromSender(String sender, String changeAddress)`` out-of-box composable function is used.


**Line-4,** Use ``AuxDataProviders.metadataProvider(metadata)`` composable function to add metadata.

**Line-5,** Use ``BalanceTxBuilders.balanceTx`` composable function to balance the unbalanced transaction. 
It handles the followings to balance a transaction

- Fee calculation
- Adjust the outputs (if required)

```java showLineNumbers
TxBuilder txBuilder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .buildInputs(createFromSender(senderAddress, senderAddress))
                .andThen(metadataProvider(metadata))
                .andThen(balanceTx(senderAddress, 1));
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
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.cip.cip20.MessageMetadata;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.helper.InputBuilders;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.AuxDataProviders.metadataProvider;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;

public class SimpleTransfer {

    public void transfer() throws Exception {
        //Sender account
        String senderMnemonic = "<24 words mnemonic>";
        Account senderAccount = new Account(Networks.testnet(), senderMnemonic);
        String senderAddress = senderAccount.baseAddress();

        //Addresses to receive ada
        String receiverAddress1 = "addr_test1qpjs693nk7makhcax3k7h0hkjyye2adwv3e300dkfwpqj8k2le4j5lg6gd773gdvs7jcnwdxvtztmxawwcdmvm0h870sardwde";
        String receiverAddress2 = "addr_test1qzvy33rr24huuqv46ajex99hrcl0dauqcch7meznf4mdyd4sqwzjy5gaynruuwtdmwmdlnasa8t2g2t0fqmf8rhq3e6svxzum4";

        // For Blockfrost
        String bf_projectId = "<Blockfrost Project Id>";
        BackendService backendService =
                new BFBackendService(Constants.BLOCKFROST_PREVIEW_URL, bf_projectId);

        // For Koios
        // BackendService backendService = new KoiosBackendService(KOIOS_TESTNET_URL);

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
        System.out.println(result);
    }

    public static void main(String[] args) throws Exception {
        new SimpleTransfer().transfer();
    }
}

```

## Simple Transfer - Using High Level Api

//TODO

## Simple Transfer - Using Low Level Api

//TODO
