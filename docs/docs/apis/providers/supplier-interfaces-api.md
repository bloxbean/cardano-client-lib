---
title: "Supplier Interfaces API"
description: "Extensible interfaces for custom backend data providers"
sidebar_position: 2
---

# Supplier Interfaces API

The Supplier Interfaces API provides extensible interfaces for integrating custom data providers with the Cardano Client Library. These interfaces provide the **essential APIs required to build and submit transactions**, making them the quickest and clearest way to integrate any third-party data provider.

## What are Supplier Interfaces?

Supplier interfaces provide the core functionality needed for transaction building and submission:
- **Getting UTXOs** - Retrieve unspent transaction outputs for addresses
- **Fetching protocol parameters** - Get current network protocol parameters
- **Evaluating transaction script costs** - Calculate execution costs for Plutus scripts
- **Submitting transactions** - Send transactions to the blockchain

**Important:** The transaction builder APIs depend on **Supplier interfaces**, not directly on `BackendService`. This provides the right level of abstraction to integrate any third-party provider with minimal effort.

### Supplier Interfaces vs BackendService

While `BackendService` provides many additional APIs useful for dApps (address history, asset metadata, block data, etc.), creating a new `BackendService` implementation requires significantly more effort. Supplier interfaces focus only on what's **strictly required for transaction building and submission**, making integration faster and simpler.

## Key Features

- **Minimal Integration Surface**: Only implement what's needed for transactions
- **Extensible Architecture**: Define custom data provider implementations
- **Interface Standardization**: Consistent contracts for different data sources
- **Provider Flexibility**: Switch between implementations seamlessly
- **Testing Support**: Mock implementations for unit and integration testing
- **Service Composition**: Combine multiple suppliers for complex scenarios

## Core Classes

### Supplier Interfaces
- `UtxoSupplier` - Provides UTXO data for addresses and transactions
- `ProtocolParamsSupplier` - Supplies current protocol parameters
- `TransactionProcessor` - Handles transaction submission and evaluation
- `ScriptSupplier` - Provides Plutus scripts by hash
- `TransactionEvaluator` - Evaluates transaction execution costs

### Default Implementations

The library provides default implementations that are automatically used when creating `QuickTxBuilder` from `BackendService`:

- `DefaultUtxoSupplier` - Default UTXO supplier from BackendService
- `DefaultProtocolParamsSupplier` - Default protocol parameters from BackendService  
- `DefaultTransactionProcessor` - Default transaction processor from BackendService

These are instantiated automatically when using:
```java
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
```

### Third-Party Implementations

For specialized use cases, third-party supplier implementations are available:

- `OgmiosProtocolParamSupplier` - Ogmios-based protocol parameters
- `OgmiosTransactionProcessor` - Ogmios transaction processing
- `KupoUtxoSupplier` - Kupo-based UTXO provider
- `OgmiosTransactionEvaluator` - Ogmios script evaluation

## Usage Examples

### Custom UtxoSupplier Implementation

Implement a custom UTXO supplier:

```java
public class CustomUtxoSupplier implements UtxoSupplier {
    
    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        // Implement UTXO fetching logic
        return fetchUtxosFromCustomSource(address, nrOfItems, page, order);
    }
    
    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        // Implement transaction output retrieval
        return fetchTransactionOutput(txHash, outputIndex);
    }
    
    @Override
    public List<Utxo> getAll(String address) {
        // Fetch all UTXOs for address
        return fetchAllUtxosFromSource(address);
    }
}
```

### Custom ProtocolParamsSupplier

Implement protocol parameters supplier:

```java
public class CustomProtocolParamsSupplier implements ProtocolParamsSupplier {
    
    @Override
    public ProtocolParams getProtocolParams() {
        // Fetch current protocol parameters from your source
        return ProtocolParams.builder()
            .minFeeA(44)
            .minFeeB(155381)
            .maxBlockSize(65536)
            .maxTxSize(16384)
            .keyDeposit(BigInteger.valueOf(2000000))
            .poolDeposit(BigInteger.valueOf(500000000))
            .build();
    }
}
```

### Using Custom Suppliers

Integrate custom suppliers with QuickTx via constructor:

```java
// Create custom suppliers
UtxoSupplier customUtxoSupplier = new CustomUtxoSupplier();
ProtocolParamsSupplier protocolParamsSupplier = new CustomProtocolParamsSupplier();
TransactionProcessor transactionProcessor = new CustomTransactionProcessor();

// Use with QuickTx - suppliers must be provided through constructor
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(
    customUtxoSupplier,
    protocolParamsSupplier,
    transactionProcessor
);

// Build and submit transaction
Tx tx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress);

Result<String> result = quickTxBuilder.compose(tx)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait(System.out::println);
```

## Built-in Implementations

### Ogmios Suppliers

Use Ogmios-based suppliers:

```java
// Ogmios protocol parameters
OgmiosProtocolParamSupplier ogmiosProtocolParams = 
    new OgmiosProtocolParamSupplier("http://localhost:1337");

// Ogmios transaction processor
OgmiosTransactionProcessor ogmiosProcessor = 
    new OgmiosTransactionProcessor("http://localhost:1337");
```

### Kupo Suppliers

Use Kupo for UTXO data combined with Ogmios for other services:

```java
// Kupo UTXO supplier
KupoUtxoSupplier kupoUtxoSupplier = new KupoUtxoSupplier("http://localhost:1442");

// Ogmios for protocol params and transaction processing
OgmiosProtocolParamSupplier protocolParamsSupplier = 
    new OgmiosProtocolParamSupplier("http://localhost:1337");
OgmiosTransactionProcessor transactionProcessor = 
    new OgmiosTransactionProcessor("http://localhost:1337");

// Use with QuickTx - all suppliers provided through constructor
QuickTxBuilder builder = new QuickTxBuilder(
    kupoUtxoSupplier,
    protocolParamsSupplier,
    transactionProcessor
);
```

### Combining with BackendService

You can also combine a custom supplier with BackendService:

```java
// Use Kupo for UTXOs, BackendService for everything else
KupoUtxoSupplier kupoUtxoSupplier = new KupoUtxoSupplier("http://localhost:1442");
BackendService backendService = new BFBackendService(
    Constants.BLOCKFROST_TESTNET_URL,
    "your-project-id"
);

QuickTxBuilder builder = new QuickTxBuilder(backendService, kupoUtxoSupplier);
```

The Supplier Interfaces API provides extensible integration points for custom data providers and backend services.

