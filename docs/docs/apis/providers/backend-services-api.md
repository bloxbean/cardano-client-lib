---
title: "Backend Services API"
description: "APIs for integrating with Cardano backend services"
sidebar_position: 1
---

# Backend Services API

The Backend Services API provides integration with various Cardano backend services through a unified interface. This allows applications to interact with the Cardano blockchain without running a local node, supporting multiple providers for flexibility and redundancy.

## Key Features

- **Unified Interface**: Single API for multiple backend providers
- **Provider Flexibility**: Switch between different backend services easily
- **Service Coverage**: Complete access to blockchain data and transaction services
- **Network Support**: Mainnet, testnet, and preview network compatibility
- **Automatic Failover**: Built-in support for provider redundancy
- **Rate Limiting**: Respect provider rate limits and quotas

## Core Classes

### BackendService Interface
The main interface for all backend service implementations, providing access to various blockchain services.

### Provider Implementations
- `BFBackendService` - Blockfrost integration
- `KoiosBackendService` - Koios REST API integration  
- `OgmiosBackendService` - Ogmios WebSocket integration

### Service Interfaces
- `TransactionService` - Transaction submission and querying
- `UtxoService` - UTXO management and queries
- `AddressService` - Address information and history
- `AssetService` - Native token and NFT data
- `BlockService` - Block information and navigation
- `EpochService` - Epoch and staking information

## Supported Backend Services

| Provider | Module | Features | Status |
|----------|--------|----------|--------|
| **Blockfrost** | `cardano-client-backend-blockfrost` | Complete API coverage, high reliability | ✅ Stable |
| **Koios** | `cardano-client-backend-koios` | REST API, community-driven | ✅ Stable |
| **Ogmios** | `cardano-client-backend-ogmios` | WebSocket, real-time updates | ✅ Stable |

## Usage Examples

### Blockfrost Integration

Initialize Blockfrost backend service with API key:

```java
// Initialize Blockfrost service
BackendService backendService = new BFBackendService(
    Constants.BLOCKFROST_TESTNET_URL, 
    "your-project-id"
);

// Access different services
TransactionService transactionService = backendService.getTransactionService();
UtxoService utxoService = backendService.getUtxoService();
AddressService addressService = backendService.getAddressService();
AssetService assetService = backendService.getAssetService();

// Use with QuickTx
QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
```

### Koios Integration

Initialize Koios backend service for community-driven API:

```java
// Initialize Koios service
BackendService backendService = new KoiosBackendService(
    "https://api.koios.rest/api/v1"
);

// Access services
TransactionService transactionService = backendService.getTransactionService();
UtxoService utxoService = backendService.getUtxoService();
AddressService addressService = backendService.getAddressService();

// Query address information
List<AddressContent> addressInfo = addressService.getAddressInfo("addr1...");
System.out.println("Address balance: " + addressInfo.get(0).getAmount());
```

### Ogmios Integration

Initialize Ogmios backend service for WebSocket-based API:

```java
// Initialize Ogmios service
BackendService backendService = new OgmiosBackendService(
    "ws://localhost:1337"
);

// Access services
TransactionService transactionService = backendService.getTransactionService();
UtxoService utxoService = backendService.getUtxoService();

// Submit transaction
Result<String> result = transactionService.submitTransaction(transactionCbor);
if (result.isSuccessful()) {
    System.out.println("Transaction submitted: " + result.getValue());
}
```

## Advanced Usage

### Provider Switching

Switch between different providers based on requirements:

```java
public class BackendServiceFactory {
    
    public static BackendService createService(String provider, String endpoint, String apiKey) {
        switch (provider.toLowerCase()) {
            case "blockfrost":
                return new BFBackendService(endpoint, apiKey);
            case "koios":
                return new KoiosBackendService(endpoint);
            case "kupomios":
                // Kupomios requires both Ogmios and Kupo URLs
                String ogmiosUrl = endpoint; // e.g., "http://localhost:1337"
                String kupoUrl = apiKey;     // e.g., "http://localhost:1442" (reusing apiKey param for kupoUrl)
                return new KupmiosBackendService(ogmiosUrl, kupoUrl);
            default:
                throw new IllegalArgumentException("Unknown provider: " + provider);
        }
    }
}

// Usage examples
BackendService blockfrostService = BackendServiceFactory.createService("blockfrost", 
    Constants.BLOCKFROST_MAINNET_URL, "your-api-key");
    
BackendService kupmiosService = BackendServiceFactory.createService("kupomios",
    "http://localhost:1337", "http://localhost:1442");
```

### Service Usage Examples

Query blockchain data using backend services:

```java
// Get UTXO information
List<Utxo> utxos = utxoService.getUtxos("addr1...", 50, 1);
System.out.println("Found " + utxos.size() + " UTXOs");

// Get transaction details
Transaction transaction = transactionService.getTransaction("tx_hash...");
System.out.println("Transaction fee: " + transaction.getFee());

// Get address balance
AddressContent addressInfo = addressService.getAddressInfo("addr1...").get(0);
System.out.println("Address balance: " + addressInfo.getAmount());
```

## Available Services

| Service | Purpose | Key Methods |
|---------|---------|-------------|
| **TransactionService** | Transaction operations | `submitTransaction()`, `getTransaction()` |
| **UtxoService** | UTXO management | `getUtxos()`, `getUtxoByTxnIdAndIndex()` |
| **AddressService** | Address information | `getAddressInfo()`, `getAddressTransactions()` |
| **AssetService** | Asset and token data | `getAsset()`, `getAssetAddresses()` |
| **BlockService** | Block information | `getLatestBlock()`, `getBlockByHash()` |
| **EpochService** | Epoch and staking data | `getLatestEpoch()`, `getEpochParameters()` |
| **MetadataService** | Transaction metadata | `getTransactionMetadata()` |
| **FeeCalculationService** | Fee estimation | `calculateFee()` |

The Backend Services API provides a flexible and robust foundation for Cardano blockchain integration across multiple provider ecosystems.
