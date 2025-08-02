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

### Unit Tests
Run unit tests:
```bash
./gradlew :watcher:test
```

### Integration Tests
The integration tests demonstrate real blockchain interaction using Yaci DevKit.

**Prerequisites:**
1. Start Yaci DevKit (external process):
   ```bash
   # Using Docker
   docker run -p 8080:8080 bloxbean/yaci-devkit:latest
   
   # Or using yaci-cli
   yaci-devkit start
   ```

2. Ensure Java 11 is being used:
   ```bash
   export JAVA_HOME=/path/to/java11
   # For example with SDKMAN:
   export JAVA_HOME=$HOME/.sdkman/candidates/java/11.0.19-librca
   ```

3. Run integration tests:
   ```bash
   # Run all integration tests
   ./gradlew :watcher:integrationTest -Dyaci.integration.test=true
   
   # Run specific test class
   ./gradlew :watcher:integrationTest -Dyaci.integration.test=true \
     --tests WatchableQuickTxBuilderRealIntegrationTest
   ```

**Note:** Yaci DevKit provides 10 pre-funded addresses (index 0-9) using the default mnemonic:
```
test test test test test test test test test test test test 
test test test test test test test test test test test sauce
```

The integration tests use these pre-funded accounts for testing transaction chains and UTXO dependencies.

## Architecture

### Component Overview
```
WatchableQuickTxBuilder
├── WatchableTxContext (step configuration)
├── WatchableStep (execution unit)
├── ChainContext (shared state)
├── ChainAwareUtxoSupplier (dependency resolution)
└── Watcher (chain orchestration)
```

### Multi-Step Transaction Interaction Diagram

This diagram shows how components interact during a multi-step transaction submission:

```mermaid
sequenceDiagram
    participant Client
    participant WatchableQuickTxBuilder as WQTxBuilder
    participant WatchableTxContext as WTxContext
    participant WatchableStep as WStep
    participant Watcher
    participant BasicWatchHandle as WatchHandle
    participant ChainContext
    participant ChainAwareUtxoSupplier as CAUtxoSupplier
    participant BackendService as Backend

    Note over Client: 1. BUILD PHASE - Create Transaction Steps
    
    Client->>WQTxBuilder: compose(tx1).withStepId("step1").watchable()
    WQTxBuilder->>WTxContext: create context with tx1, stepId, dependencies=[]
    WTxContext->>WStep: create WatchableStep(stepId="step1")
    WStep-->>Client: step1 (no dependencies)
    
    Client->>WQTxBuilder: compose(tx2).fromStep("step1").withStepId("step2").watchable()
    WQTxBuilder->>WTxContext: create context with tx2, stepId, dependencies=[step1]
    WTxContext->>WStep: create WatchableStep(stepId="step2")
    WStep-->>Client: step2 (depends on step1)

    Note over Client: 2. CHAIN PHASE - Build and Execute Chain
    
    Client->>Watcher: build("chain1").step(step1).step(step2).watch()
    Watcher->>ChainContext: create ChainContext(chainId="chain1")
    Watcher->>WatchHandle: create BasicWatchHandle(chainId, stepCount=2)
    Watcher->>Watcher: start async execution thread
    
    Note over Watcher: 3. ASYNC EXECUTION PHASE
    
    loop For each step in sequence
        Note over Watcher,Backend: Step Execution begins
        
        Watcher->>WStep: execute(chainContext)
        WStep->>WStep: validateDependencies(chainContext)
        
        alt Step has dependencies
            WStep->>ChainContext: check if dependency steps completed
            ChainContext-->>WStep: dependency status
            
            alt Dependencies not ready
                WStep-->>Watcher: throw UtxoDependencyException
            end
        end
        
        Note over WStep,Backend: UTXO Resolution & Transaction Building
        
        WStep->>WStep: determineUtxoSupplier(chainContext)
        
        alt Step has no dependencies
            WStep->>Backend: use DefaultUtxoSupplier
        else Step has dependencies
            WStep->>CAUtxoSupplier: create ChainAwareUtxoSupplier(baseSupplier, chainContext, dependencies)
            CAUtxoSupplier->>ChainContext: getStepOutputs(dependencyStepId)
            ChainContext-->>CAUtxoSupplier: available UTXOs from previous steps
            CAUtxoSupplier->>Backend: getUtxos() for base UTXOs
            CAUtxoSupplier->>CAUtxoSupplier: merge base + chain UTXOs
        end
        
        WStep->>WStep: createEffectiveTxContext(utxoSupplier)
        WStep->>Backend: build and submit transaction via QuickTxBuilder
        Backend-->>WStep: transaction result (hash or error)
        
        alt Transaction successful
            WStep->>WStep: captureOutputUtxos(transactionHash)
            WStep->>ChainContext: recordStepResult(stepId, success + outputs)
            WStep->>WatchHandle: recordStepResult(stepId, result)
            WatchHandle->>WatchHandle: trigger step completion callbacks
            WStep-->>Watcher: StepResult.success(hash, outputs)
        else Transaction failed
            WStep->>ChainContext: recordStepResult(stepId, failure + error)
            WStep->>WatchHandle: recordStepResult(stepId, result)
            WStep-->>Watcher: StepResult.failure(error)
            Watcher->>WatchHandle: markFailed(error)
            break
        end
    end
    
    Note over Watcher: 4. COMPLETION PHASE
    
    alt All steps successful
        Watcher->>WatchHandle: markCompleted()
        WatchHandle->>WatchHandle: trigger chain completion callbacks
    end
    
    Watcher-->>Client: WatchHandle (immediate return)
    
    Note over Client: 5. MONITORING PHASE
    
    Client->>WatchHandle: getStatus()
    WatchHandle-->>Client: PENDING/BUILDING/SUBMITTED/CONFIRMED/FAILED
    
    Client->>WatchHandle: onStepComplete(callback)
    WatchHandle->>WatchHandle: register callback for step events
    
    Client->>WatchHandle: onComplete(callback)
    WatchHandle->>WatchHandle: register callback for chain completion
```

### Key Interaction Points

1. **Build Phase**: Client creates WatchableSteps using fluent API
   - Steps declare UTXO dependencies via `fromStep()`, `fromStepUtxo()`, etc.
   - Each step encapsulates transaction logic + dependency metadata

2. **Chain Assembly**: Watcher orchestrates step execution
   - Creates shared ChainContext for inter-step communication
   - Validates step dependencies before execution
   - Executes steps sequentially in async thread

3. **UTXO Resolution**: ChainAwareUtxoSupplier provides dependency magic
   - For independent steps: uses base UtxoSupplier (blockchain UTXOs)
   - For dependent steps: merges blockchain + pending UTXOs from previous steps
   - Eliminates "insufficient funds" errors in transaction chains

4. **Execution Flow**: Each step builds/submits transaction independently
   - Uses effective UtxoSupplier determined by dependencies
   - Captures output UTXOs for use by subsequent steps
   - Records results in ChainContext for dependency resolution

5. **Monitoring**: Client gets immediate WatchHandle for progress tracking
   - Async execution allows non-blocking operation
   - Rich callback system for step and chain completion events
   - Status tracking throughout execution lifecycle

This architecture enables complex transaction workflows while maintaining simple, declarative APIs and automatic UTXO dependency management.