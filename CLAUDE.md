# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Cardano Client Library (CCL), a comprehensive Java library for Cardano blockchain interaction. The repository is a multi-module Gradle project with a modular architecture that supports different Cardano operations from basic transactions to advanced smart contract interactions.

**Key Focus**: The `watcher` module extends CCL with `WatchableQuickTxBuilder` API, providing automatic transaction monitoring, rollback detection, and sophisticated transaction chaining with UTXO dependency management.

## Before starting work
- Always in plan mode to make a plan
- After get the plan, make sure you Write the plan to .claude/tasks/TASK_NAME.md.
- The plan should be a detailed implementation plan and the reasoning behind them, as well as tasks broken down.
- If the task require external knowledge or certain package, also research to get latest knowledge (Use Task tool for research)
- Don't over plan it, always think MVP.
- Once you write the plan, firstly ask me to review it. Do not continue until I approve the plan.

## While implementing
- You should update the plan as you work.
- After you complete tasks in the plan, you should update and append detailed descriptions of the changes you made, so following tasks can be easily hand over to other engineers.

## Build Commands

### Main Build Commands
```bash
# Clean and build entire project
./gradlew clean build

# Build specific module (e.g., watcher module)
./gradlew :watcher:build

# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :watcher:test
```

### Integration Tests
```bash
# Run integration tests (requires Blockfrost API key)
export BF_PROJECT_ID=<Blockfrost_Project_ID>
./gradlew :integration-test:integrationTest -PBF_PROJECT_ID=${BF_PROJECT_ID}

# Watcher module integration tests (uses Yaci DevKit)
./gradlew :watcher:integrationTest -Dyaci.integration.test=true
```

### Development Commands
```bash
# Generate source and javadoc JARs
./gradlew sourceJar javadocJar

# Run specific test class
./gradlew :module:test --tests ClassName

# Run specific integration test
./gradlew :watcher:integrationTest --tests WatchableQuickTxBuilderRealIntegrationTest
```

## Architecture Overview

### Module Structure
The project follows a layered architecture with clear module boundaries:

**Core Foundation:**
- `common` - Utilities and base classes
- `crypto` - Cryptographic operations (Bip32, Bip39, CIP1852)
- `address` - Cardano address types and derivation

**Transaction Layer:**
- `transaction-spec` - CBOR serialization per CDDL spec
- `core` - High-level transaction APIs
- `function` - Composable transaction functions
- `quicktx` - Declarative transaction builder

**Backend Integration:**
- `backend` - Abstract backend APIs
- `backend-modules/blockfrost` - Blockfrost integration
- `backend-modules/koios` - Koios integration
- `backend-modules/ogmios` - Ogmios/Kupo integration

**Advanced Features:**
- `watcher` - Transaction monitoring and chaining (key focus)
- `plutus` - Smart contract support
- `cip/*` - Cardano Improvement Proposal implementations

### Watcher Module Architecture

The watcher module implements sophisticated transaction chaining through:

1. **WatchableQuickTxBuilder** - Extended QuickTxBuilder with monitoring
2. **ChainAwareUtxoSupplier** - Resolves UTXO dependencies between transaction steps
3. **Watcher** - Orchestrates multi-step transaction execution
4. **WatchHandle** - Provides async monitoring and status tracking

Key innovation: ChainAwareUtxoSupplier eliminates "insufficient funds" errors by making pending UTXOs from previous steps available to subsequent steps.

## Development Patterns

### Gradle Module Dependencies
- Dependencies are declared in individual `build.gradle` files
- The root `build.gradle` configures shared dependencies and publishing
- Integration tests use separate source sets (`src/it/java`)

### Testing Strategy
- Unit tests in `src/test/java` using JUnit 5
- Integration tests in `src/it/java` for blockchain interaction
- Watcher integration tests require Yaci DevKit for local blockchain

### Code Organization
- Package structure follows `com.bloxbean.cardano.client.*`
- Each module has clear API boundaries
- Builder patterns extensively used for fluent APIs

## Key APIs for Development

### Transaction Building (QuickTx)
```java
// Basic transaction
Tx tx = new Tx()
    .payToAddress(receiverAddr, Amount.ada(10))
    .from(senderAddr);

QuickTxBuilder builder = new QuickTxBuilder(backendService);
Result<String> result = builder.compose(tx)
    .withSigner(SignerProviders.signerFrom(account))
    .completeAndWait();
```

### Transaction Chaining (Watcher)
```java
// Multi-step with UTXO dependencies
WatchableStep step1 = builder.compose(depositTx)
    .withStepId("deposit")
    .watchable();

WatchableStep step2 = builder.compose(withdrawTx)
    .fromStep("deposit")  // Uses outputs from step1
    .withStepId("withdraw")
    .watchable();

WatchHandle handle = Watcher.build("chain")
    .step(step1)
    .step(step2)
    .watch();
```

### Backend Service Setup
```java
// Blockfrost backend
BackendService backendService = new BFBackendService(
    Constants.BLOCKFROST_TESTNET_URL, 
    projectId
);

// Access individual services
UtxoService utxoService = backendService.getUtxoService();
TransactionService txService = backendService.getTransactionService();
```

## Important Development Notes

### Watcher Module Testing
- Integration tests require Yaci DevKit running on port 8080
- Uses pre-funded test accounts with default mnemonic
- Java 11 required for integration tests
- Tests demonstrate real blockchain transaction chaining

### Module Dependencies
- Always check existing modules before adding new dependencies
- Follow established patterns for UTXO selection strategies
- Use existing serialization patterns for CBOR/JSON

### Backend Integration
- Multiple backend providers supported (Blockfrost, Koios, Ogmios)
- UtxoSupplier and ProtocolParamsSupplier abstractions for pluggability
- ChainAwareUtxoSupplier in watcher module for advanced UTXO management

### Error Handling
- Use `Result<T>` pattern for operation results
- Custom exceptions for domain-specific errors (e.g., UtxoDependencyException)
- Comprehensive error propagation in transaction chains

This architecture enables building complex Cardano applications while maintaining clean separation of concerns and extensive testing capabilities.
