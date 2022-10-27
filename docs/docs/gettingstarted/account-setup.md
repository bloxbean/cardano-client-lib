---
description: Account & Backend Provider Setup
sidebar_label: Account & Backend Provider Setup
sidebar_position: 3

---

# Account & Backend Provider Setup

## Select a Network and Provider

First we need to select a network for our transaction. You can choose one of the available public test network.

- **Prepod**
- **Preview**

Similarly, choose a backend provider to interact with Cardano blockchain. You can select either Koios or Blockfrost as
backend provider.

Please check [dependencies](dependencies.md) page to find the required dependency for your selected backend provider.

:::info
For **Blockfrost** as backend provider, you need to first create an account on [blockfrost.io](https://blockfrost.io) and get
a ``Project Id`` for the selected network.

For **Koios** backend provider, you don't need any registration.

:::

## Create Accounts

The following code snippet shows how to create a new testnet account.

```java
Account account = new Account(Networks.testnet()); 
String baseAddress = account.baseAddress();
String mnemonic = account.mnemonic();
```

If you already have mnemonic for an existing account, you can create the account from mnemonic. 

```java
String mnemonic = "<24 words mnemonic>";
Account account = new Account(Networks.testnet(), mnemonic);
```

:::info

Two types of address can be generated, mainnet address or testnet address.

To generate a test network address, you can use any of the network constant ``Networks.testnet()``, ``Networks.prepod()`` or
``Networks.preview()``. The generated testnet address can be used on any of the test network.
**(The address generation depends on the NetworkId in Network object not protocol magic. These public test networks have same network id (0))**

For mainnet address, you need to use ``Networks.mainnet()``

:::


## Topup account with test Ada

Based on your selected network, get some test Ada from one of the below faucet. You need to provide an ``address``
generated in the previous section to get some test Ada.

https://docs.cardano.org/cardano-testnet/tools/faucet

## Create Backend Service

**For Blockfrost :**

Use the correct Blockfrost url for the selected network and project id to create an instance of BackendService.

```java
String bfProjectId = "prepod...";
BackendService backendService =
        new BFBackendService(Constants.BLOCKFROST_PREPOD_URL, bfProjectId);
```
**Note:** You can find Blockfrost urls for the supported networks in ``com.bloxbean.cardano.client.backend.blockfrost.common.Constants``.

or,

**For Koios :**

```java
BackendService backendService = new KoiosBackendService(KOIOS_TESTNET_URL);
```
**Note:** You can find other Koios urls in ``com.bloxbean.cardano.client.backend.koios.Constants``
