# ADR 0004: TxFlowStream v2 — Streaming Transaction Workflows on the FlowEngine Durable Runtime

- **Status**: Accepted (2026-07-18, after five external review rounds)
- **Date**: 2026-07-18
- **Document version**: 0.6
- **Modules**: `txflow` (package `com.bloxbean.cardano.client.txflow.stream`)
- **Related**: ADR 0002 (portable contract & FlowEngine), ADR 0003 (relational durable store), `review-txflow-refinement-iter1-claude.md` **Appendix A** (TxFlowStream MVP review — the checked-in findings this ADR cites)
- **Supersedes**: the TxFlowStream MVP design shipped in commit `5421a846` (unreleased; no backward-compatibility obligation)

**Revision history**
- 0.1 (2026-07-18): initial proposal.
- 0.2 (2026-07-18): revised after external design review. Resolves: per-item idempotency in multi-item plans (Decision 3), projection authority & restart hydration (Decision 5), terminal-flow/IN_PROGRESS-step precedence (Decision 4), lane scheduling vs claim-consuming resource-busy (Decision 2), missing async engine API and incorrect builder signature (new Engine Prerequisites section + fixed example), portable-item boundary (Decision 6), close-vs-cancel lifecycle (Decision 7), auditable review reference (header).
- 0.3 (2026-07-18): revised after second external review round. Resolves: fail-closed authoritative writes vs best-effort projections (Decisions 5, 7), deterministic execution ids + two-phase binding so MATCHED cannot diverge from the write-ahead record (Decisions 3, 5), one-lane-per-execution rule for iteration 1 (Decisions 2, 6), live-redelivery semantics vs duplicate rejection (Decision 3), shared-flow cancellation semantics (Decision 7), explicit status-transition table replacing the blanket terminal guard (Decision 4), compaction-safe P2 baseline, `StableIdFactory` replacing unenforceable runtime determinism validation (Decisions 3, 6), `maintenanceExecutor` in the durable example.
- 0.4 (2026-07-18): revised after third external review round. Resolves: sensitive-data policy for persisted planned requests (Decision 5), canonical lane identities + lane-scoped coin selection (Decision 2), authoritative snapshot fast-forward exemption to the transition table (Decision 4), durable-stream-requires-durable-engine builder invariant + capability probe (Decision 5, new P5), iteration-1 recovery-repair observation via read-through reconciliation (Decision 4), honest `abort()`/`close(Duration)` semantics with retained completion machinery (Decision 7), per-item completion promises decoupling drain/receipts from handles (Decisions 4, 7), versioned full-field redelivery fingerprint (Decision 3), header version bump.
- 0.5 (2026-07-18): final pre-approval polish after fourth external review round (no architectural blockers found). `LaneIdentityResolver` returns a `ResolvedLane` carrying the funding scope so lane-pinned coin selection is mechanically enforceable (Decision 2); P3 re-scoped from iteration-blocking to iteration 2 (per-lane scheduling already prevents the in-process busy path); delivery plan split into independently verifiable slices 1A/1B/1C on external recommendation.
- 0.6 (2026-07-18): fifth review round. **Scheduling is keyed by canonical spending identity, not lane name** — alias lane names sharing one identity share one FIFO, closing a re-opened claim-poisoning path (Decision 2, blocker); `LanePolicy.single(ResolvedLane)` gives iteration 1A a statically configured funding scope so mechanical enforcement doesn't wait for 1B's dynamic resolver (Decision 2, delivery plan); resolution-timing wording fixed (static lanes validate at `build()`, dynamic lanes resolve at first use and fail the *item* typed); example aligned with the 1A surface.

## Summary

Rebuild the unreleased TxFlowStream API on top of `FlowEngine` instead of the legacy `FlowExecutor`. Items become idempotent `FlowExecutionRequest`s; stream concurrency becomes UTXO-native **lanes** scheduled by the stream (one active execution per lane) with the engine's spending-resource coordination as the cross-process safety net; item/batch status becomes a **projection of engine truth** with explicit terminal-precedence rules; execution outcomes are durable in the engine's `FlowExecutionStore` while the stream owns an authoritative, durable record of its *planning metadata* (item↔execution/step/lane mapping). The public front door stays small and idiomatic: `submit(item) → receipt` with a `CompletionStage`, `try`-with-resources lifecycle, and progressive disclosure for advanced use.

## Context

### What the MVP is

The MVP (commit `5421a846`) accepts `TxWorkItem`s (a `FlowStep` or a QuickTx `TxPlan`), groups them into count/time windows, asks a `TxStreamPlanner` to generate one bounded `TxFlow` per window, and executes flows **serially on a single worker thread** through a blocking `FlowExecutor.executeSync(...)`. Status is tracked in a stream-owned `TxStreamStateStore` (in-memory by default) and surfaced through receipts.

### Why it must change

1. **The runtime underneath it changed.** The refinement iteration (ADR 0002 v2.6.6) shipped `FlowEngine`: idempotency claims, epoch-fenced leases, durable write-ahead journaling of signed transactions, spending-resource coordination, typed recovery. The MVP predates all of it and re-implements weaker versions of several of these concerns.
2. **The MVP's advanced concerns are stubs.** `UtxoReservationPolicy` is a one-value enum that is stored and never read — "serial by funding scope" is really "one worker thread". `TxWorkItem.idempotencyKey` is carried but never consumed: redelivery from any real source produces duplicate on-chain transactions.
3. **Implementation review found systemic non-happy-path defects** (Appendix A of the review document): terminal-state overwrites (CONFIRMED items rewritten to FAILED by a later batch error); fragile worker/callback handling with zero logging (a callback `RuntimeException` in the main path strands receipts and corrupts projections; the same callbacks throwing inside the failure handlers — or any `Error` — kill the worker and wedge the stream); a `trySubmit` that blocks behind `submit`; `drain()` returning before the last item is processed; no failure isolation inside a window; duplicate item/step ids unguarded; and — post-merge — misclassification of the refined result model (`IN_PROGRESS` submitted-unconfirmed results reported as terminal FAILED **with the transaction hash dropped**, `CANCELLED` flows reported FAILED, items marked SUBMITTED before any transaction exists).
4. **No compatibility constraint.** TxFlowStream is unreleased — not even pre-release. We are free to redesign the API surface.

### Design principles

1. **Simple things are one line; powerful things are possible.** A wallet, a backend, `submit(...)` — that must be the whole beginner story. Lanes, planners, durable stores, and recovery are opt-in layers, never prerequisites.
2. **Use the UTXO model as the concurrency model.** Cardano's eUTXO design gives three native levers: *disjoint UTXOs can be spent in parallel* (lanes), *outputs of an unconfirmed transaction can be chained* (pipelining within a lane), and *many intents can share one transaction* (batching). The stream exposes exactly these levers instead of a global lock or thread-count tuning.
3. **Clear authority, no duplicated truth.** The engine store is authoritative for *execution outcomes*; the stream store is authoritative for *planning metadata* the engine never sees (item↔execution/step/lane/batch mapping). Each fact has exactly one owner; projections join them, never re-decide them.
4. **Honest states.** Never report SUBMITTED before a transaction exists or FAILED while a submitted transaction may still confirm. A transaction hash, once known, is never dropped from any status record. Every receipt reaches a terminal state in bounded time — "can hang forever" is a design bug, not an edge case.
5. **Crash-safe by construction.** Item submission is idempotent end to end; a restarted stream re-attaches to in-flight executions instead of re-running them.
6. **Deterministic and testable.** No wall-clock or real sleeps in stream logic; the scheduler/clock seams from ADR 0002 Decision 21 apply to the stream worker exactly as they do to the engine.

## Engine prerequisites (blocking for iteration 1A unless marked otherwise)

The current `FlowEngine` API cannot host this design as-is. These small, general-purpose engine extensions are explicit prerequisites — each is independently useful beyond the stream:

- **P1 — Non-blocking completion.** `FlowExecutionHandle` keeps its `CompletableFuture` private and exposes only blocking `await()`/polling (`FlowExecutionHandle.java:21-23`). Add `CompletionStage<FlowExecutionResult> completion()` (a defensive, non-cancelling view — e.g. `future.minimalCompletionStage()`). Without it, the stream would burn one waiter thread per in-flight execution.
- **P2 — Projection read access (compaction-safe).** `handleForStoredSnapshot(...)` (`FlowEngine.java:480-499`) returns a completed handle with an **empty event list and no step results**, so a `MATCHED` idempotent re-submit after a restart cannot be projected into a meaningful receipt. Add read-only accessors — `FlowEngine.executionSnapshot(executionId)` and `FlowEngine.executionEvents(executionId, afterSequence)` — that delegate to the configured store. The contract must be **compaction-safe**: the store may compact terminal-prefix events (`EventReadResult.compactedThroughSequence`), so P2's projection baseline is the *snapshot*, which must carry a typed per-step/per-attempt terminal projection sufficient to project item outcomes without the compacted events; `executionEvents` fills in post-baseline detail only, and a cursor older than the compaction watermark triggers re-baselining from the snapshot rather than an error. If the current `FlowExecutionSnapshot` shape is not sufficient for that baseline, extending it is part of P2. (Alternative considered and rejected: hydrating events into the stored-snapshot handle itself — it changes handle semantics for all callers.)
- **P3 — Resource-busy must not consume the idempotency claim** *(iteration 2 — re-scoped in 0.5)*. The engine takes the (durable) claim before acquiring spending resources (`FlowEngine.java:193-227`; refinement re-review observation 6), so a `TXFLOW_RESOURCE_BUSY` outcome is persisted as terminal FAILED *under the caller's idempotency key* — a later retry with the same key gets `MATCHED` → the stored failure, and can never run. Engine change: on lane/resource contention, either release/void the claim or record a non-terminal `REJECTED_BUSY` outcome that `createOrGet` treats as claim-absent. The stream's per-lane scheduling (Decision 2) makes this path unreachable in-process, so P3 gates only cross-process lane sharing / multi-instance operation — it lands with iteration 2, before any topology where two processes contend for a lane.
- **P4 — Namespace enumeration** *(iteration 2)*. `FlowExecutionStore.listExecutions(idempotencyNamespace, states, pagination)` for restart re-attach; rides the ADR 0003 conformance kit and both store implementations.
- **P5 — Durability capability probe** *(iteration 2)*. The engine exposes no way to ask whether executions are durably stored, but the stream's crash-recovery algorithm (Decision 5) is only sound against a durable engine store. Add a read-only capability accessor — e.g. `engine.capabilities().durableExecution()` — so the durable-stream builder invariant can be enforced at construction time instead of discovered as a double-spend in production.

## Decision 1 — TxFlowStream executes through FlowEngine

The stream no longer constructs or wraps a `FlowExecutor`. The builder takes a caller-supplied `FlowEngine`:

```java
FlowEngine engine = FlowEngine.builder(utxoSupplier, protocolParamsSupplier,
                transactionProcessor, chainDataSupplier)   // actual 4-arg signature
        .executor(executionExecutor)                       // caller-owned, required for real use
        .store(executionStore)                             // optional: durable execution when present
        .maintenanceExecutor(leaseExecutor)                // required whenever a store is configured
        .signerRegistry(signers)
        .build();

try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
        .lanes(LanePolicy.single(                           // 1A: one statically configured lane
                ResolvedLane.ofAddress("payouts", senderAddress)))
        .window(WindowPolicy.countOrTime(50, Duration.ofSeconds(10)))
        .build()) {
    stream.start();

    TxStreamReceipt receipt = stream.submit(
            TxWorkItem.builder("pay-0042")
                    .withTxPlan(plan)                       // portable payload (Decision 6)
                    .withIdempotencyKey("order-0042")       // optional; defaults to itemId
                    .build());
    // multi-lane: .lanes(LanePolicy.explicit(), laneResolver) + .withLane("treasury") on items (1B)

    TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
    // ... use outcome.getTransactionHash(), outcome.getStatus() ...
}   // close() = graceful: drains accepted work, then releases resources (Decision 7)
```

- Each planned flow is submitted as a `FlowExecutionRequest` via `FlowEngine.start(...)`; the stream composes handle `completion()` stages (P1). Execution is **non-blocking and concurrent**, bounded by the lane scheduler (Decision 2) and a global `maxInFlight` cap.
- The MVP's `FlowExecutionRunner`/`FlowExecutorRunner` seam, the legacy config pass-throughs (`withChainingMode`, `withRollbackStrategy`, `withConfirmationConfig`, `withRetryPolicy`, `withFlowListener`, `withFlowStateStore`), and the `BackendService` constructor argument are **removed**. Execution behavior is configured where it belongs: on the definition (`FlowExecutionSettings`), the request, or the engine's `FlowExecutionPolicy`. The stream builder keeps only stream concerns: source, window, planner, lanes, buffer size, event listener, projection store.
- Consequence: the stream inherits, with one implementation, everything the engine already guarantees — write-ahead journaling before submission, fenced leases, typed recovery, cooperative cancellation, policy enforcement.

## Decision 2 — Lanes: UTXO-native parallelism, scheduled by the stream

A **lane** is a funding scope — a set of UTXOs (in practice: a funding address or a designated funding UTXO chain) that at most one in-flight execution may spend at a time.

**Scheduling lives in the stream, not in engine contention.** The dispatcher maintains a FIFO queue per lane — keyed by the lane's *canonical spending identity*, never its name (see below) — and dispatches a lane's next execution only when its previous one completes. This is required, not stylistic: the engine consumes the idempotency claim before resource acquisition (P3), so letting executions pile into `TXFLOW_RESOURCE_BUSY` would poison their claims; and a naive `maxInFlight == laneCount` cap could be fully occupied by one lane's work, starving the others. Per-lane dispatch makes both failure modes structurally impossible in-process.

**One lane per execution (iteration 1).** A `PlannedExecution` targets exactly one lane. When a window contains items from multiple lanes, the built-in planners **partition the window by lane** — one planned flow per lane group — so per-lane FIFO dispatch is complete and trivially deadlock-free; a planned flow whose items span lanes is a planner validation error. Multi-lane executions (an atomic all-or-nothing reservation over deterministically sorted canonical spending identities, mirroring the engine's own sorted spending-resource acquisition) are a future extension, taken up only when a planner genuinely needs cross-lane atomicity.

**The engine's spending resources remain declared on every request** (canonical spending identity → `FlowExecutionRequest.spendingResource(...)`) as the *cross-process safety net*: if another process ever runs against the same funding scope, the engine's coordinator and durable resource leases — not stream bookkeeping — prevent concurrent spending. Until P3 lands, sharing a lane across processes is out of scope (single-writer stream per lane set), and the ADR records that as an accepted iteration-1 limitation.

```java
public interface LanePolicy {
    static LanePolicy single(ResolvedLane lane);            // whole stream = 1 statically configured lane (1A)
    static LanePolicy explicit();                           // item.getLane() names the lane, resolved dynamically (1B)
    static LanePolicy byFundingAddress();                   // lane derived from the item's sender/from address (2)
    static LanePolicy partitioned(PartitionedLanes config); // fan-out bootstrap, hash(idempotencyKey) % N (2; OQ3)
}
```

**Lane labels are names; identity — and therefore scheduling — is canonical (new in 0.4; sharpened in 0.5/0.6).** ADR 0002 (Decision 18) requires contention to be computed over *canonical resolved spending identities*, never raw alias strings — `FlowExecutionRequest.spendingResource(...)` does no resolution, so a lane label like `"treasury"` passed as-is would let two aliases of the same wallet slip past both the stream scheduler and the engine coordinator. The stream therefore separates naming from identity: `LanePolicy` produces a lane *name* (a user-facing label for receipts, stats, and diagnostics), and a **`LaneIdentityResolver`** resolves it to everything the stream needs to *enforce* the lane, not just label it:

```java
record ResolvedLane(
    String laneName,                     // user-facing label only — never a scheduling key
    String canonicalSpendingIdentity,    // scheduling key + FlowExecutionRequest.spendingResource(...)
    LaneFundingScope fundingScope) {}    // address or designated UTXO chain backing this lane
```

**The dispatcher serializes on `canonicalSpendingIdentity`, never on `laneName` (0.6 — closes a re-opened claim-poisoning path).** If scheduling keyed on names, two labels resolving to the same wallet would get separate FIFO queues and dispatch concurrently against one spending resource — recreating exactly the in-process `TXFLOW_RESOURCE_BUSY` path that the per-lane scheduler exists to make unreachable (and on whose unreachability P3's deferral rests). Rules: alias lane names that resolve to the same canonical identity **share one FIFO** (they are one lane wearing two labels); two lanes whose funding scopes *overlap* while claiming different canonical identities **fail validation** — overlapping scopes cannot be independent lanes.

**Resolution timing (0.6 wording fix):** the statically configured lane of `single(ResolvedLane)` is validated at `build()`; dynamically named lanes (`explicit()`) resolve at first use, and a name that fails to resolve fails **that item** with a typed planning error — it cannot be a startup error, since the names are not known at startup. For `byFundingAddress()`/`partitioned(n)` the resolver is derived automatically from the lane's funding address.

**A lane must also constrain coin selection, not just labeling.** Declaring a spending resource does not stop QuickTx from selecting any wallet UTXO it likes — two "different lanes" drawing on one address would still contend at selection time. Because `ResolvedLane` carries the funding scope, the stream **mechanically materializes and validates** planned transactions against it — inputs pinned to the lane's address or designated UTXO chain (`from(laneAddress)` / lane-scoped UTXO selection) — rather than leaving `explicit()` correctness as a documented caller obligation. `byFundingAddress()` gets this for free; an `explicit()` item whose transaction draws outside its lane's scope fails typed at planning time.

- **Throughput model**: items on different lanes execute concurrently; items on the same lane serialize. Within one generated flow, PIPELINED chaining (an existing engine capability) chains steps off pending outputs, so a lane is not limited to one transaction per block.
- `LanePolicy.partitioned(PartitionedLanes)` (iteration 2) is the full UTXO story: a one-time **fan-out bootstrap flow** splits the funding wallet into `N` disjoint lane UTXOs, then `N` lanes run concurrently — throughput scales with lanes, not block time. The bootstrap is a normal engine execution (durable, idempotent under `bootstrap:<N>:<fingerprint(funding, addresses, seed)>`). *(Sketch reconciled with the OQ3 resolution — the accepted normative decision is unchanged.)*
- The MVP's `UtxoReservationPolicy` enum is **deleted**.
- The known engine limitation carries over honestly: fencing cannot stop a partitioned stale worker that already holds signed bytes (ADR 0002 Decision 18, accepted residual risk). Lanes narrow the blast radius to one lane.

## Decision 3 — Item idempotency is engine idempotency (scoped per planner)

`FlowExecutionRequest` supports exactly one `(namespace, key)` claim per execution, so idempotency guarantees are **per planned flow**, and the design must be explicit about what that means per planner:

- **`perItem()` (the default) gives true per-item dedup.** One item = one flow = one claim: namespace `stream:<streamId>`, key = item `idempotencyKey` (defaulting to `itemId`). A redelivered item `MATCH`es its existing execution and the receipt projects that execution's current state (via P2 when the original run predates this process).
- **Deterministic execution identity (new in 0.3 — required so `MATCHED` and the write-ahead binding agree).** `FlowExecutionRequest.executionId` defaults to a random UUID, so a redelivered request would carry a *different* execution id than the one the stream's write-ahead binding recorded — `MATCHED` would return the stored execution and the binding would point at an execution that never ran. Therefore the stream **always sets the execution id explicitly**, derived deterministically from the claim: `executionId = stableId(namespace, claimKey)` (hash-based, `FlowStoreTextPolicy`-bounded). Same claim → same execution id, on every process, on every redelivery — the binding written before `start()` is correct whether the outcome is `CREATED` or `MATCHED`. See Decision 5 for the two-phase binding handshake this enables.
- **Deterministic planned requests via `StableIdFactory` (replaces 0.2's unenforceable "validation").** Fingerprints cover the whole compiled definition — identifiers, descriptions, settings, transaction content, bindings — so *no* runtime inspection can prove a custom planner is deterministic. Instead: (a) the **stream generates canonical flow/step ids itself** for the built-in planners (pure functions of the item idempotency keys — never batch sequence, window position, or timestamps, which is exactly what the MVP got wrong); (b) `TxStreamPlanningContext` exposes a **`StableIdFactory`** (`flowId(sortedMemberKeys)`, `stepId(memberKey)`) that custom planners are required to use; (c) full-request determinism (same items ⇒ byte-identical planned request) is a **documented SPI obligation** on custom planners, with the consequence spelled out: a non-deterministic planner converts legitimate redeliveries into `TXFLOW_IDEMPOTENCY_CONFLICT`. The stream still validates what *is* mechanically checkable: an item mapped more than once inside one plan (to two steps, or in two flows) and cross-lane flows are planner validation errors. **Step-sharing exception (batching):** *multiple items mapping to one step* is **not** an error — it is legitimate transaction-granular batching (Decision 6): several items ride one merged transaction and are each projected from that step's single outcome. The stream validates the *mapping* (no item mapped twice, no unmapped/foreign items, no orphan steps); it cannot and does not validate that a shared step's transaction actually pays each mapped item — that is the planner's owned obligation (the built-in `batching(...)` planner is correct by construction; a custom planner sharing steps must uphold it).
- **`perWindow()` and other multi-item plans get flow-level dedup only.** The flow claim key is derived from the sorted member idempotency keys. That deduplicates an exact same-window resubmission, but a *single* redelivered item that lands in a differently-composed window is a new claim — it will run again. Iteration 1 therefore documents: **durable per-item exactly-once requires `perItem()`**; `perWindow()` trades per-item dedup for grouping. Sources that redeliver must either use `perItem()` or dedup upstream.
- **Live redelivery semantics (new in 0.3).** Brokers legitimately redeliver while the first receipt is still active, so `submit`/`trySubmit` resolves duplicates by content, not by blanket rejection:
  - same `itemId`/`idempotencyKey` with **identical item fingerprint** → **attach**: return the existing item's receipt; no new work is created. The fingerprint covers **every planner-visible field** — portable-encoded payload, lane, idempotency key, and metadata — in a versioned canonical format (`txstream-item:v1` + canonically ordered fields, mirroring `ExecutionRequestFingerprinter`); payload-plus-lane alone would let two items differing only in metadata a custom planner reads attach to one receipt;
  - same `itemId`/`idempotencyKey` with a **different payload** → typed conflict (`EmitResult.conflict(...)` / `TxStreamDuplicateItemException`), never a silent replacement — the stream-level mirror of `TXFLOW_IDEMPOTENCY_CONFLICT`;
  - duplicates *inside one planner output* — *two mappings claiming the same item* (one item on two steps/flows) → planner validation error, window fails typed. The reverse — *several items mapping to one step* — is **not** a duplicate: it is transaction-granular batching (Decision 6) and is allowed.
- **Future engine extension (open question 2)**: multi-claim support — one execution atomically registering N item-level claim aliases — would give batching planners per-item dedup. Not iteration-1 scope; the store semantics (atomicity of N claims + 1 execution) must go through ADR 0003's conformance kit.

## Decision 4 — Status is a projection of engine truth, with explicit terminal precedence

Item status derives from engine states/events (event stream via handle, plus P2 reads for re-attach):

| Stream item status | Source of truth |
|---|---|
| `ACCEPTED` | stream buffer (only stream-owned state) |
| `PLANNED` | planner output recorded; engine `CREATED` |
| `SUBMITTED` | engine `TRANSACTION_SUBMITTED` event for the item's step — never asserted in advance |
| `CONFIRMED` | step result `COMPLETED` (confirmed on chain) |
| `FAILED` | terminal failure; **always carries the transaction hash when one exists** |
| `CANCELLED` | engine/flow `CANCELLED` |
| `RECOVERY_REQUIRED` | engine `RECOVERY_REQUIRED`, **or** the precedence rule below |

**Terminal-precedence rule (new in 0.2).** The refined executor deliberately reports submitted-but-unconfirmed steps as `IN_PROGRESS` inside a flow result that may itself be terminal (`FAILED`, `PARTIALLY_COMPLETED`, `RECOVERY_REQUIRED`) — and a terminal handle emits no further events. A projection that waits for a step-level terminal event would hang forever. Therefore:

> When a flow handle completes, **every item of that flow reaches a terminal stream status in the same projection pass.** An item whose step result is `IN_PROGRESS` (submitted, unconfirmed) inside a terminally-completed flow becomes **`RECOVERY_REQUIRED`**, retaining its transaction hash — the honest answer: the transaction may confirm, nobody is watching anymore, and `engine.recover(...)` (or a future stream-owned reconciliation observer, iteration 3) is the follow-up. It is never reported `FAILED` (may confirm) and never left non-terminal (would hang `drain()`/receipts).

- **Every accepted item owns an internal completion promise (sharpened in 0.4).** A flow handle completing is only *one* of the ways an item terminates — items also terminate at submit-time portability validation, planner failure, fail-closed binding failure, and buffered cancellation, none of which ever produce a `FlowExecutionHandle`. The receipt's `completion()` is backed by the item promise; handle completion, validation failure, planning failure, and cancellation are its completers. `drain()` snapshots and awaits the item promises — not handles, not receipt polling, not queue-emptiness heuristics — so no accepted item can be left out of quiescence accounting.
- `TxStreamBatchStatus` gains `PARTIALLY_COMPLETED`; a 9-of-10 batch is no longer recorded FAILED.
- **Explicit transition table (replaces 0.2's blanket terminal→terminal guard, which would have frozen recovery repairs).** Projections advance only along these edges, ordered by engine event sequence / store revision (a stale-sequence write is dropped, which is what actually kills the MVP's CONFIRMED→FAILED overwrite class):

  | From | Allowed to |
  |---|---|
  | `ACCEPTED` | `PLANNED`, `FAILED`, `CANCELLED` |
  | `PLANNED` | `SUBMITTED`, `FAILED`, `CANCELLED`, `RECOVERY_REQUIRED` |
  | `SUBMITTED` | `CONFIRMED`, `FAILED`, `CANCELLED`, `RECOVERY_REQUIRED` |
  | `RECOVERY_REQUIRED` | `CONFIRMED`, `FAILED`, `CANCELLED` — recovery is *resolvable*: operator `engine.recover(...)` outcomes repair the projection |
  | `CONFIRMED`, `FAILED`, `CANCELLED` | ∅ — final |

  **Authoritative fast-forward exemption (new in 0.4).** The table governs *live event-driven* progression. A re-baseline from an authoritative engine snapshot (restart re-attach, compaction re-baseline, read-through reconciliation) may fast-forward **any non-final projection directly to the snapshot-derived state** without visiting intermediate statuses — after a crash, an item still projected `PLANNED` whose engine snapshot says the step completed goes straight to `CONFIRMED`. Stale-overwrite protection comes from event-sequence/store-revision ordering, not from forcing every hop; final states (`CONFIRMED`/`FAILED`/`CANCELLED`) remain immutable.

  **Repair observation in iteration 1 (new in 0.4).** A `RECOVERY_REQUIRED` item's handle is terminal — nothing pushes further events — and the stream-owned reconciliation observer is iteration-3 scope. Until then, repair is **read-through**: `getItemStatus(itemId)` on a `RECOVERY_REQUIRED` item consults the engine snapshot (P2) and repairs the projection lazily (emitting the repair to the event listener when it advances), and `stream.reconcile(itemId)` forces the same check explicitly after an operator runs `engine.recover(...)`. Push-based repair without a caller poll arrives with the iteration-3 observer — the ADR promises no more than that for iteration 1.

  A receipt whose `completion()` already fired with `RECOVERY_REQUIRED` keeps that as its point-in-time outcome (futures complete once); the *live* projection (`getItemStatus`, event listener) reflects the post-recovery repair.
- `TxStreamReceipt` exposes `completion()` (`CompletionStage<TxStreamItemResult>`), `executionId()`, and an event cursor. Simple callers just await the future.

## Decision 5 — Durability: split authority, both halves durable

0.1 claimed the stream store was "non-authoritative and rebuildable from the engine store". That was wrong on two counts: the engine snapshot stores bindings and spending resources but **not** the planner's item↔step/batch/lane mapping (stream-only knowledge — nothing to rebuild from), and stored-snapshot handles carry no events or step results to rebuild with (P2). Revised model:

- **Engine store: authoritative for execution truth.** Outcomes, attempts, events, claims — exactly as ADR 0002/0003 define. The stream never re-records or re-decides any of it.
- **Stream store: authoritative for planning metadata, and its writes fail closed (sharpened in 0.3).** `TxStreamStateStore` v2 is a small, real store — not a cache — owning the item registry (`itemId`, `idempotencyKey`, lane, `batchId`) and the item→(`executionId`, `stepId`) binding. Because it is authoritative, **a failed planning-metadata write aborts the operation**: a failed item-registry write rejects the `submit`; a failed binding write fails that item (typed, retryable) *before* `FlowEngine.start(...)` is invoked — a transaction must never execute without a durable record of which item it belongs to. This is the deliberate opposite of the best-effort rule for projections and listeners (Decision 7 draws the line). The contract keeps compare-and-swap/transition semantics so last-write-wins corruption is impossible.
- **Two-phase binding handshake (new in 0.3).** Deterministic execution ids (Decision 3) make the write-ahead binding stable, and dispatch confirms it:
  1. *Bind*: write `item → (executionId = stableId(claim), stepId, lane)` with binding state `DISPATCHING` — fail closed.
  2. *Start*: call `FlowEngine.start(request)` with that explicit execution id.
  3. *Confirm*: record the start outcome on the binding (`CREATED` / `MATCHED` / typed rejection). Because the id is claim-derived, a `MATCHED` outcome refers to exactly the execution the binding already names — the 0.2 divergence (random request UUID vs stored execution) is structurally impossible.
  A crash between (1) and (3) leaves a `DISPATCHING` binding; on restart the stream resolves it by asking the engine store for `executionId` (P2): present → the start happened, confirm and re-project; absent → the start never happened. In iteration 1 (in-memory store) this window simply loses the item like any buffered work. In iteration 2 the durable stream store persists the **portable-encoded planned flow and request options alongside the binding**, so an absent execution is re-dispatched from the stored plan — restart recovery does not depend on source redelivery.
- **Durable stream mode requires a durable engine store (new in 0.4 — builder invariant).** "Snapshot absent ⇒ start never happened" is only true when the engine persists executions durably; pairing a durable stream store with an in-memory engine would re-dispatch executions that *did* run before the crash — a transaction duplicator. `TxFlowStream.builder(...)` **rejects** a durable `TxStreamStateStore` unless `engine.capabilities().durableExecution()` (P5) is true, at construction time.
- **Persisted plans never contain secrets (new in 0.4).** The engine already draws this line — sensitive parameters travel as secure-binding references and are persisted only as redacted fingerprints (`PersistedBinding`); the stream store must not become a second, plaintext secret store. The persisted planned request contains **only**: the portable-encoded definition, non-sensitive bindings, secure-binding *references* and value *fingerprints* — never resolved secret values, signer material, or runtime credentials. Crash re-dispatch resolves secure values afresh through the engine's secure-binding mechanism, exactly as a first dispatch would; a planned request whose sensitive values cannot be expressed as secure references is not persistable and fails the item typed at bind time in durable mode.
- Status *projections* stored alongside are denormalized reads — on any disagreement with the engine store, the engine wins and the projection is repaired per the Decision 4 transition table.
- Iteration 1 ships the in-memory implementation (single-process streams; crash loses ACCEPTED-but-unplanned items — bounded, documented; idempotent redelivery is the answer). Iteration 2 ships the durable implementation (reusing ADR 0003's relational infrastructure where present) plus **restart re-attach**: enumerate the stream's item bindings, resolve `DISPATCHING` ones as above, join with `listExecutions` (P4) / `executionSnapshot` + `executionEvents` (P2), re-project every item, resume watching non-terminal executions, and surface `RECOVERY_REQUIRED` items for operator-driven `engine.recover(...)`.

## Decision 6 — Planner SPI v2: plans produce execution requests; items are portable-only

```java
public interface TxStreamPlanner {
    TxStreamPlan plan(TxStreamPlanningContext context);   // context exposes StableIdFactory (Decision 3)
}
// TxStreamPlan = List<PlannedExecution>
// PlannedExecution = { TxFlow flow,
//                      String lane,                      // exactly one lane per execution (Decision 2)
//                      String idempotencyKey,            // flow-level claim key (Decision 3 rules)
//                      List<TxStreamPlannedItem> items } // itemId -> stepId
```

- The planner declares the lane and the idempotency key per generated flow; the stream mechanically converts a `PlannedExecution` into a `FlowExecutionRequest` (execution id = `stableId(claim)`, lane declared as the spending resource). The stream validates the mechanically checkable rules — an item mapped more than once, foreign/unmapped items, orphan steps, cross-lane flows — and rejects the plan typed on violation; full-request determinism is the planner's documented SPI obligation via `StableIdFactory` (Decision 3). **Several items sharing one step is allowed** (transaction-granular batching, below): the stream validates the mapping but not that a shared step's transaction serves each mapped item — that is the planner's owned guarantee, which the stream cannot verify. Planning stays pure (no I/O).
- **Portable payloads only (new in 0.2).** `FlowEngine` compiles definitions through the portable validator: a `FlowStep` carrying a Java transaction factory is rejected with `TXFLOW_NON_PORTABLE_FACTORY` (the MVP's own integration test submits exactly such steps). TxFlowStream v2 accepts only engine-compilable items — `TxPlan`-backed payloads and portable `FlowStep`s — and **validates this eagerly at `submit()`**, failing the item immediately with the portability diagnostic instead of poisoning a whole planned flow at compile time. The Java-factory item shape is listed in "Removed vs the MVP".
- Built-in planners, in delivery order: `perItem()` (default — one single-step flow per item, maximum lane parallelism, true per-item idempotency), `perWindow()` (one flow per window; flow-level dedup only, per Decision 3), `batching(...)` (iteration 2/3 — merges compatible payment-shaped intents into fewer transactions; item status becomes transaction-granular and per-item claims depend on the multi-claim engine extension).
- Planner failure fails **only that window's items** with a typed planning error; it never kills the worker.

## Decision 7 — Lifecycle and safety contract

Fixes designed in, not patched in (each maps to an Appendix A finding):

1. **Isolation with a bright line (sharpened in 0.3)**: *best-effort* surfaces — `TxStreamEventListener` callbacks and denormalized status-projection writes — are wrapped (`try/catch` + SLF4J warn), including inside failure handlers where the MVP's worker could die; a throwing listener can never kill the worker, wedge `drain()`, or abort a submission that was already accepted. *Authoritative* planning-metadata writes (item registry, execution bindings — Decision 5) are the explicit exception: they **fail closed**, rejecting the submit or failing the item typed before any engine dispatch. One rule, stated once: if losing the write loses *truth*, fail closed; if it loses only a *view* of truth, isolate and repair.
2. **Worker supervision**: the dispatch loop is supervised; an unexpected worker death fails pending items with a typed error, marks the stream unhealthy (`isHealthy()`), and unblocks all waiters.
3. **`trySubmit` never blocks**: submission uses bounded-queue operations without sharing a monitor with the blocking `submit`.
4. **Drain correctness**: `drain()` = stop accepting → flush → snapshot and await **all accepted items' completion promises** (Decision 4 — handles are one completer among validation, planning, binding, and cancellation outcomes; the accepted-but-unwindowed race is removed structurally). `awaitDrain(Duration)` throws a typed `TxStreamTimeoutException`.
5. **Two-tier termination (new in 0.2, resolves the close-vs-cancel contradiction):**
   - `close()` is **graceful**: equivalent to `drain()` then release — in-flight executions run to completion; nothing is cancelled. This makes `try`-with-resources safe and the Decision 1 example correct (the awaited receipt completes before the block exits; even un-awaited items would).
   - `abort(reason)` is **forced but honest about cooperativeness (sharpened in 0.4)**: `requestCancel()` is a cooperative signal, not termination — an engine execution may keep running after it and must still have somewhere to deliver its terminal outcome. `abort()` therefore: fails buffered items as `CANCELLED`, signals `requestCancel(reason)` to every active handle, **releases dispatch resources (queues, worker, sources) immediately but retains the minimal completion/projection machinery until every signaled handle reaches its terminal state**, and returns an `AbortReport` — the still-running execution ids plus a `CompletionStage` for full quiescence. `close(Duration graceDeadline)` composes drain-then-abort; it promises that no new work starts and cancellation is signaled by the deadline — it cannot and does not promise execution termination at the deadline.
   - `stream.cancel(itemId, reason)` is honest about flow granularity (sharpened in 0.3 — a shared `FlowExecutionHandle` can only be cancelled whole): buffered item → `CANCELLED` immediately; in-flight item in a **single-item flow** (`perItem()`) → cooperative engine cancel; in-flight item in a **shared multi-item flow** → the call is *rejected* with a typed result naming the `executionId` and the full affected item set — the caller may then explicitly escalate to `stream.cancelExecution(executionId, reason)`, which cancels the flow and projects every member item per the transition table. Item-level cancellation is never silently widened to neighbors.
6. **Stats from projections**: counters derive from the same event projection as item status — `SUBMITTED` counts transactions the chain saw, `cancelledCount` is reachable, failure paths cannot double-count.

## What is removed vs the MVP (no deprecation — unreleased API)

| Removed | Replaced by |
|---|---|
| `FlowExecutionRunner`, `FlowExecutorRunner`, `Builder.withRunner` | `FlowEngine` (test seam: engine with scripted backend + `FlowScheduler`, per ADR 0002 Decision 21) |
| `UtxoReservationPolicy` | `LanePolicy` + stream lane scheduler + engine spending resources |
| Builder `withChainingMode/withRollbackStrategy/withConfirmationConfig/withRetryPolicy/withFlowListener/withFlowStateStore`, `BackendService` param | definition `FlowExecutionSettings`, `FlowExecutionPolicy`, engine configuration |
| `TxWorkItem` payloads carrying Java transaction factories (`FlowStep` with factory) | portable-only payloads, validated at `submit()` (Decision 6) |
| Stream-asserted `SUBMITTED`; binary CONFIRMED/FAILED projection | event-driven projection with terminal-precedence rule (Decision 4) |
| Authoritative last-write-wins `TxStreamStateStore` | split-authority stores: fail-closed planning writes + CAS/transition-table projections (Decisions 5, 7) |
| `TxStreamPlan` = flows only; batch-sequence-derived ids | `PlannedExecution` with one lane + claim key + `StableIdFactory`-derived canonical ids (Decisions 3, 6) |
| `shutdown()`/`close()` with implicit cancellation ambiguity | `close()` (graceful) / `abort(reason)` / `close(Duration)` (Decision 7) |

## Delivery plan (MVP-first)

The former monolithic "iteration 1" is split into three independently verifiable slices (0.5, on external review recommendation — each slice ships with its own deterministic tests via scripted engine backend + `TestFlowScheduler`, no real-time latches). Where the decisions above say "iteration 1", they mean this 1A–1C series collectively; each constraint binds from the slice that introduces its feature:

**Iteration 1A — honest single-item core** (the true MVP):
1. Engine prerequisites P1 (`completion()`) and P2 (compaction-safe snapshot/event reads), each with its own engine tests, landed first.
2. Builder on `FlowEngine`; remove runner/legacy knobs. `perItem()` planner only — one flow per item, one statically configured lane (`LanePolicy.single(ResolvedLane)`) with mechanical funding-scope enforcement from day one, no custom planners yet.
3. Deterministic execution ids + two-phase binding handshake (Decisions 3, 5); attach-vs-conflict live redelivery with the versioned item fingerprint; `MATCHED` re-submit projection via P2.
4. Item completion promises + honest terminal projection (Decision 4): transition table with event-sequence ordering, authoritative fast-forward, terminal-precedence rule, hash preservation, reachable `CANCELLED`, `RECOVERY_REQUIRED` resolvable via read-through reconciliation + `stream.reconcile(itemId)`.
5. Graceful `close()`/`drain()` on item promises; per-item cancellation (all flows are single-item here, so cancel semantics are trivial); callback isolation + worker supervision.

**Iteration 1B — lane concurrency**:
1. `LanePolicy.explicit()` with dynamic `LaneIdentityResolver` resolution and multiple concurrent lanes (alias-sharing and overlap validation per Decision 2).
2. Per-canonical-identity FIFO dispatcher + global `maxInFlight`.
3. `abort(reason)`/`AbortReport`/`close(Duration)`; concurrency and contention tests.

**Iteration 1C — multi-item planning**:
1. `perWindow()` planner and the custom-planner SPI with `StableIdFactory` + plan validation (duplicate mappings, cross-lane flows).
2. Batch results incl. `PARTIALLY_COMPLETED`; shared-flow cancellation rules (`cancelExecution` escalation).

**Iteration 2 — UTXO throughput + durability**: `partitioned(n)` with fan-out bootstrap and lane-scoped coin selection; `byFundingAddress()`; durable stream store persisting bindings **plus portable-encoded planned flows** under the no-secrets policy (Decision 5) + the durable-pairing builder invariant (P5) + restart re-attach (P4 `listExecutions` through the ADR 0003 conformance kit); P3 busy-claim semantics ahead of any cross-process lane sharing; `batching(...)` planner for payment intents (flow-level claims until multi-claim lands).

**Iteration 3 — reach**: portable-template items (item = definition ref + `FlowBindings` — a stream of parameterized invocations of one compiled, fingerprinted flow); stream-owned reconciliation observer for `RECOVERY_REQUIRED` items; `java.util.concurrent.Flow` adapters; multi-instance stream ownership. *(3d delivered as **single-owner active/standby with lease-fenced failover**: an epoch-fenced `StreamOwnershipLease` in the `TxStreamStateStore` SPI — mirroring the engine's `ExecutionLease` — so two+ instances on one `streamId` elect exactly one ACTIVE owner that dispatches while the others stand by and take over on the owner's crash/expiry; only the current epoch-holder dispatches, a fenced owner steps down, and the standby resumes durable in-flight items via re-attach. **Active/active lane-partitioned ownership** — multiple instances each owning a disjoint lane subset — remains a **future extension** requiring the P3 cross-process lane-contention path and per-lane leases.)*

## Non-goals

- A general reactive-streams framework or message-broker connectors in the core module (sources remain a tiny SPI; Kafka/etc. adapters can live in extensions).
- Distributed stream coordination beyond what engine leases provide; cross-process lane sharing before P3.
- Transaction-count reduction in iteration 1 (`batching` is iteration 2/3).
- Zero-loss acceptance without a durable engine store (pre-planning buffer loss is documented; idempotent redelivery is the answer).
- Per-item dedup inside multi-item plans before the multi-claim engine extension (documented `perWindow()` limitation instead of a leaky promise).

## Open questions

1. **P2 surface**: expose snapshot/event reads on `FlowEngine` (proposed) vs a separate read-facade type; pagination shape for `executionEvents`.
2. **Multi-claim engine extension**: one execution registering N item-level claim aliases atomically — store semantics, conformance coverage, and whether `MATCHED`-by-alias returns the execution or a per-alias view. Prerequisite for per-item dedup in `batching(...)`.
3. **Fan-out bootstrap economics** (iteration 2): min-ADA per lane UTXO, lane consolidation on `close()`, and who pays — stream-managed vs application-provided lane addresses.
   - **Resolved (iteration 2c):** **application-provided lane addresses, application-set seed, optional bootstrap** — the stream never manages keys. `LanePolicy.partitioned(PartitionedLanes)` takes one funding source (`from` address or `from_ref`), N caller-owned lane addresses, and a caller-set `seedPerLane` amount. With `bootstrap` enabled (default) `start()` runs one idempotent engine execution — before opening for work, sequenced ahead of durable re-attach — that pays `seedPerLane` to each lane address; its claim (`bootstrap:<N>:<fingerprint(funding, addresses, seed)>`) makes it run at most once, matching on restart / a second instance (never re-splitting). A failed bootstrap fails `start()` typed (`TXSTREAM_BOOTSTRAP_FAILED`) rather than dispatching against unfunded lanes; the outcome is exposed as a `BootstrapReport`. Lane assignment is `hash(idempotencyKey) % N` (stable across restarts); each item's transaction is pinned to its lane address by the existing `enforceLaneFundingScope`. Min-ADA-per-lane and lane consolidation on `close()` are **left to the caller** (the seed and lane addresses are caller-chosen) and remain open for a later iteration.
   - **Configuration stability is funds-critical (2c review):** the funding source, seed, N, and the lane-address list **including its order** are load-bearing across restarts — they form the bootstrap claim, and the address order also defines the `partitionIndex → lane` mapping. Changing any of them mints a new split that re-drains the funding wallet, and reordering remaps lanes. A **durable** partitioned stream persists the bootstrap fingerprint and fails `start()` typed (`TXSTREAM_BOOTSTRAP_CONFIG_DRIFT`) on any drift, before submitting a split; a **non-durable** stream cannot persist and therefore cannot detect the drift (documented risk). A partitioned dispatch gate additionally guarantees no partitioned execution — including a durable re-attach's re-dispatched work — dispatches onto a lane until the bootstrap has funded it, so a failed/drifted bootstrap dispatches nothing.
4. **Receipt surface**: `executionId()`/event cursor is decided; exposing the raw `FlowExecutionHandle` is rejected (leaks engine API); is a read-only event-stream view on the receipt enough for observability tooling?
5. **P3 shape**: void-the-claim vs non-terminal `REJECTED_BUSY` outcome — the latter preserves an audit trail of contention; leaning `REJECTED_BUSY`, decided in the engine change's own review.
