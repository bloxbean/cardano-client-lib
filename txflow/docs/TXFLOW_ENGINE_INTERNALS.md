# FlowEngine Internals — Design & Maintainer Guide

**Audience:** CCL maintainers working on the durable execution engine
(`exec/FlowEngine.java` and its collaborators). This is the code-level companion to
[DURABLE_RUNTIME.md](DURABLE_RUNTIME.md) (the store contract in outline) and the
engine-side counterpart of [TXSTREAM_INTERNALS.md](TXSTREAM_INTERNALS.md). For the
portable executor underneath (chaining modes, UTXO resolution, rollback strategies),
see [Flowexecutor-code-flow.md](Flowexecutor-code-flow.md) — none of that is repeated
here.

This document is also the **canonical home of the uncertain-disposition contract**
(§7): other docs summarize it and link here.

---

## 1. What FlowEngine adds on top of FlowExecutor

`FlowExecutor` executes one flow, in-process, with no memory of the attempt once the
JVM dies. `FlowEngine` wraps it with everything needed to make execution **safe to
retry and safe to crash**:

```
FlowEngine.start(request)
  │  compile + validate            (fail before any claim exists)
  │  idempotency claim             (exactly-once identity)
  │  execution + resource leases   (single-writer, fencing epochs)
  │  write-ahead journal           (observable, replayable history)
  ▼
FlowExecutor.executeSync(plan)     (build → sign → submit → confirm)
  │  DurableExecutionPersistence   (attempt lifecycle persisted at each boundary)
  ▼
state mapping + elevation          (uncertain ⇒ RECOVERY_REQUIRED, never FAILED)
  │  terminal persist under fence
  ▼
FlowExecutionResult                (state, steps, attempts, FlowError)
```

The engine runs in two modes:

- **Durable** (`store != null`): claims, leases, journal, and attempts live in a
  `FlowExecutionStore` (see `txflow-store-rdbms`). Crash recovery is possible.
- **Non-durable**: in-memory only. Keyed requests get an in-memory claim map;
  keyless requests get no claim at all — which is why `retryable` is stricter there
  (§7).

## 2. Source map

| File (under `txflow/src/main/java/.../txflow/`) | Role |
|---|---|
| `exec/FlowEngine.java` | `start()` / `executeSync()` / `preflight()` / `recover()`; claim + lease + journal orchestration; state mapping |
| `exec/FlowExecutionRequest.java`, `FlowExecutionResult.java`, `FlowExecutionHandle.java` | Request/result/handle surface |
| `exec/ExecutionRequestFingerprinter.java` | Request fingerprint = compiled fingerprint + spending resources + concurrency opt-out + persisted bindings + secure refs |
| `exec/ExecutionJournalSession.java` | Buffers `FlowEvent`s (`record`) and appends them with the snapshot mutation under the fence (`persist`) |
| `exec/DurableLeaseGuard.java` | Acquires/renews execution + resource leases; exposes `fence()`, `checkHealthy()`, `hasFailed()` |
| `exec/SpendingResourceCoordinator.java` | In-process serialization queue per spending resource (before the durable resource lease) |
| `exec/DurableExecutionPersistence.java` | `PersistencePort` implementation: persists the `AttemptState` lifecycle + transaction journal events under the fence |
| `exec/StepRunner.java` | One step's attempt/retry loop; the uncertain-submission special path |
| `exec/ConfirmationTracker.java`, `ConfirmationOutcome.java` | Confirmation polling and outcome typing |
| `recovery/FlowRecoveryCoordinator.java` | Observe-or-identically-resubmit reconciliation of one uncertain attempt |
| `store/FlowExecutionStore.java`, `store/MutationFence.java`, `store/FlowAttemptSnapshot.java`, `store/AttemptState.java` | Store SPI, fencing proof, attempt records |

## 3. Life of an execution — `start()`

`FlowEngine.start(request)` in order (`FlowEngine.java`, `start` → `createHandle` →
`run`):

1. **Compile** the definition with bindings/resources/policy. Diagnostics →
   `TXFLOW_COMPILATION_FAILED` as an already-completed handle. *Nothing is claimed or
   persisted for a request that fails validation.*
2. **Durable-only validation**: sensitive parameters must use secure binding
   references (`TXFLOW_SECURE_BINDING_REFERENCE_REQUIRED` / `_INVALID`) — plaintext
   secrets must never reach the store; persisted bindings store a SHA-256 and `***`
   for sensitive values.
3. **Policy gate**: concurrent-spending opt-out vs server policy
   (`TXFLOW_CONCURRENT_SPENDING_FORBIDDEN`).
4. **Request fingerprint** (`ExecutionRequestFingerprinter`): identity of *what would
   run*. Two requests with the same claim identity but different fingerprints are a
   conflict, never a match.
5. **Claim identity** (`claimIdentity()`): the idempotency key + namespace when
   provided, otherwise the execution id under an internal claim domain — so in
   durable mode **every** execution has a claim, keyed or not.
6. **Claim check** — see §4.
7. **Handle creation**: journal `EXECUTION_CREATED` + `COMPILATION_COMPLETED`,
   register in `activeExecutions`, dispatch `run()` on the caller-owned executor
   (`TXFLOW_EXECUTOR_REJECTED` if it refuses; even that failure is journalled and
   persisted under a lease when durable).

### `run()` — the execution task

1. Acquire the **execution lease** and start renewal (renewal begins before the
   in-process spending queue wait, which may outlast a lease TTL).
2. **In-process spending queue** (`SpendingResourceCoordinator.acquire`): serializes
   flows contending for the same spending resources inside this JVM (journals
   `EXECUTION_QUEUED`). Cancellation is honored at every wait point.
3. Acquire **durable resource leases**, sorted by identity (deterministic order —
   prevents AB/BA deadlocks across processes).
4. Journal `EXECUTION_STARTED`, persist state `RUNNING`.
5. Build a `FlowExecutor` facade wired with:
   - the engine's **journal listener** (§6), and
   - `DurableExecutionPersistence` as the executor's `PersistencePort` (durable only).
6. `executeSync(plan, cancelCheck)` — where `cancelCheck` is
   `cancelled || leases.hasFailed()`: **a lost lease aborts the flow from the
   inside**, not just at the end.
7. **State mapping + elevation** (§7) → build `FlowError` → persist terminal state
   under the fence → complete the handle's future.
8. `finally`: release leases, close the spending acquisition, deregister.

The catch path mirrors the same discipline: lease contention →
`TXFLOW_RESOURCE_BUSY`; renewal failure → `TXFLOW_LEASE_RENEWAL_FAILED`; store
failures keep their code; anything whose terminal state cannot be persisted becomes
`RECOVERY_REQUIRED` (`TXFLOW_TERMINAL_PERSISTENCE_FAILED`, retryable) rather than a
silently-lost verdict.

## 4. Exactly-once: claims

**Durable mode** — `store.createOrGet(namespace, key, initialSnapshot)` is the
authoritative claim (`createHandle`):

| Claim outcome | Behavior |
|---|---|
| Created | Fresh execution; snapshot starts at `CREATED` with spending resources, concurrency flag, and persisted bindings |
| Exists, same fingerprint, active in this process | Return the **same live handle** |
| Exists, different fingerprint | `TXFLOW_EXECUTION_ID_CONFLICT` / `TXFLOW_IDEMPOTENCY_CONFLICT` — never attach to another caller's execution |
| Exists, not active here (**MATCHED**) | `handleForStoredSnapshot`: terminal snapshot → its state + `TXFLOW_STORED_EXECUTION_TERMINAL`; non-terminal snapshot → `RECOVERY_REQUIRED` + `TXFLOW_RECOVERY_REQUIRED` (retryable) |

> **MATCHED results carry empty `steps()`/`attempts()`.** Read details from
> `store.get(executionId)` — every consumer (including TxStream's `SnapshotLookup`)
> must know this.

**The busy-claim void (P3):** if a fresh durable execution fails with
`TXFLOW_RESOURCE_BUSY` *after* acquiring its execution lease, its just-created claim
is deleted (`store.deleteExecution`) after leases release. Otherwise a same-key retry
would MATCH a permanently-stored failure forever. Execution-lease contention is
excluded — that claim belongs to another live owner.

**Non-durable mode** — keyed requests use the in-memory `idempotencyClaims` map
(fingerprint match → same handle; mismatch → conflict; bounded by
`maxInMemoryIdempotencyClaims` → `TXFLOW_IDEMPOTENCY_CAPACITY_EXCEEDED`). The map
**retains claims after completion** — that retention is what makes a keyed retry safe.
Keyless non-durable requests are deduplicated only while active (`activeExecutions`);
after completion a re-`start()` is a fresh execution. §7's `retryable` rule follows
directly from this.

## 5. Leases, epochs, fencing

- The **fencing epoch** is minted monotonically at lease acquisition (backed by the
  `txflow_lease_epoch` singleton in the RDBMS store); renewal keeps the epoch, expiry
  or release never lets the old owner write again.
- `DurableLeaseGuard` owns acquisition and background renewal (on the maintenance
  executor) for the execution lease and each resource lease, and produces the
  **`MutationFence`** — execution lease + all resource leases as one composite proof.
- Every durable write goes through `store.append(executionId, expectedRevision,
  fence, events, snapshotMutation)`: the store validates owner, epoch, expiry, and
  execution binding of the complete fence **atomically** with the optimistic revision
  check and the journal append. A zombie process that lost its lease cannot corrupt
  state; a concurrent mutation fails the revision check instead of interleaving.
- In-flight protection is two-sided: writes are fenced (above), and the running flow
  polls `leases.hasFailed()` via its cancel check (§3 step 6).

## 6. Journal

`ExecutionJournalSession` has two operations with different guarantees:

- `record(type, stepId, hash, data)` — appends to the in-memory event list (visible
  on the handle immediately) and buffers for the next durable append.
- `persist(state, dataMutation)` — flushes buffered events + the snapshot mutation in
  **one fenced append**.

**Dual-write suppression** — the rule that keeps the journal single-sourced: in
durable mode, transaction lifecycle events (`TRANSACTION_SUBMITTED`, `_IN_BLOCK`,
`CONFIRMATION_DEPTH_CHANGED`, `_CONFIRMED`, `_ROLLED_BACK`) are journalled by
`DurableExecutionPersistence` at the same fenced append that records the attempt
transition; the engine's listener (`eventListener`) emits them **only when
`store == null`**. If you add a transaction-lifecycle event, pick exactly one writer.

### Attempt lifecycle (`AttemptState`, written by `DurableExecutionPersistence`)

```
BUILDING → BUILT → SIGNED → SUBMITTING → SUBMITTED → IN_BLOCK → CONFIRMED
                                │                        │
                                │                        └→ ROLLED_BACK
                                │                            (dependents → RECOVERY_REQUIRED)
                                └ the crash-safety linchpin: persisted BEFORE
                                  the backend call, so a crash between persist
                                  and response is provably "maybe submitted"
```

Attempts are stored under snapshot data key `"attempts"` as
`stepId#attemptNumber → FlowAttemptSnapshot` (state, signed payload, validity slots,
spent inputs, inclusion records, error code). Two facts matter downstream:

- **Nothing ever writes `AttemptState.CANCELLED` or `FAILED` over a submitted
  attempt** — a submitted-then-cancelled attempt stays `SUBMITTED`/`IN_BLOCK`, which
  is exactly what lets TxStream's cancelled-template projection detect
  submitted-but-undecided evidence.
- `SUPERSEDED` marks an attempt replaced by a newer one; the newer attempt carries
  the live state.

## 7. The uncertain-disposition contract (canonical)

> **A submitted transaction whose outcome is unknown must NEVER settle as FAILED.**
> The litmus test for any new failure path: *can I prove this transaction is not,
> and will never be, on chain?* If not, it is uncertain.

Uncertainty is carried in **two independent channels**, and both are checked
(`FlowExecutor.isUncertainDisposition`):

1. `ConfirmationOutcome.Type` ∈ {`TIMEOUT`, `CANCELLED`, `RECOVERY_REQUIRED`}.
2. The error cause chain: `ConfirmationTimeoutException` or
   `ReconciliationUncertainException` anywhere in it — some rollback policies keep a
   `ROLLED_BACK` outcome (so rollback hooks still run) while carrying the
   uncertainty in the chain (e.g., an exhausted same-hash reinclusion window).

What uncertain settlement looks like at each layer:

| Layer | Uncertain disposition |
|---|---|
| Step (`FlowStepResult`) | `submissionPendingAt(...)`: `IN_PROGRESS` + hash + cause — never `FAILED` |
| Legacy flow result | `FAILED` with **empty `getFailedStep()`**; inspect step results and error |
| Engine state | Elevated to **`RECOVERY_REQUIRED`** in `run()`: `FlowStoreException`, `ReconciliationUncertainException`, `ConfirmationTimeoutException` in the chain, or a rollback under `PAUSE_FOR_RECOVERY` |
| Listener | `onStepUncertain` / `onFlowUncertain` (routed via the sole choke points `notifyStepTerminal` / `notifyFlowTerminal` in `FlowExecutor`) |
| Journal | `RECOVERY_REQUIRED` events (step-scoped with stepId+hash, and flow-scoped) — **never `STEP_FAILED`/`EXECUTION_FAILED`** for an uncertain outcome; the journal listener and `run()`'s persisted state must always agree |
| `FlowError.retryable` | **Per-request truth**: `true` only when a claim exists to attach to — a durable store or an explicit idempotency key. A keyless non-durable re-`start()` is fresh work and can pay twice → `false` |
| Legacy `resumeSync`/`resume` | **Refuse** (`IllegalStateException`) when the previous result contains an `IN_PROGRESS`+hash step |
| `StepRunner` | An `UncertainSubmissionException` never enters the fresh-rebuild retry loop: the policy may choose `RECONCILE_THEN_RETRY` (reconcile the hash first) or the step settles pending via `uncertainFailure` |

**Cancellation during confirmation is a partial case**: the step still settles
`IN_PROGRESS`+hash, but flow/engine state is `CANCELLED`, the journal records
`EXECUTION_CANCELLED`, and the uncertain listener callbacks do not fire. Stream-side
handling of this case (including template items) is covered in
[TXSTREAM_INTERNALS.md](TXSTREAM_INTERNALS.md) §6.

## 8. Recovery — `engine.recover(request)`

Reconciles **one uncertain attempt** by hash. It never rebuilds a transaction; the
only write it can make to the chain is the **byte-identical signed payload**.

`FlowRecoveryCoordinator.recover`:

1. No persisted signed payload → `RECOVERY_REQUIRED`
   (`TXFLOW_SIGNED_PAYLOAD_MISSING`).
2. **Observe the hash**: found with a block → `IN_BLOCK` (+ inclusion record); found
   without → `SUBMITTED`. Decided — done.
3. Not found: if `currentSlot + resubmitSafetyMargin >= validToSlot` →
   `RECOVERY_REQUIRED` (`TXFLOW_VALIDITY_WINDOW_EXPIRED`) — too close to the window
   edge to resubmit safely (the original could still land at the boundary).
4. Otherwise verify the payload (`SignedPayloadVerifier`, hash-checked against the
   persisted digest) and **resubmit the identical bytes** → `SUBMITTED`
   (`identicalPayloadResubmitted=true`), or `RECOVERY_REQUIRED`
   (`TXFLOW_IDENTICAL_RESUBMISSION_FAILED`).
5. Any observation error → `RECOVERY_REQUIRED` (`TXFLOW_RECOVERY_OBSERVATION_FAILED`)
   — inconclusive stays inconclusive.

In durable mode (`recoverDurably`) this runs **under the execution's leases**, with
`RECOVERY_STARTED` / `RECOVERY_COMPLETED` (or `RECOVERY_REQUIRED`) journalled and the
attempt snapshot updated (state, deduplicated inclusions) in fenced appends. Calling
`recover` is an explicit operator-authorized override — it may reconcile an attempt
retained by a terminally cancelled execution, which is never resumed automatically.

## 9. Invariants — do not break these

1. **Fail before claiming.** Validation/compilation failures must not create claims
   or snapshots.
2. **One journal writer per event type** (dual-write suppression, §6).
3. **`SUBMITTING` is persisted before the backend call.** Moving it after the call
   destroys crash recovery's ability to say "maybe submitted".
4. **Never write a decided `AttemptState` over a submitted attempt without chain
   evidence** — cancellation and flow failure leave it `SUBMITTED`/`IN_BLOCK`.
5. **Uncertain ⇒ `RECOVERY_REQUIRED`, never `FAILED`** — at every layer, and journal
   events must agree with the persisted state (§7).
6. **`retryable` is per-request truth**, not a convention (§7).
7. **Recovery resubmits identical bytes only**; a rebuild is a new execution
   decision, never part of reconciliation.
8. **Resource leases acquire in sorted order**; every durable write carries the full
   `MutationFence`.
9. **Busy claims are voided, terminal failures are not** (§4) — a same-key retry
   must recover from busy, but must MATCH a genuine terminal outcome.
10. **MATCHED handles have empty steps/attempts** — consumers read the store.

## 10. Test map

| Concern | Tests (in `txflow/src/test/java/.../exec/`) |
|---|---|
| Uncertain elevation, journal agreement, retryable | `FlowEngineTest`: `confirmationTimeoutSettlesRecoveryRequiredNotFailed`, `confirmationTimeoutJournalsRecoveryRequiredEventsNotFailureEvents`, `batchUnknownSubmissionObservationFailureIsDurablyRecoveryRequired`, `reconciliationUncertaintyNestedUnderRollbackMapsToRecoveryRequired` |
| Claims, conflicts, MATCHED, busy-void, leases | `FlowEngineTest` (idempotency / lease / resource-busy groups) |
| Executor-side uncertain settlement + resume guard | `FlowExecutorTest`: timeout trio, uncertain-submission tests, `portableWaitForReinclusionExhaustsAtWindowNotRecoveryCycleCount`, `testResumeSync_submissionPendingStep_refusesResume` |
| Recovery coordinator | `recovery/` tests (observe / expired-window / identical-resubmit paths) |
| Store contract + fencing | `txflow-extensions/txflow-store-rdbms` tests |
| End-to-end under chaos | `txflow-extensions/txflow-soak` (crash / rollback / failover; chain-as-oracle reconciliation) |

## 11. Reading order for a new maintainer

1. This document, then [Flowexecutor-code-flow.md](Flowexecutor-code-flow.md) for the
   executor underneath.
2. `FlowExecutionState`, `FlowEventType`, `AttemptState`, `ConfirmationOutcome` —
   the vocabulary.
3. `FlowEngine.start()` → `createHandle()` → `run()` — the spine of §3–§4, in code.
4. `DurableExecutionPersistence` + `ExecutionJournalSession` + `DurableLeaseGuard` —
   the durability triangle (§5–§6).
5. `FlowExecutor.isUncertainDisposition` and the `notify*Terminal` choke points (§7).
6. `FlowRecoveryCoordinator` (§8), then [TXSTREAM_INTERNALS.md](TXSTREAM_INTERNALS.md)
   for the stream layer on top.
