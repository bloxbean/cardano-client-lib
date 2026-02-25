# FlowExecutor Code Flow Documentation

## Purpose

This document provides a comprehensive walkthrough of the `FlowExecutor` implementation in the
`txflow` module. It covers all execution paths, chaining modes, rollback strategies, UTXO resolution,
confirmation tracking, retry policies, and resume capability. Use this document as a reference when
maintaining or extending the executor.

---

## 1. High-Level Architecture Overview

```
  +------------------+       +---------------+       +-------------+
  |     TxFlow       |------>| FlowExecutor  |------>| FlowResult  |
  | (flow definition)|       | (orchestrator)|       |  (outcome)  |
  +------------------+       +-------+-------+       +-------------+
         |                          |
         |  steps: List<FlowStep>   |  uses
         v                          v
  +------------------+     +---------------------------+
  |    FlowStep      |     |   ExecutionHooks          |
  | - id             |     |  (syncHooks / handleHooks)|
  | - txContextFactory     +---------------------------+
  | - txPlan         |               |
  | - dependencies   |               v
  | - retryPolicy    |     +---------------------+       +-----------------------+
  +------------------+     |  FlowExecutionContext|<----->|   FlowUtxoSupplier    |
                           | - stepResults       |       | - baseSupplier        |
                           | - spentInputs       |       | - context (pending)   |
                           | - variables          |       | - dependencies        |
                           +---------------------+       +-----------------------+
                                                                  |
                                                                  v
                                                         +------------------+
                                                         | UtxoSupplier     |
                                                         | (backend/chain)  |
                                                         +------------------+

  Async path returns:
  +------------------+
  |   FlowHandle     |
  | - status         |
  | - currentStepId  |
  | - resultFuture   |
  | - cancel()       |
  +------------------+
```

### Component Summary

| Component              | Role                                                     |
|------------------------|----------------------------------------------------------|
| `TxFlow`               | Immutable flow definition: id, variables, ordered steps  |
| `FlowStep`             | Single step: txPlan OR txContextFactory, dependencies, retryPolicy |
| `FlowExecutor`         | Orchestrates execution, manages lifecycle and rollback    |
| `FlowExecutionContext`  | Mutable state: step results, spent inputs, variables     |
| `FlowUtxoSupplier`     | Chain-aware UTXO supplier that merges pending outputs and filters spent inputs |
| `ConfirmationTracker`  | Monitors tx confirmation depth and detects rollbacks     |
| `FlowHandle`           | Async handle: status tracking, await, cancellation       |
| `FlowResult`           | Final outcome: status, step results, timing, error       |
| `FlowListener`         | Callback interface for all lifecycle events               |
| `FlowStateStore`       | Optional persistence of flow state transitions           |
| `FlowRegistry`         | Optional tracking of active async flows                  |

---

## 2. Entry Points: Sync vs Async

FlowExecutor provides four public entry points:

```
  executeSync(flow)    ──> blocking  ──> returns FlowResult
  execute(flow)        ──> async     ──> returns FlowHandle
  resumeSync(flow, previousResult)  ──> blocking  ──> returns FlowResult
  resume(flow, previousResult)      ──> async     ──> returns FlowHandle
```

### ExecutionHooks Interface

Both sync and async paths converge on the same `doExecuteX()` methods via an internal
`ExecutionHooks` interface. This eliminates code duplication between the two paths.

```
  ExecutionHooks
  ├── onFlowStarting(flow)
  ├── onStepStarting(step)
  ├── onStepCompleted(step, result)
  ├── onTransactionSubmitted(flow, step, txHash)
  ├── onTransactionConfirmed(flow, step, txHash, confirmResult)
  ├── onFlowFailed(flow, status)
  ├── onFlowCompleted(flow)
  ├── onFlowRestarting()
  ├── onRollbackDetected(flow, step, txHash, prevBlockHeight, msg)
  └── isCancelled()
```

**syncHooks** — Only calls persistence methods. `isCancelled()` always returns `false`.

**handleHooks** — Calls persistence methods AND updates `FlowHandle` state (status,
currentStep, completedStepCount). `isCancelled()` delegates to `handle.isCancelled()`.

### Execution Flow

```
  User calls executeSync(flow)
       │
       ├─ validateConfiguration()
       ├─ flow.validate()
       ├─ activeFlowIds.add(flow.getId())   ← duplicate check
       ├─ switch(chainingMode)
       │    ├─ SEQUENTIAL  → doExecuteSequential(flow, syncHooks)
       │    ├─ PIPELINED   → doExecutePipelined(flow, syncHooks)
       │    └─ BATCH       → doExecuteBatch(flow, syncHooks)
       └─ activeFlowIds.remove(flow.getId())

  User calls execute(flow)
       │
       ├─ validateConfiguration()
       ├─ activeFlowIds.add(flow.getId())
       ├─ Create FlowHandle + CompletableFuture
       ├─ Register in FlowRegistry (if configured)
       ├─ Submit Runnable to executor:
       │    ├─ handle.updateStatus(IN_PROGRESS)
       │    ├─ switch(chainingMode)
       │    │    ├─ SEQUENTIAL  → doExecuteSequential(flow, handleHooks)
       │    │    ├─ PIPELINED   → doExecutePipelined(flow, handleHooks)
       │    │    └─ BATCH       → doExecuteBatch(flow, handleHooks)
       │    ├─ activeFlowIds.remove()     ← BEFORE future.complete()
       │    └─ future.complete(result)
       └─ return handle
```

Note: `activeFlowIds.remove()` is called **before** `future.complete()` so that callers
unblocked by `await()` can immediately re-execute the same flow ID.

---

## 3. Chaining Modes

### 3.1 SEQUENTIAL (Default)

Each step waits for on-chain confirmation before the next step begins.
Transactions are guaranteed to be in **separate blocks**.

```
  Step 1            Step 2            Step 3
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │ Build    │      │ Build    │      │ Build    │
  │ Sign     │      │ Sign     │      │ Sign     │
  │ Submit   │      │ Submit   │      │ Submit   │
  │ Wait ◆◆◆ │      │ Wait ◆◆◆ │      │ Wait ◆◆◆ │
  │ Confirmed│──────│ Confirmed│──────│ Confirmed│
  └──────────┘      └──────────┘      └──────────┘
     Block N          Block N+k         Block N+k+j

  ◆◆◆ = polling until confirmed (completeAndWait + optional deep confirmation)
```

**Key method:** `doExecuteSequential(flow, hooks)`

Per-step flow:
1. `hooks.onStepStarting(step)` + `listener.onStepStarted()`
2. `executeStepWithRollbackHandling()` → `executeStepWithRetry()` → `executeStepSequential()`
3. Inside `executeStepSequential()`:
   - Create `FlowUtxoSupplier` wrapping `baseUtxoSupplier`
   - Create `QuickTxBuilder` with the flow UTXO supplier
   - Build `TxContext` via `txContextFactory` or `TxPlan`
   - Attach `txInspector` to capture the built `Transaction`
   - Call `txContext.completeAndWait()` — this builds, signs, submits, and waits for basic confirmation
   - Capture output UTXOs and spent inputs from the built transaction
   - If `confirmationTracker` is configured, call `waitForConfirmation()` for deep confirmation
   - Record result in context

### 3.2 PIPELINED

All transactions are built and submitted without waiting for confirmations between steps.
Subsequent steps use **expected outputs** captured from the built transaction.
Confirmations are collected **after** all submissions. Transactions can land in the **same block**.

```
  Phase 1: Build + Submit All           Phase 2: Confirm All
  ┌────────┐ ┌────────┐ ┌────────┐     ┌────────────────────────┐
  │Build S1│ │Build S2│ │Build S3│     │ Wait for S1 confirm    │
  │Submit  │ │Submit  │ │Submit  │     │ Wait for S2 confirm    │
  └────────┘ └────────┘ └────────┘     │ Wait for S3 confirm    │
     │           │           │          └────────────────────────┘
     │ capture   │ capture   │ capture
     │ outputs   │ outputs   │ outputs
     │     ╰─────╯     ╰─────╯
     │  pending UTXOs flow forward
     │  via FlowExecutionContext

  Potentially: Block N contains S1, S2, S3 together
```

**Key method:** `doExecutePipelined(flow, hooks)`

Two-phase flow:
1. **Phase 1: Build + Submit** — For each step, `executeStepPipelined()`:
   - Same UTXO supplier setup as SEQUENTIAL
   - Call `txContext.complete()` — builds, signs, submits but does NOT wait
   - Capture outputs/inputs from the built tx BEFORE confirmation
   - Record in context (so next step can use them)
2. **Phase 2: Confirm** — For each submitted tx:
   - `waitForConfirmation(txHash, step, cancelCheck)`
   - Fire step completed callbacks

### 3.3 BATCH

Maximum same-block likelihood. Three strict phases:
1. **Build ALL** transactions (compute tx hashes client-side via Blake2b256)
2. **Submit ALL** transactions in rapid succession (within milliseconds)
3. **Wait for ALL** confirmations

```
  Phase 1: Build All     Phase 2: Submit All     Phase 3: Confirm All
  ┌─────────────────┐    ┌─────────────────┐     ┌─────────────────┐
  │ Build S1        │    │ Submit S1       │     │ Confirm S1      │
  │ Build S2        │    │ Submit S2       │     │ Confirm S2      │
  │ Build S3        │    │ Submit S3       │     │ Confirm S3      │
  └─────────────────┘    └─────────────────┘     └─────────────────┘
        │                       │                       │
        │ hash = Blake2b256(tx) │ verify hash match     │ deep confirm
        │ capture outputs       │ if mismatch → FAIL    │
        │ record in context     │                       │
```

**Key method:** `doExecuteBatch(flow, hooks)`

Phase 1 uses `buildStepOnly(step, context, variables)`:
- Builds and signs via `txContext.buildAndSign()` — does NOT submit
- Computes tx hash client-side: `TransactionUtil.getTxHash(tx)`
- Captures outputs/inputs, records in context

Phase 2 uses `submitTransaction(tx)`:
- Serializes the pre-built transaction
- Submits via `transactionProcessor.submitTransaction()`
- **Verifies** the returned hash matches the pre-computed hash (fails on mismatch)

### Chaining Mode Comparison

| Aspect                    | SEQUENTIAL            | PIPELINED              | BATCH                    |
|---------------------------|-----------------------|------------------------|--------------------------|
| Submit timing             | After prev confirmed  | Immediately after build| All at once after build   |
| Same-block possible?      | No (separate blocks)  | Yes (likely)           | Yes (maximum likelihood)  |
| UTXO source for next step | On-chain (confirmed)  | Pending (from context) | Pending (from context)    |
| Failure cascade           | Isolated              | Downstream may fail    | Downstream may fail       |
| Phase count               | 1 (per step)          | 2 (submit all, confirm all) | 3 (build, submit, confirm) |
| Tx hash known before submit| After submit         | After submit           | **Before submit** (client-side) |

---

## 4. UTXO Dependency Resolution

### FlowUtxoSupplier

`FlowUtxoSupplier` wraps the `baseUtxoSupplier` and adds two capabilities:

1. **Spent UTXO filtering** — Removes UTXOs consumed by previous steps
2. **Pending output injection** — Adds unconfirmed outputs from dependent steps

```
  getPage(address, ...)
       │
       ├─ baseSupplier.getPage(address, ...)     ← fetch from chain/backend
       │       │
       │       └─ filterOutSpentUtxos(baseUtxos)  ← remove UTXOs spent by earlier steps
       │              │
       │              └─ context.getAllSpentInputs() → compare txHash+outputIndex
       │
       └─ resolvePendingUtxosForAddress(address)  ← add unconfirmed outputs from deps
              │
              └─ for each StepDependency:
                    dependency.resolveUtxos(context)  → apply SelectionStrategy
                    filter by address match
```

### When FlowUtxoSupplier is Created

```java
private UtxoSupplier createUtxoSupplier(FlowStep step, FlowExecutionContext context) {
    if (!step.hasDependencies() && context.getCompletedStepCount() == 0) {
        return baseUtxoSupplier;  // First step, no deps → no wrapping needed
    }
    // Always wrap to filter spent UTXOs (even without explicit deps)
    return new FlowUtxoSupplier(baseUtxoSupplier, context, step.getDependencies());
}
```

Even steps without explicit dependencies get wrapped after the first step completes,
to prevent double-spend when steps share the same address (especially in PIPELINED/BATCH).

### SelectionStrategy

| Strategy | Behavior                                          |
|----------|---------------------------------------------------|
| `ALL`    | Returns all outputs from the dependent step       |
| `INDEX`  | Returns a single output at the specified index    |
| `FILTER` | Returns outputs matching a `Predicate<Utxo>`      |

### StepDependency Resolution

```
  StepDependency.resolveUtxos(context)
       │
       ├─ context.getStepOutputs(stepId)  ← get outputs from completed step
       └─ selectUtxos(outputs)
            ├─ ALL    → return all
            ├─ INDEX  → return outputs.get(utxoIndex)
            └─ FILTER → apply filterPredicate to each output
```

---

## 5. Confirmation Tracking

### Two Modes

**Simple mode** (no `ConfirmationConfig` set):
- Polls `chainDataSupplier.getTransactionInfo(txHash)` in a loop
- Returns as soon as the tx is found (regardless of depth)
- No rollback detection

**Enhanced mode** (with `ConfirmationConfig` + `ConfirmationTracker`):
- Tracks confirmation depth: `tipHeight - txBlockHeight`
- Detects rollbacks: previously-tracked tx disappears from chain
- Fires granular listener callbacks (IN_BLOCK, depth changes)
- Supports rollback strategies

### ConfirmationStatus Progression

```
                    ┌──────────────────────────────────────────────┐
                    │                                              │
  SUBMITTED ──────> IN_BLOCK ──────> CONFIRMED                    │
  (in mempool)    (depth < min)    (depth >= minConfirmations)    │
       │              │                                           │
       │              └──────────> ROLLED_BACK  <─────────────────┘
       │                          (was in chain,
       │                           now missing)
       └──────────────────────────────────────────> (timeout)
```

### ConfirmationTracker.checkStatus(txHash)

```
  1. Get chain tip height: chainDataSupplier.getChainTipHeight()
  2. Get transaction info: chainDataSupplier.getTransactionInfo(txHash)
  3. If tx NOT found:
       If previously tracked with blockHeight → ROLLED_BACK
       Else → SUBMITTED
  4. If tx found:
       depth = tipHeight - txBlockHeight
       If depth < minConfirmations → IN_BLOCK
       If depth >= minConfirmations → CONFIRMED
  5. Update TrackedTransaction in ConcurrentMap
  6. Return ConfirmationResult
```

### waitForConfirmation() — Simple vs Enhanced

```
  waitForConfirmation(txHash, step, cancelCheck)
       │
       ├─ confirmationTracker != null?
       │    ├─ YES → waitForConfirmationWithTracking(txHash, step, cancelCheck)
       │    │         │
       │    │         ├─ confirmationTracker.waitForConfirmation(txHash, CONFIRMED, onProgress, cancelCheck)
       │    │         │    └─ Polls checkStatus() every checkInterval until target reached/timeout
       │    │         │
       │    │         └─ Handle result:
       │    │              ├─ CONFIRMED → return Optional.of(result)
       │    │              ├─ ROLLED_BACK → apply rollbackStrategy (see Section 6)
       │    │              └─ timeout/error → return Optional.empty()
       │    │
       │    └─ NO  → Simple polling loop:
       │              while (not timed out):
       │                chainDataSupplier.getTransactionInfo(txHash)
       │                if present → return ConfirmationResult(CONFIRMED)
       │                sleep(checkInterval)
       │              return Optional.empty()
```

---

## 6. Rollback Strategies

Rollback strategies only take effect when `ConfirmationConfig` is set (enhanced mode).
Validated at startup: non-FAIL_IMMEDIATELY strategies require `confirmationTracker != null`.

### 6.1 FAIL_IMMEDIATELY (Default)

```
  Rollback detected
       │
       ├─ listener.onTransactionRolledBack(step, txHash, prevHeight)
       ├─ waitForConfirmationWithTracking() returns Optional.empty()
       └─ Step fails → Flow fails
```

Simplest and safest. Application handles retry externally.

### 6.2 NOTIFY_ONLY

```
  Rollback detected
       │
       ├─ listener.onTransactionRolledBack(step, txHash, prevHeight)
       ├─ notifyOnlyRepolls++
       ├─ if notifyOnlyRepolls > maxRollbackRetries → return empty (fail)
       ├─ confirmationTracker.stopTracking(txHash)  ← clear stale state
       ├─ reset firstBlockHeight, lastStatus
       └─ continue (re-enter while loop) ← re-poll from SUBMITTED status
                                            tx may be re-included from mempool
```

### 6.3 REBUILD_FROM_FAILED

```
  Rollback detected during waitForConfirmationWithTracking()
       │
       └─ throw RollbackException.forStepRebuild(txHash, step, prevHeight)
              │
              └─ Caught by executeStepWithRollbackHandling():
                    │
                    ├─ hasDownstreamDependents(step)?
                    │    ├─ YES → Auto-escalate: throw RollbackException.forFlowRestart()
                    │    │         (downstream steps used this step's outputs — must restart)
                    │    └─ NO  → Continue with step rebuild
                    │
                    ├─ stepRollbackAttempts++ for this step
                    ├─ if attempts > maxRollbackRetries → throw (propagate as failure)
                    ├─ listener.onStepRebuilding(step, attempt, max, reason)
                    ├─ waitForBackendReadyAfterRollback()
                    ├─ context.clearStepResult(step.getId())
                    └─ continue (loop retries the step)
```

**Auto-escalation check:**
```java
boolean hasDownstreamDependents(FlowStep step, List<FlowStep> allSteps) {
    // Checks if any step AFTER this one has a dependency on this step's ID
    // If yes → rebuilding only this step is insufficient
}
```

### 6.4 REBUILD_ENTIRE_FLOW

```
  Rollback detected during waitForConfirmationWithTracking()
       │
       └─ throw RollbackException.forFlowRestart(txHash, step, prevHeight)
              │
              └─ Caught by doExecuteSequential/Pipelined/Batch outer try-catch:
                    │
                    ├─ hooks.onRollbackDetected(...)
                    ├─ flowRestartAttempts++
                    ├─ if attempts > maxRollbackRetries → fail
                    ├─ listener.onFlowRestarting(flow, attempt, max, reason)
                    │
                    ├─ [PIPELINED/BATCH only]:
                    │    previousConfirmedSteps = findStillConfirmedSteps()
                    │    (check which earlier txs are still on-chain → skip on restart)
                    │
                    ├─ waitForBackendReadyAfterRollback(flowTxHashes)
                    ├─ stepRollbackAttempts.clear()
                    ├─ hooks.onFlowRestarting()
                    └─ continue (outer while loop restarts from step 0)
```

### Rollback Strategy Comparison

```
  ┌─────────────────────┬─────────────────────────────────────────────────┐
  │ FAIL_IMMEDIATELY    │  Detect → Notify → Fail flow                   │
  ├─────────────────────┼─────────────────────────────────────────────────┤
  │ NOTIFY_ONLY         │  Detect → Notify → Re-poll (up to max retries) │
  ├─────────────────────┼─────────────────────────────────────────────────┤
  │ REBUILD_FROM_FAILED │  Detect → Notify → Clear step → Rebuild step   │
  │                     │  (auto-escalate to flow restart if downstream   │
  │                     │   steps depend on this step's outputs)          │
  ├─────────────────────┼─────────────────────────────────────────────────┤
  │ REBUILD_ENTIRE_FLOW │  Detect → Notify → Clear all → Restart flow    │
  │                     │  (PIPELINED/BATCH: skip still-confirmed steps)  │
  └─────────────────────┴─────────────────────────────────────────────────┘
```

### waitForBackendReadyAfterRollback()

Called after every rollback detection before retrying:

```
  1. Clear this flow's tracked transactions in ConfirmationTracker
     (scoped to flowTxHashes — does NOT clear other concurrent flows)

  2. If confirmationConfig.waitForBackendAfterRollback == false (production default):
       → return immediately (no wait needed)

  3. If true (devnet/test — rollback simulation may restart node):
       → waitForBackendReady(maxAttempts, retryDelay):
           polls chainDataSupplier.getChainTipHeight() until success
       → sleep(postRollbackUtxoSyncDelay) for UTXO indexer sync
```

---

## 7. Step Retry Policy

Retry handles transient failures during step execution (network errors, timeouts).
This is separate from rollback handling.

### RetryPolicy Configuration

| Parameter         | Default       | Description                              |
|-------------------|---------------|------------------------------------------|
| `maxAttempts`     | 3             | Total attempts (1 = no retry)            |
| `backoffStrategy` | EXPONENTIAL   | FIXED, LINEAR, or EXPONENTIAL            |
| `initialDelay`    | 1 second      | Delay before first retry                 |
| `maxDelay`        | 30 seconds    | Cap on delay growth                      |
| `retryOnTimeout`  | true          | Retry on network timeout errors          |
| `retryOnNetworkError` | true      | Retry on connection/socket errors        |

### Backoff Calculation

```
  FIXED:       delay = initialDelay
  LINEAR:      delay = initialDelay * attemptNumber
  EXPONENTIAL: delay = initialDelay * 2^(attemptNumber - 1)

  Always capped at maxDelay.
```

### Retry Flow

```
  executeStepWithRetry(step, context, variables, pipelined, cancelCheck)
       │
       ├─ Determine policy: step.retryPolicy ?? defaultRetryPolicy
       ├─ maxAttempts = policy != null ? policy.maxAttempts : 1
       │
       └─ for attempt = 1 to maxAttempts:
            │
            ├─ executeStepSequential() or executeStepPipelined()
            │
            ├─ if result.isSuccessful() → return result
            │
            ├─ if !policy.isRetryable(error) || attempt >= maxAttempts:
            │    ├─ listener.onStepRetryExhausted() (if attempt > 1)
            │    └─ return result (failure)
            │
            ├─ listener.onStepRetry(step, attempt, maxAttempts, error)
            ├─ if cancelled → return failure
            └─ Thread.sleep(policy.calculateDelay(attempt))
```

### Retryable vs Non-Retryable Errors

**Non-retryable** (permanent failures):
- "insufficient" funds
- "invalid" transaction
- "already spent" UTXOs
- "bad request"
- `ConfirmationTimeoutException` (tx may still confirm later — don't resubmit)

**Retryable** (transient failures):
- "timeout" (network/connection, NOT confirmation)
- "connection", "network", "socket", "reset", "refused"
- Unknown errors (default: retry)

**Never retried**: `java.lang.Error` subclasses (OutOfMemoryError, etc.)

---

## 8. Resume from Failure

Resume allows re-executing a failed flow by skipping steps that completed successfully
in a previous run. Works across all three chaining modes.

### Resume Flow

```
  resumeSync(flow, previousResult) / resume(flow, previousResult)
       │
       ├─ validateConfiguration()
       ├─ flow.validate()
       ├─ validateResumeArgs(flow, previousResult):
       │    ├─ previousResult != null
       │    ├─ flow.id == previousResult.flowId
       │    └─ !previousResult.isSuccessful()  ← nothing to resume if already succeeded
       │
       ├─ verifyPreviousSteps(flow, previousResult):
       │    │
       │    │  For each step (in order):
       │    │    ├─ Get previousResult.getStepResult(step.id)
       │    │    ├─ If present and successful:
       │    │    │    ├─ chainDataSupplier.getTransactionInfo(txHash)
       │    │    │    ├─ If still on-chain (blockHeight != null):
       │    │    │    │    confirmedSteps.put(index, result)
       │    │    │    └─ If NOT on-chain → BREAK (stop verification)
       │    │    └─ If not present or not successful → BREAK
       │    │
       │    └─ Returns: Map<Integer, FlowStepResult>  (contiguous prefix only)
       │
       ├─ switch(chainingMode):
       │    ├─ SEQUENTIAL  → doExecuteSequentialWithResume(flow, hooks, confirmedSteps)
       │    ├─ PIPELINED   → doExecutePipelinedWithResume(flow, hooks, confirmedSteps)
       │    └─ BATCH       → doExecuteBatchWithResume(flow, hooks, confirmedSteps)
       │
       └─ Each *WithResume method:
            ├─ verifyAndPrepareSkippedSteps():
            │    Re-verify confirmed steps are still on-chain
            │    Pre-populate context with verified step results
            │    Return skippedStepIndices
            ├─ For skipped steps: reuse previous result, skip build/submit
            └─ For remaining steps: execute normally
```

### Contiguous Prefix Rule

Resume only skips a **contiguous prefix** of verified steps. If step 3 failed but
step 2's tx is no longer on-chain, verification stops at step 2 — step 1 is skipped
but steps 2+ are re-executed.

```
  Previous result:  S1=OK  S2=OK  S3=OK  S4=FAILED
  Verification:     S1=✓   S2=✓   S3=✗   S4=skip
  Resume from:                     S3 (S1, S2 skipped)
```

### UTXO Correctness on Resume

- Steps **without** dependencies on skipped steps fetch fresh UTXOs from the blockchain
  (where skipped steps' consumed UTXOs are already absent — they were consumed in the
  original run and confirmed on-chain)
- Steps **with** dependencies resolve from context, which contains real on-chain UTXOs
  from the skipped steps

---

## 9. FlowHandle (Async Monitoring)

`FlowHandle` is returned by `execute()` and `resume()` for non-blocking monitoring.

### API

| Method                  | Returns                     | Description                           |
|-------------------------|-----------------------------|---------------------------------------|
| `getStatus()`           | `FlowStatus`                | Current: PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED |
| `getCurrentStepId()`    | `Optional<String>`          | Step currently being executed          |
| `getCompletedStepCount()`| `int`                      | Number of steps finished               |
| `getTotalStepCount()`   | `int`                       | Total steps in the flow                |
| `isRunning()`           | `boolean`                   | True if status == IN_PROGRESS          |
| `isDone()`              | `boolean`                   | True if future is complete             |
| `await()`               | `FlowResult`                | Block until complete                   |
| `await(timeout)`        | `FlowResult`                | Block with timeout                     |
| `getResult()`           | `Optional<FlowResult>`      | Non-blocking: empty if still running   |
| `getResultFuture()`     | `CompletableFuture<FlowResult>` | Underlying future                 |
| `cancel()`              | `boolean`                   | Request cancellation                   |

### Cancellation

When `cancel()` is called:
1. `cancelled = true` flag set
2. `currentStatus = CANCELLED`
3. `resultFuture.cancel(true)` — interrupts waiting thread

The executor checks `hooks.isCancelled()` (which delegates to `handle.isCancelled()`):
- Before each step
- Before retry waits
- During confirmation polling (passed as `BooleanSupplier cancelCheck`)

---

## 10. Configuration Reference

### ConfirmationConfig Presets

| Preset         | minConfirmations | checkInterval | timeout   | waitForBackend | postRollbackWait | utxoSyncDelay |
|----------------|------------------|---------------|-----------|----------------|------------------|---------------|
| `defaults()`   | 10               | 5s            | 30 min    | false          | 5 attempts       | 0s            |
| `testnet()`    | 6                | 3s            | 10 min    | false          | 5 attempts       | 0s            |
| `devnet()`     | 3                | 1s            | 5 min     | true           | 30 attempts      | 3s            |
| `quick()`      | 1                | 1s            | 2 min     | true           | 30 attempts      | 3s            |

### FlowExecutor Builder Options

```java
FlowExecutor executor = FlowExecutor.create(backendService)
    // or: FlowExecutor.create(utxoSupplier, protocolParamsSupplier, transactionProcessor, chainDataSupplier)
    .withSignerRegistry(registry)          // for TxPlan/YAML workflows
    .withListener(listener)                // lifecycle callbacks
    .withExecutor(customExecutor)          // custom thread pool (default: virtual threads on Java 21+)
    .withTxInspector(tx -> ...)            // inspect built transactions
    .withChainingMode(ChainingMode.PIPELINED)
    .withDefaultRetryPolicy(RetryPolicy.defaults())
    .withConfirmationConfig(ConfirmationConfig.devnet())
    .withRollbackStrategy(RollbackStrategy.REBUILD_ENTIRE_FLOW)
    .withRegistry(flowRegistry)            // track active flows
    .withStateStore(stateStore);           // persist flow state
```

### FlowListener Callback Reference

| Callback                       | When fired                                              |
|--------------------------------|---------------------------------------------------------|
| `onFlowStarted(flow)`         | Flow execution begins                                   |
| `onFlowCompleted(flow, result)`| Flow completed successfully                            |
| `onFlowFailed(flow, result)`  | Flow failed or cancelled                                |
| `onFlowRestarting(flow, attempt, max, reason)` | REBUILD_ENTIRE_FLOW restart beginning |
| `onStepStarted(step, index, total)` | Step execution begins                             |
| `onStepCompleted(step, result)` | Step completed successfully                           |
| `onStepFailed(step, result)`   | Step failed                                            |
| `onTransactionSubmitted(step, txHash)` | Transaction submitted to mempool               |
| `onTransactionConfirmed(step, txHash)` | Transaction confirmed on-chain                 |
| `onTransactionInBlock(step, txHash, blockHeight)` | First seen in a block (enhanced mode) |
| `onConfirmationDepthChanged(step, txHash, depth, status)` | Confirmation depth progressed |
| `onTransactionRolledBack(step, txHash, prevBlockHeight)` | Rollback detected           |
| `onStepRetry(step, attempt, max, error)` | Before retry attempt                       |
| `onStepRetryExhausted(step, total, error)` | All retries exhausted                    |
| `onStepRebuilding(step, attempt, max, reason)` | REBUILD_FROM_FAILED rebuilding step   |

Listeners are wrapped in `CompositeFlowListener` which catches and logs exceptions from
individual listeners, preventing buggy callbacks from crashing flow execution.

---

## 11. State Persistence

When a `FlowStateStore` is configured, the executor persists state at key transitions:

| Event                 | Persisted State               | Method Called                |
|-----------------------|-------------------------------|-----------------------------|
| Flow started          | `FlowStateSnapshot` (IN_PROGRESS) | `persistFlowStarted()`   |
| Transaction submitted | `TransactionStateDetails` (SUBMITTED) | `persistTransactionSubmitted()` |
| Transaction confirmed | `TransactionStateDetails` (CONFIRMED, blockHeight, depth) | `persistTransactionConfirmed()` |
| Transaction rolled back| `TransactionStateDetails` (ROLLED_BACK, prevHeight, reason) | `persistTransactionRolledBack()` |
| Flow completed        | `FlowStatus` (COMPLETED/FAILED) | `persistFlowComplete()` |

All persistence calls are wrapped in try-catch — persistence failures are logged but
do not abort flow execution.

---

## 12. Thread Safety and Concurrency

- `FlowExecutor` fields are `volatile` for safe publication across threads
- `FlowExecutionContext` uses `ConcurrentHashMap` for step results
- `activeFlowIds` and `activeHandles` are `ConcurrentHashMap.newKeySet()` backed sets
- Duplicate flow ID detection: `activeFlowIds.add(id)` returns false if already present
- Default executor: virtual threads (Java 21+) or cached daemon thread pool (Java 11-20)
- `ConfirmationTracker.trackedTransactions` is a `ConcurrentMap`
- `FlowHandle` fields use `volatile` and `AtomicInteger` for thread-safe status reporting

---

## 13. End-to-End Example: 3-Step PIPELINED Flow with REBUILD_ENTIRE_FLOW

```
  User creates:
    TxFlow "escrow-flow"
      Step 1: "deposit"    — pay to script address
      Step 2: "release"    — collect from script (dependsOn "deposit")
      Step 3: "fee-refund" — refund fees (dependsOn "deposit")

  FlowExecutor configured:
    chainingMode = PIPELINED
    rollbackStrategy = REBUILD_ENTIRE_FLOW
    confirmationConfig = ConfirmationConfig.devnet()

  Execution:

  1. execute(flow) → returns FlowHandle

  2. PIPELINED Phase 1: Build + Submit
     S1: Build with baseUtxoSupplier → submit → capture outputs [O1a, O1b]
     S2: Build with FlowUtxoSupplier(base + pending from S1) → submit
         FlowUtxoSupplier resolves "deposit" outputs [O1a, O1b] for script address
     S3: Build with FlowUtxoSupplier(base + pending from S1) → submit
         Uses O1b (fee change output) via dependsOn("deposit")

  3. PIPELINED Phase 2: Confirm
     Wait for S1 tx confirmation → depth reaches minConfirmations → CONFIRMED
     Wait for S2 tx confirmation → CONFIRMED
     Wait for S3 tx confirmation → ROLLED_BACK detected!
       │
       └─ REBUILD_ENTIRE_FLOW strategy:
            throw RollbackException.forFlowRestart()
            │
            └─ Caught at doExecutePipelined outer catch:
                 flowRestartAttempts = 1
                 findStillConfirmedSteps() → S1 still confirmed, S2 not
                 waitForBackendReadyAfterRollback()
                 RESTART (continue outer while loop)

  4. Restart iteration 1:
     verifyAndPrepareSkippedSteps() → S1 still confirmed → skip
     S2: Rebuild with fresh UTXOs + S1 context
     S3: Rebuild with fresh UTXOs + S1 context
     Submit S2, S3
     Confirm all → SUCCESS

  5. FlowResult returned with status=COMPLETED
```
