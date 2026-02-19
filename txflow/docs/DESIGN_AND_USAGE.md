# TxFlow: Design & Usage Guide

TxFlow orchestrates multi-step Cardano transaction flows with automatic UTXO dependency management, confirmation tracking, rollback recovery, and retry policies.

**Core problem:** When a Cardano workflow requires multiple related transactions (e.g., deposit then release, mint then distribute), each subsequent transaction may depend on UTXOs produced by a previous one. TxFlow automates this dependency resolution, monitors confirmations, and handles chain reorganizations.

## Table of Contents

1. [Overview](#1-overview)
2. [Building Flows](#2-building-flows)
3. [Step Dependencies & UTXO Chaining](#3-step-dependencies--utxo-chaining)
4. [Chaining Modes](#4-chaining-modes)
5. [Confirmation Tracking](#5-confirmation-tracking)
6. [Rollback Strategies](#6-rollback-strategies)
7. [Retry Policy](#7-retry-policy)
8. [Flow Execution](#8-flow-execution)
9. [FlowResult & FlowStepResult](#9-flowresult--flowstepresult)
10. [State Persistence & Recovery](#10-state-persistence--recovery)
11. [FlowRegistry](#11-flowregistry)
12. [Quick Reference](#12-quick-reference)
13. [See Also](#13-see-also)

---

## 1. Overview

### Core Concepts

```
TxFlow              Top-level container: ID, description, variables, ordered list of steps
  └─ FlowStep      Single transaction step: ID, tx definition, dependencies, retry policy
       └─ StepDependency   UTXO dependency on a previous step's outputs

FlowExecutor        Orchestrates execution: builds, submits, confirms, retries, handles rollbacks
  ├─ FlowHandle    Async monitoring: status, progress, cancellation, await
  └─ FlowResult    Aggregated result: status, step results, tx hashes, duration, errors
```

### Minimal Example

```java
// Define a two-step flow
TxFlow flow = TxFlow.builder("simple-transfer")
    .withDescription("Send ADA then send a token")
    .addStep(FlowStep.builder("send-ada")
        .withTxContext(builder -> builder
            .compose(new Tx()
                .payToAddress(receiver, Amount.ada(10))
                .from(sender))
            .withSigner(SignerProviders.signerFrom(account)))
        .build())
    .addStep(FlowStep.builder("send-token")
        .dependsOn("send-ada")  // Uses UTXOs from step 1
        .withTxContext(builder -> builder
            .compose(new Tx()
                .payToAddress(receiver, Amount.asset(policyId, assetName, 1))
                .from(sender))
            .withSigner(SignerProviders.signerFrom(account)))
        .build())
    .build();

// Execute synchronously
FlowExecutor executor = FlowExecutor.create(backendService);
FlowResult result = executor.executeSync(flow);

if (result.isSuccessful()) {
    System.out.println("All tx hashes: " + result.getTransactionHashes());
}
```

---

## 2. Building Flows

### TxFlow.builder API

```java
TxFlow flow = TxFlow.builder("escrow-flow")       // Required: unique flow ID
    .withDescription("Deposit and release escrow") // Optional: human-readable description
    .withVersion("1.0")                            // Optional: schema version (default "1.0")
    .addVariable("amount", 50_000_000L)            // Optional: flow-level variables
    .addVariable("receiver", "addr_test1...")
    .addStep(step1)                                // Required: at least one step
    .addStep(step2)
    .build();                                      // Throws if no steps defined
```

### FlowStep.builder API

Each step needs either a `TxContext` factory (Java-first) or a `TxPlan` (YAML-first). They are mutually exclusive.

```java
// Java-first: full access to QuickTxBuilder.TxContext
FlowStep step = FlowStep.builder("deposit")           // Required: unique step ID
    .withDescription("Lock funds in escrow contract")  // Optional
    .withTxContext(builder -> builder                   // Transaction definition
        .compose(new ScriptTx()
            .payToContract(contractAddr, amount, datum)
            .attachSpendingValidator(script))
        .feePayer(feeAddr)
        .collateralPayer(collateralAddr)
        .withSigner(signer)
        .validFrom(currentSlot))
    .dependsOn("previous-step")                        // Optional: UTXO dependency
    .withRetryPolicy(RetryPolicy.defaults())           // Optional: step-level retry
    .build();                                          // Throws if no tx definition

// YAML-first: uses TxPlan for declarative definition
FlowStep yamlStep = FlowStep.builder("mint-token")
    .withTxPlan(txPlan)
    .build();
```

### Flow Validation

`TxFlow.validate()` checks for:

- **Duplicate step IDs** — each step must have a unique ID
- **Missing dependency references** — `dependsOn("X")` requires step `"X"` to exist
- **Circular dependencies** — detected via DFS cycle detection
- **Forward dependencies** — a step cannot depend on a later step

```java
TxFlow.ValidationResult validation = flow.validate();
if (!validation.isValid()) {
    System.err.println("Errors: " + validation.getErrors());
}
```

Validation runs automatically before execution (`executeSync` / `execute`). Invalid flows throw `FlowExecutionException`.

### YAML Serialization

Flows can be serialized to/from YAML for storage or transfer:

```java
String yaml = flow.toYaml();
TxFlow restored = TxFlow.fromYaml(yaml);
```

---

## 3. Step Dependencies & UTXO Chaining

Steps can declare dependencies on outputs from previous steps using `StepDependency`. The executor resolves these dependencies by making the specified UTXOs available to the dependent step's UTXO supplier.

### SelectionStrategy

| Strategy | Description | Factory Method |
|----------|-------------|----------------|
| `ALL` | All outputs from the previous step | `dependsOn("stepId")` |
| `INDEX` | A specific output by index | `dependsOnIndex("stepId", 0)` |
| `CHANGE` | The change output (last output) | `dependsOnChange("stepId")` |
| `FILTER` | Outputs matching a predicate | `StepDependency.filter("stepId", predicate)` |

### Usage Examples

```java
// Use ALL outputs from "deposit"
FlowStep.builder("release")
    .dependsOn("deposit")                        // Default: SelectionStrategy.ALL
    .withTxContext(...)
    .build();

// Use only output at index 0
FlowStep.builder("release")
    .dependsOnIndex("deposit", 0)
    .withTxContext(...)
    .build();

// Use only the change output
FlowStep.builder("next-payment")
    .dependsOnChange("deposit")
    .withTxContext(...)
    .build();

// Use a filter predicate
FlowStep.builder("collect")
    .dependsOn(StepDependency.filter("deposit",
        utxo -> utxo.getAmount().stream()
            .anyMatch(a -> a.getQuantity().compareTo(BigInteger.valueOf(5_000_000)) > 0)))
    .withTxContext(...)
    .build();

// Optional dependency (won't fail if step has no outputs)
FlowStep.builder("optional-step")
    .dependsOn(StepDependency.builder("maybe-step")
        .withStrategy(SelectionStrategy.ALL)
        .optional()
        .build())
    .withTxContext(...)
    .build();
```

### How UTXO Resolution Works

1. Step A executes and produces a transaction with outputs
2. The executor captures those outputs as `List<Utxo>` in `FlowExecutionContext`
3. When Step B (which depends on A) executes, the executor resolves dependencies:
   - Calls `StepDependency.resolveUtxos(context)` to get the selected UTXOs
   - Makes them available through the UTXO supplier so the transaction builder can find them
   - Filters out UTXOs already spent by previous steps
4. Step B's transaction is built with access to both on-chain UTXOs and pending UTXOs from A

---

## 4. Chaining Modes

The chaining mode controls how transactions are submitted and confirmed relative to each other.

### SEQUENTIAL (Default)

Each step waits for confirmation before the next step begins.

```
Step 1: build → submit → wait for confirmation ✓
Step 2: build → submit → wait for confirmation ✓
Step 3: build → submit → wait for confirmation ✓
```

**Guarantees:** Each transaction is in a separate block.

### PIPELINED

All transactions are built and submitted without waiting for confirmations between steps. Confirmations are awaited after all submissions.

```
Step 1: build → submit ─────────────────────────→ wait for confirmation ✓
Step 2:          build → submit ─────────────────→ wait for confirmation ✓
Step 3:                   build → submit ────────→ wait for confirmation ✓
```

**Enables:** Multiple transactions in the same block for faster execution.

### BATCH

All transactions are built and signed first (Phase 1), then submitted in rapid succession (Phase 2), then confirmations are awaited (Phase 3).

```
Phase 1 (Build):  build tx1 → build tx2 → build tx3
Phase 2 (Submit): submit tx1, submit tx2, submit tx3  (rapid fire)
Phase 3 (Confirm): wait for tx1 ✓, wait for tx2 ✓, wait for tx3 ✓
```

**Enables:** Highest likelihood of same-block inclusion. Transaction hashes are computed client-side using Blake2b256, so subsequent transactions can reference earlier outputs before any are submitted.

### Comparison

| Aspect | SEQUENTIAL | PIPELINED | BATCH |
|--------|-----------|-----------|-------|
| Safety | Highest | Medium | Medium |
| Speed | Slowest | Fast | Fastest |
| Same-block possible | No | Yes | Highest likelihood |
| Cascade failure risk | None | Medium (later txs fail if earlier fail) | High |
| Rollback recovery | Per-step | Skip confirmed, rebuild rest | Skip confirmed, rebuild rest |
| Best for | Production, complex deps | Simple UTXO chaining | Devnets, fast networks |

### Rollback Behavior by Chaining Mode

Rollback handling differs significantly across chaining modes:

**SEQUENTIAL:** Supports all 4 rollback strategies with full per-step control. `REBUILD_FROM_FAILED` rebuilds only the rolled-back step (with auto-escalation to flow restart if the step has downstream dependents). `REBUILD_ENTIRE_FLOW` restarts from step 1.

**PIPELINED:** Any rollback triggers a full flow restart regardless of the configured strategy. On restart, the executor calls `findStillConfirmedSteps()` to identify which transactions are still on-chain, then skips those steps during re-execution. For example, if steps 1-3 are submitted and step 2 is rolled back but steps 1 and 3 are still confirmed, only step 2 is rebuilt on restart. `REBUILD_FROM_FAILED` effectively behaves as `REBUILD_ENTIRE_FLOW` in this mode.

**BATCH:** Any rollback triggers a flow restart. Like PIPELINED, the executor identifies which transactions are still confirmed on-chain and skips those steps (build, submit, and confirm phases are all skipped for still-confirmed steps). Only rolled-back or unconfirmed steps are rebuilt. `REBUILD_FROM_FAILED` effectively behaves as `REBUILD_ENTIRE_FLOW` in this mode.

### Usage

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    .withChainingMode(ChainingMode.PIPELINED);
```

---

## 5. Confirmation Tracking

Confirmation tracking monitors transactions through their lifecycle and detects chain reorganizations (rollbacks).

### ConfirmationStatus Lifecycle

```
SUBMITTED → IN_BLOCK → CONFIRMED
                ↓
           ROLLED_BACK
```

| Status | Meaning | Depth Criteria |
|--------|---------|----------------|
| `SUBMITTED` | In mempool, not yet in a block | Not found in block |
| `IN_BLOCK` | Included in a block, waiting for confirmations | `0 ≤ depth < minConfirmations` |
| `CONFIRMED` | Reached practical safety threshold | `depth ≥ minConfirmations` |
| `ROLLED_BACK` | Was in chain but disappeared (reorg detected) | Previously tracked, now not found |

### What the Executor Waits For

The executor waits for `CONFIRMED` status (reaching `minConfirmations` depth).

| Configuration | Target Status | Depth Required | Mainnet Wait Time |
|--------------|---------------|----------------|-------------------|
| Default | `CONFIRMED` | `minConfirmations` (default: 10) | ~200 seconds |
| No `withConfirmationConfig()` | N/A | Simple polling (tx exists = confirmed) | Depends on `confirmationTimeout` |

**Without confirmation tracking:** If no `ConfirmationConfig` is set, the executor uses simple polling — it considers a transaction confirmed as soon as it appears on-chain, with no depth tracking and no rollback detection.

### ConfirmationConfig

```java
ConfirmationConfig config = ConfirmationConfig.builder()
    .minConfirmations(10)                          // Blocks for CONFIRMED status (default: 10)
    .checkInterval(Duration.ofSeconds(5))          // Polling interval (default: 5s)
    .timeout(Duration.ofMinutes(30))               // Max wait time (default: 30min)
    .maxRollbackRetries(3)                         // Max rebuild/restart attempts (default: 3)
    .waitForBackendAfterRollback(false)            // Wait for node after rollback (default: false)
    .postRollbackWaitAttempts(5)                   // Backend readiness check attempts (default: 5)
    .postRollbackUtxoSyncDelay(Duration.ZERO)      // Extra delay for UTXO indexer sync (default: 0)
    .build();
```

### Presets

| Preset | minConfirmations | checkInterval | timeout |
|--------|-----------------|---------------|---------|
| `defaults()` | 10 | 5s | 30min |
| `devnet()` | 3 | 1s | 5min |
| `testnet()` | 6 | 3s | 10min |
| `quick()` | 1 | 1s | 2min |

The `devnet()` and `quick()` presets also enable `waitForBackendAfterRollback` with `postRollbackWaitAttempts=30` and `postRollbackUtxoSyncDelay=3s`, since test environments may restart nodes during rollback simulation.

### Enabling Confirmation Tracking

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    .withConfirmationConfig(ConfirmationConfig.devnet());
```

Without `withConfirmationConfig`, the executor uses simple confirmation checking (transaction exists = confirmed) and rollback detection is disabled.

---

## 6. Rollback Strategies

Chain reorganizations (rollbacks/reorgs) occur when the network switches to a longer competing chain, removing previously included transactions. Rollback strategies determine how the executor responds.

### FAIL_IMMEDIATELY (Default)

```
Rollback detected
  → onTransactionRolledBack callback
  → Step fails with rollback error
  → Flow fails
```

The safest option. The application decides how to handle the failure externally.

### NOTIFY_ONLY

```
Rollback detected
  → onTransactionRolledBack callback
  → Continue monitoring (re-enter polling loop)
  → If re-included: monitoring continues normally
  → If not re-included: eventually times out
```

Useful when you expect shallow reorgs where the transaction may be re-included, or when external systems handle rollback logic.

### REBUILD_FROM_FAILED

```
Rollback detected
  → onTransactionRolledBack callback
  → onStepRebuilding callback
  → Clear step result
  → Rebuild step with fresh UTXOs
  → Submit and monitor rebuilt transaction
```

**Auto-escalation:** If the rolled-back step has downstream dependents (other steps depend on its outputs), REBUILD_FROM_FAILED automatically escalates to a full flow restart, since downstream steps would reference invalid UTXOs.

> **Mode-specific behavior:** Per-step rebuild only applies in SEQUENTIAL mode. In PIPELINED and BATCH modes, `REBUILD_FROM_FAILED` behaves identically to `REBUILD_ENTIRE_FLOW` (triggers a flow restart with skip-confirmed optimization).

### REBUILD_ENTIRE_FLOW

```
Rollback detected
  → onTransactionRolledBack callback
  → onFlowRestarting callback
  → Clear all step results
  → Re-execute entire flow from step 1
```

> **Mode-specific behavior:**
> - **SEQUENTIAL:** Clears all step results and re-executes the entire flow from step 1.
> - **PIPELINED:** On restart, checks which transactions are still confirmed on-chain and skips those steps. Only rolled-back or unconfirmed steps are rebuilt.
> - **BATCH:** Same as PIPELINED — on restart, checks which transactions are still confirmed on-chain and skips those steps across all three phases (build, submit, confirm). Only rolled-back or unconfirmed steps are rebuilt.

### maxRollbackRetries

Controls how many times rebuild/restart is attempted before failing:
- **REBUILD_FROM_FAILED:** max times a single step can be rebuilt
- **REBUILD_ENTIRE_FLOW:** max times the entire flow can be restarted

Configured via `ConfirmationConfig.builder().maxRollbackRetries(3)`.

### Decision Guide

| Scenario | Recommended Strategy |
|----------|---------------------|
| Production, high-value transactions | `FAIL_IMMEDIATELY` |
| Simple flows, independent steps | `REBUILD_FROM_FAILED` |
| Complex flows with UTXO dependencies | `REBUILD_ENTIRE_FLOW` |
| Custom rollback logic / external coordination | `NOTIFY_ONLY` |
| Development / testing | `REBUILD_ENTIRE_FLOW` |

### Usage

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    .withConfirmationConfig(ConfirmationConfig.devnet())
    .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW);
```

> **Note:** Rollback strategies only take effect when confirmation tracking is enabled via `withConfirmationConfig()`.

---

## 7. Retry Policy

Retry policies handle transient failures during step execution (network errors, timeouts). They are distinct from rollback handling.

### Configuration

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxAttempts(5)                                    // Max attempts (default: 3)
    .backoffStrategy(BackoffStrategy.EXPONENTIAL)      // FIXED, LINEAR, EXPONENTIAL (default: EXPONENTIAL)
    .initialDelay(Duration.ofSeconds(2))               // First retry delay (default: 1s)
    .maxDelay(Duration.ofSeconds(60))                  // Delay cap (default: 30s)
    .retryOnTimeout(true)                              // Retry network timeouts (default: true)
    .retryOnNetworkError(true)                         // Retry connection errors (default: true)
    .build();
```

### BackoffStrategy

| Strategy | Delay Formula | Example (initialDelay=1s) |
|----------|---------------|---------------------------|
| `FIXED` | `initialDelay` | 1s, 1s, 1s, 1s |
| `LINEAR` | `initialDelay * attempt` | 1s, 2s, 3s, 4s |
| `EXPONENTIAL` | `initialDelay * 2^(attempt-1)` | 1s, 2s, 4s, 8s |

All strategies are capped by `maxDelay`.

### Factory Methods

```java
RetryPolicy.defaults()  // 3 attempts, exponential, 1s initial, 30s max
RetryPolicy.noRetry()   // 1 attempt (no retries)
```

### Per-Step vs Default Retry Policy

```java
// Default policy for all steps
FlowExecutor executor = FlowExecutor.create(backendService)
    .withDefaultRetryPolicy(RetryPolicy.defaults());

// Step-level override takes precedence
FlowStep criticalStep = FlowStep.builder("critical")
    .withTxContext(...)
    .withRetryPolicy(RetryPolicy.builder()
        .maxAttempts(10)
        .initialDelay(Duration.ofSeconds(5))
        .build())
    .build();
```

### What Is Retryable

| Error Type | Retryable? |
|-----------|-----------|
| Network timeout | Yes (if `retryOnTimeout` is true) |
| Connection refused / reset | Yes (if `retryOnNetworkError` is true) |
| Insufficient funds | No |
| Invalid transaction | No |
| Already spent UTXOs | No |
| Confirmation timeout | No |
| Unknown errors | Yes (by default) |

---

## 8. Flow Execution

### Creating a FlowExecutor

```java
// From BackendService (convenient)
FlowExecutor executor = FlowExecutor.create(backendService);

// From individual suppliers (loose coupling)
FlowExecutor executor = FlowExecutor.create(
    utxoSupplier,
    protocolParamsSupplier,
    transactionProcessor,
    chainDataSupplier
);
```

### Full Configuration

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    .withChainingMode(ChainingMode.SEQUENTIAL)             // Execution mode
    .withConfirmationConfig(ConfirmationConfig.devnet())    // Confirmation tracking
    .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)  // Rollback handling
    .withDefaultRetryPolicy(RetryPolicy.defaults())        // Default retry for all steps
    .withListener(new MyFlowListener())                    // Event callbacks
    .withSignerRegistry(signerRegistry)                    // For YAML/TxPlan workflows
    .withExecutor(virtualThreadExecutor)                   // Custom thread executor
    .withTxInspector(tx -> log.debug("Built: {}", tx))     // Debug transaction inspection
    .withRegistry(flowRegistry)                            // Auto-register flows
    .withStateStore(stateStore)                            // Persist state for recovery
    .withConfirmationTimeout(Duration.ofSeconds(60))       // Simple mode timeout
    .withCheckInterval(Duration.ofSeconds(2));             // Simple mode check interval
```

### Synchronous Execution

Blocks until the flow completes. Returns `FlowResult` directly.

```java
FlowResult result = executor.executeSync(flow);

if (result.isSuccessful()) {
    System.out.println("Completed in " + result.getDuration());
    result.getTransactionHashes().forEach(System.out::println);
} else {
    System.err.println("Failed: " + result.getError().getMessage());
    result.getFailedStep().ifPresent(step ->
        System.err.println("Failed at step: " + step.getStepId()));
}
```

### Asynchronous Execution

Returns a `FlowHandle` immediately for non-blocking monitoring.

```java
FlowHandle handle = executor.execute(flow);

// Monitor progress
while (handle.isRunning()) {
    System.out.printf("Progress: %d/%d (step: %s)%n",
        handle.getCompletedStepCount(),
        handle.getTotalStepCount(),
        handle.getCurrentStepId().orElse("none"));
    Thread.sleep(1000);
}

// Block until done
FlowResult result = handle.await();

// Or with timeout
FlowResult result = handle.await(Duration.ofMinutes(5));
```

### FlowHandle API

| Method | Description |
|--------|-------------|
| `getStatus()` | Current `FlowStatus` (PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED) |
| `getCurrentStepId()` | ID of the step currently executing |
| `getCompletedStepCount()` | Number of completed steps |
| `getTotalStepCount()` | Total number of steps |
| `isRunning()` | True if status is IN_PROGRESS |
| `isDone()` | True if the underlying future is complete |
| `await()` | Block until complete, return FlowResult |
| `await(Duration)` | Block with timeout |
| `getResult()` | Non-blocking: get result if available |
| `getResultFuture()` | Access the underlying CompletableFuture |
| `cancel()` | Request cancellation (in-progress steps cannot be undone) |

---

## 9. FlowResult & FlowStepResult

### FlowResult

```java
FlowResult result = executor.executeSync(flow);

result.isSuccessful();           // true if status == COMPLETED
result.isFailed();               // true if status == FAILED
result.getStatus();              // FlowStatus enum
result.getDuration();            // Duration from start to completion
result.getTransactionHashes();   // List of tx hashes from successful steps
result.getStepResults();         // List<FlowStepResult> for all steps
result.getStepResult("deposit"); // Optional<FlowStepResult> by step ID
result.getFailedStep();          // Optional<FlowStepResult> of the failed step
result.getCompletedStepCount();  // Count of successful steps
result.getTotalStepCount();      // Total step count
result.getError();               // Throwable if failed
result.getStartedAt();           // Instant when execution started
result.getCompletedAt();         // Instant when execution finished
```

### FlowStepResult

```java
FlowStepResult stepResult = result.getStepResult("deposit").orElseThrow();

stepResult.isSuccessful();        // true if completed
stepResult.getStatus();           // FlowStatus (COMPLETED, FAILED, CANCELLED)
stepResult.getStepId();           // Step ID
stepResult.getTransactionHash();  // Tx hash (null if failed)
stepResult.getOutputUtxos();      // List<Utxo> produced by this step
stepResult.getSpentInputs();      // List<TransactionInput> consumed by this step
stepResult.getError();            // Throwable if failed
stepResult.getCompletedAt();      // Instant when this step finished
```

### Error Handling Patterns

```java
FlowResult result = executor.executeSync(flow);

if (result.isFailed()) {
    // Identify which step failed
    result.getFailedStep().ifPresent(failedStep -> {
        System.err.printf("Step '%s' failed: %s%n",
            failedStep.getStepId(),
            failedStep.getError().getMessage());
    });

    // Check which steps succeeded before the failure
    for (FlowStepResult step : result.getStepResults()) {
        if (step.isSuccessful()) {
            System.out.printf("Step '%s' succeeded: %s%n",
                step.getStepId(), step.getTransactionHash());
        }
    }
}
```

---

## 10. State Persistence & Recovery

State persistence enables recovery of in-progress flows after application restarts.

### FlowStateStore Interface

Implement `FlowStateStore` to persist flow state to your storage backend (database, Redis, file, etc.):

```java
public interface FlowStateStore {
    void saveFlowState(FlowStateSnapshot snapshot);
    List<FlowStateSnapshot> loadPendingFlows();
    void updateTransactionState(String flowId, String stepId, String txHash,
                                TransactionStateDetails details);
    void markFlowComplete(String flowId, FlowStatus status);
    Optional<FlowStateSnapshot> getFlowState(String flowId);
    boolean deleteFlow(String flowId);
}
```

The executor persists state on key transitions:
- Flow started — initial state saved
- Transaction submitted — `TransactionState.SUBMITTED`
- Transaction confirmed — `TransactionState.CONFIRMED`
- Transaction rolled back — `TransactionState.ROLLED_BACK`
- Flow completed — final status

A no-op implementation (`FlowStateStore.NOOP`) is available for testing.

### FlowStateSnapshot & StepStateSnapshot

```java
// FlowStateSnapshot captures overall flow state
FlowStateSnapshot snapshot = FlowStateSnapshot.builder()
    .flowId("escrow-flow")
    .status(FlowStatus.IN_PROGRESS)
    .startedAt(Instant.now())
    .totalSteps(3)
    .completedSteps(1)
    .description("Escrow flow")
    .build();

// StepStateSnapshot captures per-step state
StepStateSnapshot stepSnapshot = StepStateSnapshot.submitted("deposit", txHash);
snapshot.addStep(stepSnapshot);
```

### Recovery on Application Startup

```java
// 1. Load pending flows from store
List<FlowStateSnapshot> pending = stateStore.loadPendingFlows();

// 2. Resume tracking for each pending flow
for (FlowStateSnapshot snapshot : pending) {
    FlowHandle handle = executor.resumeTracking(snapshot);
    registry.register(snapshot.getFlowId(), handle);
}
```

### Wiring It All Together

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    .withConfirmationConfig(ConfirmationConfig.defaults())
    .withStateStore(myStateStore)
    .withRegistry(new InMemoryFlowRegistry());

// Normal execution — state is persisted automatically
FlowHandle handle = executor.execute(flow);

// On restart — recover pending flows
List<FlowStateSnapshot> pending = myStateStore.loadPendingFlows();
for (FlowStateSnapshot snapshot : pending) {
    FlowHandle recovered = executor.resumeTracking(snapshot);
}
```

---

## 11. FlowRegistry

`FlowRegistry` provides centralized in-memory tracking of all active flows.

### Usage

```java
FlowRegistry registry = new InMemoryFlowRegistry();

FlowExecutor executor = FlowExecutor.create(backendService)
    .withRegistry(registry);  // Flows auto-register on execute()

// Query flows
registry.getActiveFlows();                        // Currently running flows
registry.getFlowsByStatus(FlowStatus.COMPLETED);  // Completed flows
registry.getFlow("escrow-flow");                   // Specific flow by ID
registry.size();                                   // Total registered flows
registry.activeCount();                            // Count of IN_PROGRESS flows
registry.contains("escrow-flow");                  // Check if registered
```

### FlowLifecycleListener

Receive notifications for registry-level events across all flows:

```java
registry.addLifecycleListener(new FlowLifecycleListener() {
    @Override
    public void onFlowRegistered(String flowId, FlowHandle handle) {
        log.info("Flow registered: {}", flowId);
    }

    @Override
    public void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {
        log.info("Flow completed: {} status={}", flowId, result.getStatus());
    }

    @Override
    public void onFlowStatusChanged(String flowId, FlowHandle handle,
                                     FlowStatus oldStatus, FlowStatus newStatus) {
        log.info("Flow {} status changed: {} -> {}", flowId, oldStatus, newStatus);
    }
});
```

> **Note:** `FlowRegistry` is in-memory only and does not survive restarts. Use `FlowStateStore` for persistence across restarts.

---

## 12. Quick Reference

### ConfirmationConfig Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `minConfirmations` | int | 10 | Blocks for CONFIRMED status (~200s on mainnet) |
| `checkInterval` | Duration | 5s | Polling interval between status checks |
| `timeout` | Duration | 30min | Max time to wait for confirmation |
| `maxRollbackRetries` | int | 3 | Max rebuild/restart attempts after rollback |
| `waitForBackendAfterRollback` | boolean | false | Wait for backend readiness after rollback |
| `postRollbackWaitAttempts` | int | 5 | Backend readiness check attempts |
| `postRollbackUtxoSyncDelay` | Duration | 0 | Extra delay for UTXO indexer sync |

### Chaining Mode Comparison

| | SEQUENTIAL | PIPELINED | BATCH |
|-|-----------|-----------|-------|
| Submit order | One at a time | One at a time | All at once |
| Confirm order | Before next submit | After all submitted | After all submitted |
| Same block possible | No | Yes | Highest likelihood |
| Default | Yes | No | No |

### Rollback Strategy Comparison

| | FAIL_IMMEDIATELY | NOTIFY_ONLY | REBUILD_FROM_FAILED | REBUILD_ENTIRE_FLOW |
|-|-----------------|-------------|---------------------|---------------------|
| Auto-recovery | No | No | Yes (single step) | Yes (full flow) |
| Default | Yes | No | No | No |
| Handles dependents | N/A | N/A | Auto-escalates to flow restart | Yes |
| Requires confirmation tracking | Yes | Yes | Yes | Yes |

### Rollback Behavior by Chaining Mode

| Aspect | SEQUENTIAL | PIPELINED | BATCH |
|--------|-----------|-----------|-------|
| Rollback scope | Per-step (REBUILD_FROM_FAILED) or full flow | Always full flow restart | Always full flow restart |
| Skip confirmed steps on restart | N/A (per-step rebuild) | Yes — checks on-chain, skips still-confirmed | Yes — checks on-chain, skips still-confirmed |
| REBUILD_FROM_FAILED behavior | Rebuilds only rolled-back step | Same as REBUILD_ENTIRE_FLOW | Same as REBUILD_ENTIRE_FLOW |
| Auto-escalation | Yes (if step has dependents) | N/A (always full restart) | N/A (always full restart) |
| Atomicity model | Per-step | Per-flow (with partial skip) | Per-flow (with partial skip) |

### FlowExecutor Configuration Methods

| Method | Description |
|--------|-------------|
| `withChainingMode(ChainingMode)` | Set execution mode (default: SEQUENTIAL) |
| `withConfirmationConfig(ConfirmationConfig)` | Enable confirmation tracking |
| `withRollbackStrategy(RollbackStrategy)` | Set rollback handling (default: FAIL_IMMEDIATELY) |
| `withDefaultRetryPolicy(RetryPolicy)` | Default retry for all steps |
| `withListener(FlowListener)` | Event callbacks |
| `withSignerRegistry(SignerRegistry)` | For YAML/TxPlan workflows |
| `withExecutor(Executor)` | Custom thread executor |
| `withTxInspector(Consumer<Transaction>)` | Debug transaction inspection |
| `withRegistry(FlowRegistry)` | Auto-register flows |
| `withStateStore(FlowStateStore)` | Persist state for recovery |
| `withConfirmationTimeout(Duration)` | Simple mode timeout (default: 60s) |
| `withCheckInterval(Duration)` | Simple mode check interval (default: 2s) |

---

## 13. See Also

- **[FLOWLISTENER_PATTERNS.md](FLOWLISTENER_PATTERNS.md)** — Listener implementation patterns, logging, metrics, alerting
- **[SPRING_BOOT_INTEGRATION.md](SPRING_BOOT_INTEGRATION.md)** — Spring Boot auto-configuration, bean wiring, profiles
- **[VIRTUAL_THREADS.md](VIRTUAL_THREADS.md)** — Java 21 virtual threads integration, executor configuration
