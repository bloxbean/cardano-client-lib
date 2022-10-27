---
description: Example :- Multi-signature transfer with native script
sidebar_label: Multi-sig transfer with Native script
sidebar_position: 5

---

# Multi-sig Transfer using Native Script

## Overview

In this guide, we will go through an example of multi-sig transaction using native script. The example will demonstrate the
steps required to create a multi-sig script and claim fund from the script address.

Let's go through some theory first. Click [here](#simple-transfer-from-a-multi-sig-script-address) to directly jump to the example section.

## What's Native Script or Multi-Sig Script ?

A native script is a set of rules that defines how you can spend a UTxO. Native scripts are used 
to make script addresses where the authorisation condition for a transaction to use that address is that the transaction has
signatures from multiple cryptographic keys. Examples include M of N schemes, where a transaction can be authorized if at 
least M distinct keys, from a set of N keys, sign the transaction. 

Some key points about native scripts in Cardano

- A native script can be encoded as a json text file
- It uses a simple language for expressing witness requirements for spending an output, minting or burning transaction
- Keys are identified in the script by hash of public key
- It also provides support for time-locking, so the script is valid for a specific time range

The simplest native script requires just one key

**Example:** 
```shell
{
  "type": "sig",
  "keyHash": "e09d36c79dec9bd1b3d9e152247701cd0bb860b5ebfd1de8abb6735a"
}
```

### Types

There are six supported constructors in a multi-sig script. The following section briefly explains about each constructor and 
corresponding **type** in json file.

1. **RequireSignature**: has the hash of a verification key.  
     This expression evaluates to true if the transaction is signed by a particular key, identified by its verification key hash.   
     **Type:** "sig"

2. **RequireAllOf:** has a list of multisig sub-expressions.  
     This expression evaluates to true if (and only if) all the sub-expressions evaluate to true.  
     **Type:** "all"

3. **RequireAnyOf:** has a list of multisig sub-expressions.  
     This expression evaluates to true if (and only if) any the sub-expressions evaluate to true.  
     **Type:** "any"

4. **RequireMOf:** has a number M and a list of multisig sub-expressions.  
     This expression evaluates to true if (and only if) at least M of the sub-expressions evaluate to true.  
     **Type:** "atLeast"

5. **RequireTimeBefore:** has a slot number X.  
     This condition guarantees that the actual slot number in which the transaction is included is (strictly) less than slot number X.  
     **Type:** "before"

6. **RequireTimeAfter:** has a slot number X.  
     This condition guarantees that the actual slot number in which the transaction is included is greater than or equal to slot number X.  
     **Type:** "after"

You can find more info about native script [here](https://github.com/input-output-hk/cardano-node/blob/master/doc/reference/simple-scripts.md)

:::info
**Cardano Client Lib** provides apis and helpers to create and manage native scripts in Java.

:::

## Simple Transfer from a multi-sig script address 

In this example we are going to simulate a joint account use case using a multi-sig script. So a muti-sig account between three parties
which specifies that at least any two members need to sign to spend fund from the account.

In next few sections, we are going to 
- Create a multi-sig script with three key hashes (2 regular accounts, 1 payment key pair(skey & vkey))
- Create script address for the multi-sig script
- Top up multi-sig address with some fund
- Claim fund from the multi-sig address and distribute to two regular accounts

## Accounts Setup
We will use 2 regular accounts and 1 payment key pair (Ed25519 keys) for our example. 

:::info
The payment key pair is not mandatory, but it is included to demonstrate the transaction signing capability using ``SecretKey``.

:::

To spend fund from our multi-sig script address, we need to sign the transaction with at least 2 keys. Any of the below should work for our use case
- Sign with 2 regular accounts
- Sign with 1 regular account and payment secret key
- Sign with 2 regular account and payment secret key

To create two new regular accounts, check [here](account-setup.md#create-sender-and-receiver-accounts).

1. Create **account-1** and **account-2** from existing mnemonics

```java
String account1Mnemonic = "<24 words mnemonic>";
Account account1 = new Account(Networks.testnet(), account1Mnemonic);

String account2Mnemonic = "<24 words mnemonic>";
Account account2 = new Account(Networks.testnet(), account2Mnemonic);
```

2. Create a new payment key pair (Ed25519 secret key / verification key)

```java
Keys keys = KeyGenUtil.generateKey();
VerificationKey verificationKey = keys.getVkey();
SecretKey secretKey = keys.getSkey();
String paymentSigningKeyCborHex = secretKey.getCborHex();
```

**Alternatively,** you can create a ``SecretKey`` and ``VerificationKey`` from an existing payment secret key. 

```java
String paymentSigningKeyCborHex = "58205d9ccc4202bde1785708c10f8b13231d5a57078c326d0e0ff172191f975a983e";
SecretKey secretKey = new SecretKey(paymentSigningKeyCborHex);
VerificationKey verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
```

## Create Multi-sig script with 2 regular accounts and 1 payment key pair

We are going to create a multi-sig script with "**RequireMOf**" constructor or "**atLeast**" type.   
For our example, we first need to create verification keys for account1 and account2 using their public key. These verification 
keys, including the one from payment key pair, are then used to create corresponding ``ScriptPubKey`` (sig type native script) instances. 

Finally, all three ``ScriptPubKey`` instances can be composed to build a ``ScriptAtLeast`` instance.

1. Create ``VerificationKey`` instances using public keys of account1 and account2. Use verification keys to create ``ScriptPubKey`` instances.

```java
VerificationKey account1Vk = VerificationKey.create(account1.publicKeyBytes());
VerificationKey account2Vk = VerificationKey.create(account2.publicKeyBytes());

ScriptPubkey scriptPubkey1 = ScriptPubkey.create(account1Vk);
ScriptPubkey scriptPubkey2 = ScriptPubkey.create(account2Vk);
```

2. Use ``VerificationKey`` of payment key pair (3rd key) to create the third ``ScriptPubKey``

```java
ScriptPubkey scriptPubkey3 = ScriptPubkey.create(verificationKey);
```

3. Now we can use the above ``ScriptPubKey`` instances to create a multi-sig "**atLeast**" instance

```java
ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);
```

:::info
If you convert ``scriptAtLeast`` object to json, you will see something similar

```json
{
  "type" : "atLeast",
  "required" : 2,
  "scripts" : [ {
    "type" : "sig",
    "keyHash" : "0d30c6d716fd6c48ab546f0b66fd5faaa3a2f0ccecf0a72ea8c04a30"
  }, {
    "type" : "sig",
    "keyHash" : "1737dd414cf68676312db8af317fc89167292302b97d65a2d1def5a2"
  }, {
    "type" : "sig",
    "keyHash" : "cdba7678210358a58160183551f23589fb68ca0f71cd74ce776257fe"
  } ]
}
```
:::

## Create Script Address

Now let's use ``AddressService`` to generate a script address from our muti-sig native script.  

```json
String scriptAddress = AddressService.getInstance().getEntAddress(scriptAtLeast, Networks.testnet()).toBech32();
```

## Topup script address with test Ada

Based on your selected network (**prepod** / **preview**), get some test Ada from the below faucet. You need to provide ``scriptAddress``
generated in the previous section to get some test Ada.

https://docs.cardano.org/cardano-testnet/tools/faucet

## Create a Backend Service

Please check [here](account-setup#create-backend-service) to create a ``BackendService`` instance.

## Claim fund from script address and transfer to account1 and account2

Now we are ready to claim fund from the script address. 

### Define expected output

Let's say we want to claim total **50 Ada** from the script address and transfer **25 Ada** to account1 and **25 Ada** to account2.

```java
String address1 = account1.baseAddress();
Output output1 = Output.builder()
        .address(address1)
        .assetName(LOVELACE)
        .qty(adaToLovelace(25))
        .build();

String address2 = account2.baseAddress();
Output output2 = Output.builder()
        .address(address2)
        .assetName(LOVELACE)
        .qty(adaToLovelace(25))
        .build();      
```

### Define Transaction

**Line-1**, **Line-2** Create ``TxOutputBuilder`` from ``output1`` and compose it with another ``TxOutputBuilder``
generated from ``output2``.

:::info
**Note:** Check out various helper methods in ``com.bloxbean.cardano.client.function.helper.OutputBuilders`` to create ``TxOutputBuilder``.

:::

**Line-3,** Invoke ``TxOutputBuilder.buildInputs`` with a ``TxInputBuilder`` function. ``TxInputBuilder`` function builds required
inputs based on the expected outputs.

:::info
As we are claiming fund from script address, both **sender address** and **change address** are set to **scriptAddress** 
in ``TxInputBuilder createFromSender`` method call.

:::

**Line-5**, Add ``scriptAtLeast`` multi-sig script to transaction's witnessset.

**Line-7,** Use ``BalanceTxBuilders.balanceTx`` composable function to balance the unbalanced transaction.
It handles the followings to balance a transaction

- Fee calculation
- Adjust the outputs (if required)

The first parameter is change address which is set to ``scriptAddress`` as the fee is deducted from scriptAddress.

The second parameter is "no of signatures'. This is set to 2 as we have two signers for this transaction. This info is required
to calculate correct fee as fee calculation depends on transaction size.

```java showLineNumbers
TxBuilder txBuilder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .buildInputs(createFromSender(scriptAddress, scriptAddress))
                .andThen((context, txn) -> {
                    txn.getWitnessSet().getNativeScripts().add(scriptAtLeast);
                })
                .andThen(balanceTx(scriptAddress, 2));
```

### Build and Sign 

**Line-1 & Line-2,** Create ``UtxoSupplier`` & ``ProtocolParamsSupplier`` from the ``BackendService`` instance.

**Line-4 & Line-5,** Create ``TxSigner`` function by composing ``SignerProviders.signerFrom(Account... signers)`` & 
``SignerProviders.signerFrom(SecretKey sk)``. Then use ``TxSigner`` to sign the transaction.
In this case we are signing the transaction with ``account1`` and ``secretkey`` (third key) as we need at least 2 signatures
as per the rule defined in the multi-sig script.

:::info

Alternatively, you can also sign with account1 and account2.
:::

**Line-6,** Initialize ``TxBuilderContext`` using ``UtxoSupplier`` and ``ProtocolParamsSupplier``.

:::info

Using ``TxBuilderContext`` you can customize few behaviors during transaction building.

**For example:** Select a different ``UtxoSelectionStrategy`` implementation

:::

```java showLineNumbers
UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

TxSigner signers = signerFrom(account1)
                .andThen(signerFrom(secretKey)); //3rd sk/vk pair
Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(txBuilder, signers);

```

### Submit the transaction to Cardano network

Now we are ready to submit the transaction to the network. In this example, we are going to submit this transaction through
``BackendService``. Alternatively, you can submit the generated transaction using your own ``TransactionProcessor`` implementation.

```java
Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
System.out.println(result);
```

If successful, ``result.isSuccessful()`` will return true.  

Now copy the transaction id from the output and then go to a 
Cardano explorer, [Cardanoscan](https://cardanoscan.io) or [Cardano Explorer](https://cexplorer.io), to check the transaction
details. You may need to wait for few secs to a min depending on the blockchain load.

### Full Source Code

```java showLineNumbers
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressService;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.function.Output;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxBuilderContext;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptAtLeast;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;

import static com.bloxbean.cardano.client.common.ADAConversionUtil.adaToLovelace;
import static com.bloxbean.cardano.client.common.CardanoConstants.LOVELACE;
import static com.bloxbean.cardano.client.function.helper.BalanceTxBuilders.balanceTx;
import static com.bloxbean.cardano.client.function.helper.InputBuilders.createFromSender;
import static com.bloxbean.cardano.client.function.helper.SignerProviders.signerFrom;

public class MultiSigTransfer {

    public void transfer() throws Exception {
        //First account
        String account1Mnemonic = "turkey elder dad veteran they lumber feature garment race answer file erase riot resist sting process law deputy turtle foil legal calm exist civil";
        Account account1 = new Account(Networks.testnet(), account1Mnemonic);

        //Second account
        String account2Mnemonic = "report crowd trophy rough twin column access include evolve awkward world random bounce pave select rocket vote junk farm group main boat tissue mad";
        Account account2 = new Account(Networks.testnet(), account2Mnemonic);

        //Third account payment key
        String paymentSigningKeyCborHex = "58205d9ccc4202bde1785708c10f8b13231d5a57078c326d0e0ff172191f975a983e";
        SecretKey secretKey = new SecretKey(paymentSigningKeyCborHex);
        VerificationKey verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);

        //Derive verification key for account1 and account2
        VerificationKey account1Vk = VerificationKey.create(account1.publicKeyBytes());
        VerificationKey account2Vk = VerificationKey.create(account2.publicKeyBytes());

        //Create native script with type=sig for each verification key
        ScriptPubkey scriptPubkey1 = ScriptPubkey.create(account1Vk);
        ScriptPubkey scriptPubkey2 = ScriptPubkey.create(account2Vk);
        ScriptPubkey scriptPubkey3 = ScriptPubkey.create(verificationKey);

        //Create multi-sig script with type "atLeast"
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(2)
                .addScript(scriptPubkey1)
                .addScript(scriptPubkey2)
                .addScript(scriptPubkey3);

        //addr_test1wr6fvn0y3rumu30ch3lrggss4vmgsr65cxly2t6dulvwamq3y98et
        String scriptAddress = AddressService.getInstance().getEntAddress(scriptAtLeast, Networks.testnet()).toBech32();

        // For Blockfrost
        String bf_projectId = "preprod...";
        BackendService backendService = new BFBackendService(Constants.BLOCKFROST_PREPOD_URL, bf_projectId);
        // For Koios
        //BackendService backendService = new KoiosBackendService(KOIOS_TESTNET_URL);

        //Define outputs
        String address1 = account1.baseAddress();
        Output output1 = Output.builder()
                .address(address1)
                .assetName(LOVELACE)
                .qty(adaToLovelace(25))
                .build();

        String address2 = account2.baseAddress();
        Output output2 = Output.builder()
                .address(address2)
                .assetName(LOVELACE)
                .qty(adaToLovelace(25))
                .build();

        TxBuilder txBuilder = output1.outputBuilder()
                .and(output2.outputBuilder())
                .buildInputs(createFromSender(scriptAddress, scriptAddress))
                .andThen((context, txn) -> {
                    txn.getWitnessSet().getNativeScripts().add(scriptAtLeast);
                })
                .andThen(balanceTx(scriptAddress, 2));


        UtxoSupplier utxoSupplier = new DefaultUtxoSupplier(backendService.getUtxoService());
        ProtocolParamsSupplier protocolParamsSupplier = new DefaultProtocolParamsSupplier(backendService.getEpochService());

        TxSigner signers = signerFrom(account1)
                .andThen(signerFrom(secretKey)); //3rd sk/vk pair
        Transaction signedTransaction = TxBuilderContext.init(utxoSupplier, protocolParamsSupplier)
                .buildAndSign(txBuilder, signers);

        Result<String> result = backendService.getTransactionService().submitTransaction(signedTransaction.serialize());
        System.out.println(result);

    }

    public static void main(String[] args) throws Exception {
        new MultiSigTransfer().transfer();
    }
}

```

### What's next ?

Update the multi-sig script to support time-lock. For example, evaluate to true if minimum 2 signatures and slot number is after X.

**Hint:** Use **RequireTimeAfter** with **RequireMOf** to create multi-sig script.






