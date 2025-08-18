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
- **Chain Visualization**: ASCII art representation of chain structure, execution progress, and UTXO flow

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

## Chain Visualization

The watcher module includes a powerful visualization system that provides ASCII art representations of transaction chains, showing structure, execution progress, and UTXO dependencies.

### Basic Visualization

```java
import com.bloxbean.cardano.client.watcher.visualizer.ChainVisualizer;

// Visualize chain structure before execution
WatcherBuilder builder = Watcher.build("payment-chain")
    .step(depositStep)
    .step(withdrawStep);

String structure = ChainVisualizer.visualizeStructure(builder);
System.out.println(structure);
```

**Output:**
```
╔════════════════════════════════════════════════════════════╗
║ Chain: payment-chain                                       ║
║ Steps: 2 | Status: Not Started                            ║
╚════════════════════════════════════════════════════════════╝

┌──────────────┐      ┌──────────────┐
│   deposit    │ ───▶ │   withdraw   │
├──────────────┤      ├──────────────┤
│ Send 100 ADA │      │ Withdraw 50  │
│ to receiver  │      │ ADA back     │
└──────────────┘      └──────────────┘
     Step 1                Step 2
                      Depends on: 1
```

### Progress Monitoring

```java
// Execute the chain
WatchHandle handle = builder.watch();

// Show current progress
String progress = ChainVisualizer.visualizeProgress((BasicWatchHandle) handle);
System.out.println(progress);
```

**Output:**
```
╔════════════════════════════════════════════════════════════╗
║ Chain: payment-chain                                       ║
║ Progress: [████████░░░░░░░░░░] 40% | Time: 12.3s          ║
╚════════════════════════════════════════════════════════════╝

┌──────────────┐      ┌──────────────┐
│   deposit    │ ───▶ │   withdraw   │
├──────────────┤      ├──────────────┤
│      ✅      │      │      ⚡      │
│  CONFIRMED   │      │  EXECUTING   │
├──────────────┤      ├──────────────┤
│ tx: abc123.. │      │ tx: def456.. │
│ Block: 82345 │      │ Submitted    │
└──────────────┘      └──────────────┘
```

### UTXO Flow Visualization

```java
// Show how UTXOs flow between steps
String utxoFlow = ChainVisualizer.visualizeUtxoFlow((BasicWatchHandle) handle);
System.out.println(utxoFlow);
```

### Live Monitoring

```java
// Start real-time console monitoring
ChainVisualizer.startLiveMonitoring(handle, System.out);

// Custom refresh interval (500ms)
ChainVisualizer.startLiveMonitoring(handle, System.out, 500);
```

### Visualization Styles

The system automatically detects terminal capabilities and selects the best style:

```java
import com.bloxbean.cardano.client.watcher.visualizer.VisualizationStyle;

// Force specific style
String diagram = ChainVisualizer.visualizeStructure(builder, VisualizationStyle.SIMPLE_ASCII);
String diagram2 = ChainVisualizer.visualizeStructure(builder, VisualizationStyle.UNICODE_BOX);
```

Available styles:
- `SIMPLE_ASCII`: Basic ASCII characters (+-|) for maximum compatibility
- `UNICODE_BOX`: Unicode box drawing characters (┌─┐│) for better appearance  
- `COMPACT`: Minimal representation with reduced detail
- `DETAILED`: Full information including all metadata

### Export Formats

```java
// Export as JSON for external tools
String json = ChainVisualizer.exportJson((BasicWatchHandle) handle);

// Export as SVG for documentation
String svg = ChainVisualizer.exportSvg((BasicWatchHandle) handle);

// Export the abstract model for custom renderers
ChainVisualizationModel model = ChainVisualizer.exportModel((BasicWatchHandle) handle);
```

### Integration with Logging

```java
// Log chain structure at startup
logger.info("Chain Structure:\n{}", ChainVisualizer.visualizeStructure(builder));

// Log progress updates
handle.onStepComplete(stepId -> {
    String progress = ChainVisualizer.visualizeProgress((BasicWatchHandle) handle);
    logger.debug("Step {} completed:\n{}", stepId, progress);
});
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
- [x] ChainAwareUtxoSupplier 
- [x] Sequential execution engine
- [x] Integration tests passing
- [x] Chain visualization system with multiple output formats

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
    participant Builder as WatchableQuickTxBuilder
    participant Step as WatchableStep
    participant Watcher
    participant Handle as WatchHandle
    participant Context as ChainContext
    participant Supplier as ChainAwareUtxoSupplier
    participant Backend as BackendService

    Note over Client,Backend: Phase 1 - Build Transaction Steps
    
    Client->>Builder: compose(tx1).withStepId("deposit").watchable()
    Builder->>Step: create step1 (no dependencies)
    Step-->>Client: return step1
    
    Client->>Builder: compose(tx2).fromStep("deposit").withStepId("withdraw").watchable()
    Builder->>Step: create step2 (depends on deposit)
    Step-->>Client: return step2

    Note over Client,Backend: Phase 2 - Execute Chain
    
    Client->>Watcher: build("chain").step(step1).step(step2).watch()
    Watcher->>Context: create ChainContext
    Watcher->>Handle: create WatchHandle
    
    Note over Watcher,Backend: Phase 3 - Async Step Execution
    
    loop Each step in sequence
        Watcher->>Step: execute(chainContext)
        Step->>Step: validateDependencies()
        
        alt Has dependencies
            Step->>Supplier: create ChainAwareUtxoSupplier
            Supplier->>Context: getStepOutputs(previousStep)
            Context-->>Supplier: previous step UTXOs
            Supplier->>Backend: getUtxos() for base UTXOs
            Supplier-->>Step: merged UTXOs (base + chain)
        else No dependencies
            Step->>Backend: use default UtxoSupplier
        end
        
        Step->>Backend: build and submit transaction
        Backend-->>Step: transaction result
        
        alt Success
            Step->>Context: recordStepResult(success)
            Step->>Handle: notify step complete
        else Failure
            Step->>Handle: markFailed()
            break
        end
    end
    
    Watcher->>Handle: markCompleted() if all successful
    Watcher-->>Client: return WatchHandle
    
    Note over Client,Handle: Phase 4 - Monitor Progress
    
    Client->>Handle: getStatus()
    Handle-->>Client: current status
    
    Client->>Handle: onStepComplete(callback)
    Handle->>Handle: register callback
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
