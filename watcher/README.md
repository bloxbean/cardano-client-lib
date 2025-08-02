# Cardano Client Library - Watcher Module

The Watcher module extends the Cardano Client Library with the WatchableQuickTxBuilder API, providing automatic transaction monitoring, rollback detection, and sophisticated transaction chaining capabilities with UTXO dependency management.

## Features

- **WatchableQuickTxBuilder**: Extended QuickTxBuilder with transaction watching capabilities
- **Transaction Chaining**: Build multi-step transaction workflows with automatic UTXO dependencies
- **Chain-Aware UTXO Management**: Automatic UTXO resolution between dependent steps (no more "insufficient funds" errors!)
- **Step Dependencies**: Express UTXO relationships with `fromStep()`, `fromStepUtxo()`, and `fromStepWhere()`
- **Sequential Execution**: Steps execute in order with proper dependency resolution
- **Event System**: Rich event notifications for monitoring and integration
- **Status Tracking**: Monitor transaction and chain execution progress

## Quick Start

### Single Transaction Watching

```java
// Create WatchableQuickTxBuilder
WatchableQuickTxBuilder watchableBuilder = new WatchableQuickTxBuilder(backendService);

// Create and watch a simple transaction
Tx paymentTx = new Tx()
    .payToAddress(receiverAddress, Amount.ada(10))
    .from(senderAddress);

WatchHandle handle = watchableBuilder.compose(paymentTx)
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .feePayer(senderAccount.baseAddress())
    .withDescription("Payment to Alice")
    .watch();

System.out.println("Transaction submitted with watch ID: " + handle.getWatchId());
```

### Transaction Chaining with UTXO Dependencies

```java
// Step 1: Deposit transaction
WatchableStep depositStep = watchableBuilder.compose(new Tx()
        .payToAddress(receiverAddress, Amount.ada(100))
        .from(senderAccount.baseAddress()))
    .withSigner(SignerProviders.signerFrom(senderAccount))
    .feePayer(senderAccount.baseAddress())
    .withStepId("deposit")
    .withDescription("Deposit 100 ADA")
    .watchable();

// Step 2: Withdrawal transaction that depends on deposit outputs
WatchableStep withdrawStep = watchableBuilder.compose(new Tx()
        .payToAddress(senderAccount.baseAddress(), Amount.ada(50))
        .from(receiverAddress))
    .withSigner(SignerProviders.signerFrom(receiverAccount))
    .feePayer(receiverAddress)
    .fromStep("deposit")  // 🎯 This step uses outputs from deposit step!
    .withStepId("withdraw")
    .withDescription("Withdraw 50 ADA using deposit outputs")
    .watchable();

// Execute the chain
WatchHandle chainHandle = Watcher.build("my-chain")
    .step(depositStep)
    .step(withdrawStep)
    .withDescription("Deposit -> Withdraw Chain")
    .watch();

System.out.println("Chain started with ID: " + chainHandle.getWatchId());
```

### UTXO Dependency Options

```java
// Use all outputs from a step
.fromStep("previous-step")

// Use specific output by index
.fromStepUtxo("previous-step", 0)  // Use first output

// Use filtered outputs
.fromStepWhere("previous-step", utxo -> 
    utxo.getAmount().stream().anyMatch(amount -> 
        amount.getQuantity().compareTo(BigInteger.valueOf(1000000)) > 0))

// Chain multiple dependencies
.fromStep("step1")
.fromStepUtxo("step2", 1)
.fromStepWhere("step3", someCondition)
```

## Dependencies

This module depends on:
- `core` - Core CCL functionality
- `core-api` - Core API definitions
- `function` - Functional transaction building
- `quicktx` - Quick transaction API
- `backend` - Backend service abstractions

## Key Benefits

### 🚀 **ChainAware UTXO Management**
The revolutionary ChainAwareUtxoSupplier automatically resolves UTXO dependencies between steps:
- **Step 1**: Uses base UtxoSupplier → executes normally
- **Step 2**: Uses ChainAwareUtxoSupplier → sees Step 1 outputs as available inputs
- **No delays needed**: Step 2 can use Step 1 outputs immediately, even before blockchain confirmation

### ✅ **Solves Common Problems**
- **"Insufficient funds" errors**: Eliminated when step dependencies are properly declared
- **Manual UTXO tracking**: Automatic UTXO flow between steps
- **Complex transaction ordering**: Declarative step dependencies

## Module Status

✅ **MVP Complete** - Core functionality implemented and tested:
- [x] WatchableQuickTxBuilder with full API
- [x] Transaction chaining with UTXO dependencies  
- [x] ChainAwareUtxoSupplier (February 2025)
- [x] Sequential execution engine
- [x] Integration tests passing

🚧 **In Progress**: Advanced features, monitoring, production optimizations

## Testing

Run integration tests with Yaci DevKit:
```bash
./gradlew :watcher:integrationTest
```

## Architecture

```
WatchableQuickTxBuilder
├── WatchableTxContext (step configuration)
├── WatchableStep (execution unit)
├── ChainContext (shared state)
├── ChainAwareUtxoSupplier (dependency resolution)
└── Watcher (chain orchestration)
```