---
title: "Concepts"
description: "Key Concepts in Cardano Client Lib"
sidebar_label: Concepts
sidebar_position: 1
---

# Concepts

## Account

Account class provides a simple abstraction to create and manage secrets, and perform account-based work such as
signing transactions. It encapsulates features like CIP 1852 compatible address derivation, BIP-39 mnemonic generation through
a simple ``Account`` class. An instance of ``Account`` can be created from an existing mnemonic or account private key and derivation path.

## Transaction Building Apis
### Low Level Serialization Api

These are low level serialization api to build transaction for Cardano network. These APIs are flexible and good for 
complex scenarios. Basically, you can achieve any complexity with low level api. These apis provide flexibility, but building
transactions this way is complex. Unless really required, this option should be avoided to build transactions.

### Composable Functions (Recommended)

These apis provide a balance between simple interface and flexibility. Using out-of-box composable functions, you can achieve any 
complexity and at the same time, you can write your own composable functions to customize the behavior during transaction building.

### High-Level Api
Provides simple interfaces to do transfer and token minting transaction. These apis are beginner friendly, but some complex transactions
may not be possible through high-level API.

## Ledger data provider apis
Cardano is an UTXO based blockchain. Unlike account-based blockchains like Ethereum, a transaction in Cardano has inputs and outputs,
where inputs are unspent outputs from previous transactions. Assets are stored on the ledger in unspent outputs, rather than accounts.

A transaction is built using off-chain code and submitted to Cardano node for processing. But inputs (unspent outputs) used
in a transaction are fetched from the Cardano blockchain. Similarly, few protocol parameters may be required during transaction building.

### Supplier Interfaces

The library provides simple supplier interfaces which can be implemented to provide required on-chain data during transaction
building. These apis remove direct dependency with on-chain data provider in core library.

**1. ProtocolParameterSupplier** :- Implement this functional interface to provide current protocol parameters. 
Protocol parameters are required to build the transaction.

```java
public interface ProtocolParamsSupplier {
    ProtocolParams getProtocolParams();
}
```

**2. UtxoSupplier**:- This interface has only one method. Implement this interface to provide utxos which are required during
transaction building.

```java
public interface UtxoSupplier {
    ...
    List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order);
    ...
}
```

**3. TransactionProcessor** :- Implement this interface to submit transactions to the Cardano blockchain. This is an optional
interface.

```java
public interface TransactionProcessor {
    Result<String> submitTransaction(byte[] cborData) throws ApiException;
}
```

### Backend Api

Alternatively, the required on-chain data for transaction building can be retrieved through backend api layer. The library
defines standard apis to get commonly used on-chain data from a provider. Using a backend service implementation, you can easily create an 
instance of ``UtxoSupplier`` or ``ProtocolParamsSupplier``.

Following Backend apis are currently available
- TransactionService (Submit transaction, Get transaction, Evaluate ExUnits for Script Txn)
- AddressService (Get address details)
- UtxoService (Get utxos for an address)
- AssetService
- BlockService
- NetworkInfoService
- EpochService
- MetadataService

:::info

Though backend api provides an easy way to fetch required data from the blockchain, but it's not mandatory for transaction
building. You can just implement supplier interfaces to provide required on-chain data to build a transaction. **There is no
direct dependency between library's core functionality and backend apis.**

:::

#### Backend Providers
Out of box, the library currently supports integration with following providers through the Backend api.

- Blockfrost
- Koios
- Ogmios (Supports submitTransaction, evaluateTx and UtxoService through Kupo)

## Utxo Selection Strategy
The library provides different utxo selection strategy implementations. The utxo selection strategy can be 
changed by providing a custom implementation of "UtxoSelectionStrategy" interface or by selecting an existing one.

- **DefaultUtxoSelectionStrategyImpl**
- **LargestFirstUtxoSelectionStrategy**
- **RandomImproveUtxoSelectionStrategy**

:::info

By default, ``DefaultUtxoSelectionStrategy`` is used both in **Composable functions** and **High Level api**. The default strategy 
is too simple and finds required utxos sequentially. But it may not be efficient for some use-cases. You can easily use another
available UtxoSelectionStrategy impl or provide your own implementation.

:::

## CIP Implementations

Cardano Improvement Proposals (CIPs) describe standards, processes; or provide general guidelines or information to the 
Cardano Community. It is a formal, technical communication process that exists off-chain. 

The library implements some commonly used CIPs. 

- [CIP20 - Transaction Message/Comment metada](https://cips.cardano.org/cips/cip20/)
- [CIP25 - NFT Metadata Standard](https://cips.cardano.org/cips/cip25/)
- [CIP8  - Message Signing](https://cips.cardano.org/cips/cip8/)
- [CIP30  - dApp signData & verify](https://cips.cardano.org/cips/cip30/)
