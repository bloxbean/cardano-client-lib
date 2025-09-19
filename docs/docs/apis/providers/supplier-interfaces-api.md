---
title: "Supplier Interfaces API"
description: "Extensible interfaces for custom backend data providers"
sidebar_position: 2
---

# Supplier Interfaces API

The Supplier Interfaces API provides extensible interfaces for integrating custom data providers with the Cardano Client Library. These interfaces enable custom backend implementations, service extensions, and flexible provider switching for enhanced testability and integration capabilities.

## Key Features

- **Extensible Architecture**: Define custom data provider implementations
- **Interface Standardization**: Consistent contracts for different data sources
- **Provider Flexibility**: Switch between implementations seamlessly
- **Testing Support**: Mock implementations for unit and integration testing
- **Service Composition**: Combine multiple suppliers for complex scenarios
- **Custom Integration**: Integrate with proprietary or specialized data sources

## Core Classes

### Supplier Interfaces
- `UtxoSupplier` - Provides UTXO data for addresses and transactions
- `ProtocolParamsSupplier` - Supplies current protocol parameters
- `TransactionProcessor` - Handles transaction submission and evaluation
- `ScriptSupplier` - Provides Plutus scripts by hash
- `TransactionEvaluator` - Evaluates transaction execution costs

### Built-in Implementations
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

Integrate custom suppliers with QuickTx:

```java
// Create custom suppliers
UtxoSupplier customUtxoSupplier = new CustomUtxoSupplier();
ProtocolParamsSupplier protocolParamsSupplier = new CustomProtocolParamsSupplier();

// Use with QuickTx
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(customUtxoSupplier)
    .protocolParamsSupplier(protocolParamsSupplier);

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

Use Kupo for UTXO data:

```java
// Kupo UTXO supplier
KupoUtxoSupplier kupoUtxoSupplier = new KupoUtxoSupplier("http://localhost:1442");

// Use with QuickTx
QuickTxBuilder builder = new QuickTxBuilder(kupoUtxoSupplier);
```

The Supplier Interfaces API provides extensible integration points for custom data providers and backend services.

