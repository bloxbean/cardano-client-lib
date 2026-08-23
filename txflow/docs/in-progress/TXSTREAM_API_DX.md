# TxStream API DX — Refactoring Proposal

- **Date:** 2026-08-23
- **Status:** Proposal. No code has been changed.
- **Constraint:** Keep `EngineTxFlowStream` as the runtime. Prefer builder defaults, factories, and facades. Touch internals only where a default cannot be expressed at the API layer.
- **Related:** [TXSTREAM_DESIGN.md](TXSTREAM_DESIGN.md), [TXSTREAM_READINESS_REPORT.md](TXSTREAM_READINESS_REPORT.md), ADR [0004](../adr/0004-txstream-on-flow-engine.md)

This is an API-design proposal, not a rewrite. The correctness core (honest states, write-ahead binding, lane FIFO, ownership fencing) stays. The goal is progressive disclosure: a beginner never learns what a lane, a planner, or a stream executor is, and an advanced user still reaches every capability that ships today.

---

## 1. Diagnosis

ADR 0004 principle 1: *"A wallet, a backend, `submit(...)` — that must be the whole beginner story. Lanes, planners, durable stores, and recovery are opt-in layers, never prerequisites."*

What a beginner actually has to assemble today:

```java
ExecutorService engineExecutor = Executors.newFixedThreadPool(4);
ExecutorService engineMaintenance = Executors.newSingleThreadExecutor();
ExecutorService streamExecutor = Executors.newFixedThreadPool(2);

FlowEngine engine = FlowEngine.builder(
                new DefaultUtxoSupplier(backend.getUtxoService()),
                new DefaultProtocolParamsSupplier(backend.getEpochService()),
                new DefaultTransactionProcessor(backend.getTransactionService()),
                new DefaultChainDataSupplier(backend))
        .executor(engineExecutor)
        .maintenanceExecutor(engineMaintenance)
        .signerRegistry(signers)
        .build();

try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
        .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))  // required
        .executor(streamExecutor)                                       // required
        .build()) {
    stream.start();                                                     // easy to forget
    TxStreamReceipt receipt = stream.submit(
            TxWorkItem.builder("pay-1")
                    .withTxPlan(plan)
                    .withIdempotencyKey("order-1")
                    .build());
    TxStreamItemResult outcome = receipt.completion()
            .toCompletableFuture().join();                              // ceremony + silent RECOVERY_REQUIRED
}
```

Roughly **13 concepts** and **three executors** before a single payment moves. None of that complexity is load-bearing for the default case (`perItem()`, one wallet, in-memory, no window).

| Friction | Why it exists | Load-bearing for beginners? |
|---|---|---|
| Four-supplier `FlowEngine.builder` | Pluggable backends | No — `FlowExecutor.create(BackendService)` already wraps this |
| Stream `executor` required | "never create threads" | No — the engine already has one |
| Stream `maintenanceExecutor` required for some features | Same rule | No for the default path (no timer, no ownership, no observer) |
| `lane(...)` required | Lane scheduling is real | No — `byFundingAddress()` derives it from the item's `from` / `from_ref` |
| Separate `build()` then `start()` | Tests need an unstarted stream | No — forgetting `start()` is the #1 lifecycle bug |
| `TxWorkItem.builder` + `withTxPlan` + `withIdempotencyKey` | Full item model | No — id can default as the idempotency key (`fromTxPlan` already does) |
| `completion().toCompletableFuture().join()` | Reactive-friendly receipt | No — most callers want `awaitConfirmed()` or `await()` |
| `join()` on `RECOVERY_REQUIRED` looks like "done" | Honest uncertain state | **Yes, but the API does not make the danger loud** |
| `trySubmit` `OK` + already-`FAILED` receipt | Eager validation still "accepted" | No — that is a wart |
| Mixed `getItemId()` vs `itemId()` | Two eras of style | No |
| ~50 public types in one package | Store SPI next to the front door | No |
| Error codes as string literals | Grew with slices | No |

The runtime core is not the DX problem. The **front door does not apply defaults the runtime already implements.** The separate durable redelivery/read-through holes remain correctness work and are called out explicitly in §6.

---

## 2. Design principles

1. **Simple things are one screen.** Backend, signers, one executor, `open`, `submit`, `awaitConfirmed`. A lane is not a beginner word.
2. **Defaults must be the safe defaults.** `perItem()` (true per-item dedup), `byFundingAddress()` (serialize one wallet, parallelize many), inherit the engine dispatch executor (no hidden threads), in-memory store (no false durability). Never default to `batching()` or `perWindow()`, and keep timer/lease maintenance explicit until the engine can distinguish a deliberately configured scheduler from its execution fallback.
3. **Dangerous outcomes are typed, not boolean.** `RECOVERY_REQUIRED` must not look like success. Validation failure must not look like acceptance.
4. **Power is still on the same type.** No parallel "simple stream" vs "real stream". One `TxFlowStream`. Beginners call `open`; advanced users call `builder`.
5. **Do not create threads inside the stream or the engine.** Caller-owned executors stay. Facades may *reuse* the engine's executor; they must not start new pools unless the caller opts into an explicit runtime object that owns them.
6. **Preview compatibility is source-level.** Existing `.lane(...).executor(...).build(); start();` keeps working. New defaults only fire when those knobs are omitted.
7. **Do not hide funds-critical contracts.** Batching re-batch, partitioned drift, ownership new-item-id stay documented and remain opt-in.

---

## 3. Target beginner story

After this refactor, the getting-started sample should compile as:

```java
ExecutorService tasks = Executors.newFixedThreadPool(4);

FlowEngine engine = FlowEngine.builder(backend)
        .executor(tasks)
        .signerRegistry(signers)
        .build();

try (TxFlowStream stream = TxFlowStream.open("payouts", engine)) {
    TxPlan plan = TxPlan.from(new Tx()
                    .payToAddress(receiver, Amount.ada(2))
                    .fromRef("account://sender"))
            .withSigner("account://sender");

    TxStreamItemResult result = stream.submit("order-0042", plan).awaitConfirmed();
}

tasks.shutdown();
```

What disappeared: four supplier wrappers, the stream executor, the lane, `TxWorkItem.builder`, `start()`, `CompletionStage` ceremony.

What remains, and should: a backend, signers, **one caller-owned executor**, a stable stream id, a portable `TxPlan`, an idempotency id, and an explicit check of the outcome.

A caller who deliberately wants to branch over every settled outcome can use the lower-level form:

```java
TxStreamItemResult result = stream.submit("order-0042", plan).await();
if (!result.isSuccessful()) {
    // FAILED, CANCELLED, or RECOVERY_REQUIRED — inspect status and hash
}
```

The getting-started path uses `awaitConfirmed()` so a beginner cannot accidentally treat an uncertain submission as success. It throws `TxStreamFailedException`, `TxStreamCancelledException`, or `TxStreamUncertainException` (the uncertain exception carries the latest result and transaction hash).

---

## 4. Progressive disclosure

```
Layer 0 — Beginner (open + submit + awaitConfirmed)
    TxFlowStream.open(streamId, engine)
    stream.submit(itemId, plan).awaitConfirmed()
    close() drains

Layer 1 — Everyday production
    FlowEngine.builder(backend).store(execStore).maintenanceExecutor(maint)
    TxFlowStream.builder(id, engine).stateStore(streamStore).start()
    trySubmit, listener, cancel, reconcile

Layer 2 — Throughput
    .lanes(LanePolicy.partitioned(...))  or  .lanes(LanePolicy.explicit()).laneResolver(...)
    .window(WindowPolicy.countOrTime(n, d)).planner(TxStreamPlanner.batching())
    .planner(TxStreamPlanner.perWindow(ChainingMode.PIPELINED))

Layer 3 — Reach
    .template(id, flow)
    .ownership(token, ttl)
    .source(TxWorkSource.fromPublisher(...))
    custom TxStreamPlanner
```

One type, one runtime. Layers are builder/planner knobs, not new runtimes (except the optional `FlowRuntime` in §5.12, which is explicitly a later convenience and owns threads).

---

## 5. Concrete API changes

Numbered so they can land as independent PRs. Unless noted, each is **API-layer only**.

### 5.1 Default the lane policy to `byFundingAddress()` — **the highest-leverage change**

Today `build()` throws if `lanePolicy == null`. Change:

```java
// TxFlowStream.Builder.build()
if (lanePolicy == null) {
    lanePolicy = LanePolicy.byFundingAddress();
}
```

Why this is the correct default:

- One funding wallet → one canonical identity → FIFO. No extra config, no UTXO races.
- Two senders in one stream → two lanes automatically → real parallelism.
- The item already names `from` / `from_ref`. The stream should not ask the caller to name it again.
- ADR Decision 2 already shipped this policy; it is just not the default.

**Template items** still require an explicit lane policy. A template's funding lives inside a potentially multi-step definition, and automatically falling back to one lane could be a scheduling lie when that definition has multiple funding sources. A dedicated template stream uses `.lane(...)`; a mixed plan/template stream uses an explicit policy and item lane.

Do **not** add a generic `fallbackLane` in the first release. Today both a missing funding source and an ambiguous plan containing both `from` and `from_ref` surface as `TXSTREAM_LANE_UNDERIVABLE`; a code-based fallback could silently accept malformed work. If a real mixed-stream use case later needs a default, add a narrowly named `templateLane(...)` or `defaultFundingLane(...)` and apply it only to the exact absence case—never to ambiguity, mismatch, scope violations, or non-portable payloads.

Beginners sending ordinary single-transaction `TxPlan`s never set a lane. Keep `lane(ResolvedLane)` as the "whole stream is this wallet" shorthand; it still forces `LanePolicy.single`, which is appropriate for a dedicated template stream or a plan whose funding source should be materialized from configuration.

Existing `.lane(ResolvedLane)` / `.lanes(...)` calls are unchanged.

### 5.2 Inherit the engine dispatch executor; keep maintenance explicit

Today the stream requires its own `Executor`. The engine already has one. Tests already pass `Runnable::run`.

Add on `FlowEngine` (tiny, generally useful; the application still owns it):

```java
public Executor executionExecutor();
```

Expose it on `EngineGateway` so `EngineTxFlowStream` does not take a `FlowEngine` dependency beyond the gateway:

```java
default Optional<Executor> dispatcher() { return Optional.empty(); }
```

`FlowEngineGateway` returns the engine's; `StubEngineGateway` can return `Runnable::run` so unit tests that omit `.executor(...)` still run deterministically.

Builder logic is deliberately limited to dispatch:

```
stream.executor
    ?? engine.dispatcher()
    else fail with today's teaching message
```

Do not inherit scheduled maintenance in Phase A. `FlowEngine` currently falls back to its execution executor when no maintenance executor was explicitly supplied, while stream timers need a scheduler that cannot be starved by blocking flow tasks. Merely checking `instanceof ScheduledExecutorService` cannot distinguish an intentionally provisioned maintenance scheduler from that fallback. Time windows, ownership, and the reconciliation observer therefore continue to require `.maintenanceExecutor(...)` on the stream.

A later additive API may expose `configuredMaintenanceExecutor()` (empty when the engine is using its execution fallback), after which an explicitly configured `ScheduledExecutorService` can be safely inherited. Never wrap a plain executor in a hidden scheduled pool.

### 5.3 `FlowEngine.builder(BackendService)`

`txflow` already depends on `backend`, and `FlowExecutor.create(BackendService)` already does this wrap. Copy it onto the engine so the stream's getting-started path does not teach four supplier types:

```java
public static Builder builder(BackendService backend) {
    Objects.requireNonNull(backend, "backend");
    return builder(
            new DefaultUtxoSupplier(backend.getUtxoService()),
            new DefaultProtocolParamsSupplier(backend.getEpochService()),
            new DefaultTransactionProcessor(backend.getTransactionService()),
            new DefaultChainDataSupplier(backend));
}
```

The four-supplier overload stays for non-`BackendService` hosts (Ogmios, custom). Durable mode still requires `.store(...)` and `.maintenanceExecutor(...)` — those are real constraints, not ceremony.

### 5.4 `open(...)` and fluent `Builder.start()` — stop requiring a separate `start()`

Forgetting `start()` is silent: `submit` throws `TXSTREAM_CLOSED`. Beginners should get a live stream.

```java
public interface TxFlowStream extends AutoCloseable {

    /** Build with defaults, start, return. Equivalent to builder(id, engine).start(). */
    static TxFlowStream open(String streamId, FlowEngine engine) {
        return builder(streamId, engine).start();
    }

    /** Idempotent start. Already exists; keep it. */
    void start();

    final class Builder {
        /** Build + start, aborting the unreachable instance if start fails. */
        public TxFlowStream start() {
            TxFlowStream stream = build();
            try {
                stream.start();
                return stream;
            } catch (RuntimeException | Error failure) {
                try {
                    stream.abort("Stream startup failed");
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }
    }
}
```

Keep `build()` unstarted — tests, and callers who need to wire a source before start, still can.

The cleanup path is part of the factory contract, not an implementation detail. `EngineTxFlowStream.start()` marks the instance started before bootstrap, ownership acquisition, durable re-attach, and source startup. Any of those operations may throw. Without the `abort` guard, `Builder.start()` would lose the only reference to a partially started stream, along with any source, timer, ownership lease, or queued work it had acquired. Use `abort`, not graceful `close`, so cleanup cannot wait indefinitely on a failed startup.

`try`-with-resources around `open` / `Builder.start()` is the documented path. `close()` remains graceful drain.

### 5.5 Submission sugar (keep `TxWorkItem` as the full model)

```java
// 90% case: item id is the idempotency key
TxStreamReceipt submit(String itemId, TxPlan plan);

// already exists; keep
TxStreamReceipt submit(TxWorkItem item);
EmitResult trySubmit(TxWorkItem item);

// matching non-blocking sugar
default EmitResult trySubmit(String itemId, TxPlan plan) {
    return trySubmit(TxWorkItem.fromTxPlan(itemId, plan));
}
```

Do **not** add `submitPayment(receiver, amount)` that builds a `Tx` internally. Signers, `from` vs `fromRef`, and change addresses belong on `TxPlan`. Hiding them would create a second, incomplete transaction API.

Do not add `TxWorkItem.of(...)`; `fromTxPlan(...)` already names the conversion precisely. Also defer `submit(String, FlowStep)`: the beginner path needs only `TxPlan`, while advanced callers already have `TxWorkItem.fromFlowStep(...)`. It can be added later if usage data justifies another overload.

### 5.6 Receipt: `await()`, `await(Duration)`, `awaitConfirmed()`

This is the second-highest DX win after defaults. Today's `completion().toCompletableFuture().join()` is both noisy and unsafe: `RECOVERY_REQUIRED` completes the future.

```java
public final class TxStreamReceipt {

    /** Block until the promise settles (CONFIRMED / FAILED / CANCELLED / RECOVERY_REQUIRED). */
    public TxStreamItemResult await();
    public TxStreamItemResult await(Duration timeout);   // throws TxStreamTimeoutException

    /**
     * Block until the receipt settles, then require the latest projection to be CONFIRMED.
     * FAILED → TxStreamFailedException.
     * CANCELLED → TxStreamCancelledException.
     * RECOVERY_REQUIRED → TxStreamUncertainException (hash + "do not resubmit blindly").
     */
    public TxStreamItemResult awaitConfirmed();
    public TxStreamItemResult awaitConfirmed(Duration timeout);

    public CompletionStage<TxStreamItemResult> completion(); // keep
}
```

New exception types, all subclasses of `TxStreamException` and all carrying the complete `TxStreamItemResult`:

| Type | When | Caller action |
|---|---|---|
| `TxStreamFailedException` | `FAILED` | Inspect `result()` and its cause; retry only when the failure is conclusive and retryable |
| `TxStreamCancelledException` | `CANCELLED` | Treat as an explicit cancellation outcome, not a generic failure |
| `TxStreamUncertainException` | `RECOVERY_REQUIRED` | Reconcile `transactionHash()` on chain. Never rebuild the item until the hash cannot land |

`awaitConfirmed()` is the getting-started default. `await()` stays for callers who deliberately want to branch on every settled status.

The existing completion promise is a point-in-time settlement: it completes on `RECOVERY_REQUIRED` and is not re-completed if later reconciliation repairs the live projection. `awaitConfirmed()` must therefore not blindly classify the promise's historical value. After waiting, it reads `current()` once and classifies that latest snapshot; a receipt repaired to `CONFIRMED` before classification returns successfully. It does **not** perform hidden network reconciliation or wait indefinitely for a future repair. If the latest projection remains `RECOVERY_REQUIRED`, it throws `TxStreamUncertainException` immediately and loudly.

`await(Duration)` and `awaitConfirmed(Duration)` require a positive duration, preserve the thread interruption flag, translate interruption to `TXSTREAM_INTERRUPTED`, and throw `TxStreamTimeoutException` on expiry. Expand that exception's javadoc beyond drain-only use.

### 5.7 Make `trySubmit` honest about validation

Today a non-portable item is `EmitResult.OK` with a receipt already `FAILED`. `isAccepted()` is true. Sources and beginners treat that as "in the pipeline."

Proposed contract (preview, so we can change it):

| Outcome | Today | Proposed |
|---|---|---|
| Buffer accepted, will run | `OK` + receipt | `OK` + receipt |
| Eager validation failed | `OK` + settled FAILED receipt | **`REJECTED`** + cause (`getRejection()`), no receipt |
| Duplicate content | `CONFLICT` | unchanged |
| Same-content redelivery | `DUPLICATE_ATTACHED` | unchanged |
| Standby | `PAUSED` | unchanged |
| Full / closed | `FULL` / `CLOSED` | unchanged |

`submit(item)` throws the same typed cause that `trySubmit(item)` places in `REJECTED`. One rule: if no registered/buffered stream work was created, it is a rejection, not an accepted failed item. Fire-and-forget callers who want "never throw for a submission outcome" already have `trySubmit`.

A rejected item has no receipt, does not increment accepted/failed item counters, does not enter retention, and does not fire `onItemAccepted`. It may be corrected and retried with the same item id. This deliberately replaces today's mixed behavior: malformed identifiers and registration failures already reject, while portability, lane-content, and template validation currently create retained live-only failures reported as `OK`.

`isAccepted()` remains and means exactly `OK` or `DUPLICATE_ATTACHED`; no `isWorkCreated()` alias is needed once validation no longer returns `OK`.

### 5.8 Fluent result accessors — additive, deferred polish

Pick one style. Receipts already use `itemId()`, `executionId()`, `current()`. Align `TxStreamItemResult` and `EmitResult`:

```java
public String streamId();
public String itemId();
public TxStreamItemStatus status();
public String executionId();
public String transactionHash();
public Throwable error();
public Instant updatedAt();
```

Keep `getItemId()` etc. as non-deprecated delegates for now. The same API still contains JavaBean accessors on `TxWorkItem`, `EmitResult`, and `TxStreamException`, while records and receipts are record-like; deprecating only one result type would replace inconsistency with a different inconsistency. Make one repository-wide style decision before 1.0, informed by framework/tool compatibility, rather than coupling accessor churn to the DX release.

Same pass on `TxStreamStats` (`acceptedItemCount()` is already record-like — keep it).

### 5.9 Public error-code catalog

```java
public final class TxStreamCodes {
    public static final String CLOSED = "TXSTREAM_CLOSED";
    public static final String DUPLICATE_ITEM = "TXSTREAM_DUPLICATE_ITEM";
    public static final String IDEMPOTENCY_KEY_REUSE = "TXSTREAM_IDEMPOTENCY_KEY_REUSE";
    public static final String LANE_UNRESOLVED = "TXSTREAM_LANE_UNRESOLVED";
    public static final String OWNERSHIP_LOST = "TXSTREAM_OWNERSHIP_LOST";
    public static final String RECOVERY_UNCONFIRMED = /* used by TxStreamUncertainException */;
    // ... every core txflow TXSTREAM_* code
    private TxStreamCodes() {}
}
```

`TxStreamException.getCode()` keeps returning the string; an additive `code()` alias may be considered with the broader accessor decision. Internals can switch to the constants. The core catalog covers codes emitted by the `txflow` module; extensions such as the RDBMS store own extension-specific catalogs rather than forcing the core module to enumerate downstream codes. Add a table to `package-info` / the design doc (already in [TXSTREAM_DESIGN.md](TXSTREAM_DESIGN.md) §20).

### 5.10 Listener: `onStreamAborted`

```java
default void onStreamAborted(String streamId, AbortReport report) {}
```

Close/drain/ownership are already signalled. Abort is not. Default method, no break.

Specify ordering and cardinality: `onStreamAborted` fires exactly once after the `AbortReport` is published and before the existing exactly-once `onStreamClosed`. The callback observes a report whose `quiescence()` may still be incomplete. Add reentrant-abort and listener-failure tests so the new callback cannot widen abort or prevent close notification.

Also tighten `submit`-not-started error text: *"Stream 'payouts' has not been started; call start() or use TxFlowStream.open(...)"* instead of the generic `TXSTREAM_CLOSED`.

### 5.11 Defer moving store types out of the front-door package

The eventual package shape may be cleaner as:

```
com.bloxbean.cardano.client.txflow.stream.store
    TxStreamStateStore
    TxStreamItemRecord, TxStreamBinding, TxStreamPlannedRecord
    TxStreamStoreCodec
    InMemoryTxStreamStore, InMemoryDurableTxStreamStore
    StreamOwnershipLease, BindingOutcome
```

Do not perform this move in the DX release. Java has no typedef/re-export mechanism: deprecated bridge interfaces do not preserve method signatures whose parameters are moved records, and final records/classes cannot be subclassed into compatibility aliases. A move would break external `TxStreamStateStore` implementations and the shipped RDBMS extension at source and potentially binary level.

Package size does not affect the documented beginner imports, so functional DX wins first. If the move is still desired before 1.0, make one explicit clean break with extension migration in the same release. `PlannedExecution`, `TxStreamPlanner`, and `StableIdFactory` remain in `stream` because they are the planner SPI advanced applications implement.

### 5.12 Optional later: `FlowRuntime` — the only type allowed to own threads

Not in the first drop. Recorded so we do not "fix" DX by putting `Executors.newFixedThreadPool` inside `EngineTxFlowStream`.

```java
public final class FlowRuntime implements AutoCloseable {
    public static FlowRuntime create(BackendService backend, SignerRegistry signers);
    public FlowEngine engine();
    public TxFlowStream open(String streamId);
    @Override public void close(); // shutdown pools + close open streams
}
```

This is for scripts, samples, and Java 21 virtual-thread apps that do not want to see `ExecutorService`. Production servers keep owning their pools. Until this exists, getting-started shows **one** `ExecutorService`.

---

## 6. Focused internals changes that are worth it

These are not a rewrite of `EngineTxFlowStream`, but the durable fixes do require explicit store-SPI and hydration design rather than being described as a few lines in the accept/read paths.

### 6.1 Distinguish missing and ambiguous funding sources

Default `byFundingAddress()` makes its diagnostics part of the beginner experience. Split the current shared `TXSTREAM_LANE_UNDERIVABLE` path so a plan containing both `from` and `from_ref` reports a distinct ambiguity diagnostic. A genuinely absent funding source remains underivable and teaches the caller to add `from` / `from_ref` or configure `.lane(...)`. No error is silently converted into a fallback lane.

### 6.2 Scope built-in pipelining to the planner that can use it

Beginners do not need this. Advanced users currently cannot get intra-lane pipelining without a custom planner, because `perWindow()` / `batching()` emit `TxFlow`s with empty settings → `ChainingMode.SEQUENTIAL`.

Prefer a planner-local API:

```java
TxStreamPlanner.perWindow(ChainingMode.PIPELINED)
```

or a small `PerWindowOptions` if more execution settings are expected. A stream-level `.chaining(...)` overstates its reach: it is a no-op for `perItem`, has no meaningful effect on one-step batching executions, may be ignored by custom planners, and should not override registered template settings. The overload must document whether `ChainingMode.BATCH` is supported as well as `PIPELINED`; reject unsupported modes rather than silently downgrading them.

This is the third UTXO lever the ADR promised. It does not change the beginner path.

### 6.3 Durable attach-after-eviction (correctness, not DX — do it anyway)

Documented in the readiness report: after live-map eviction a durable store still has the registration, but `accept()` treats a redelivery as `registerItem` conflict. `stateStore.getItem(...)` is insufficient because it returns only the denormalized projection; attach-versus-conflict needs the registration fingerprint.

Prefer an atomic SPI operation:

```java
RegistrationOutcome registerOrMatch(TxStreamItemRecord candidate);
```

with `REGISTERED`, `MATCHED`, and `CONFLICT` outcomes carrying the stored registration where relevant. This gives the in-memory and RDBMS stores one race-safe contract for live misses, concurrent callers, and post-eviction redelivery. A less disruptive `getRegistration(...)` plus compare-after-conflict fallback is acceptable for preview, but a pre-read alone leaves a lookup/register race.

On `MATCHED`, hydrate a settled live `ItemState` from the stored projection and registration, seed its projection sequence correctly, complete its receipt, and return `DUPLICATE_ATTACHED` / the existing receipt semantics. Add the same-content, different-content, concurrent-redelivery, and RDBMS contract cases. Shipping nicer constructors on top of a broken durable redelivery path would be cosmetic.

### 6.4 `getItemStatus` / `reconcile` store-only `RECOVERY_REQUIRED`

Read-through repair should run when the row exists only in the durable store. Otherwise `open()` + restart looks like "status is stuck" to callers who configured durability correctly.

This needs a defined hydration path, not just an engine lookup: reconstruct enough live state to preserve the item/execution/step mapping, seed the projection CAS sequence, run the normal authoritative transition logic, persist the repaired projection, and notify listeners exactly once. Share that hydration helper with attach-after-eviction and re-attach rather than creating a second projection path.

---

## 7. What we will not do

| Idea | Why not |
|---|---|
| Create threads inside `EngineTxFlowStream` / `FlowEngine` | Breaks ADR 0002 Decision 21; tests and Java 21 virtual-thread choice go away |
| Default to `batching()` or `perWindow()` | Flow-level dedup; re-batch double-pays. Unsafe default |
| Auto-enable durable stores | Silent persistence of planning metadata against an in-memory engine is the double-submit case the builder already rejects |
| `submitPayment(address, ada)` that builds a `Tx` | Second, incomplete transaction API; signers and `fromRef` would be guessed wrong |
| Expose `FlowExecutionHandle` on the receipt | Leaks engine API (ADR Decision, open question 4, rejected) |
| Auto-start on `build()` | Tests and source-before-start wiring break. `open()` / `Builder.start()` are the started path |
| Merge engine and stream into one builder | Layering is the product: many streams can share one engine; engine is useful without a stream |
| A second `SimpleTxStream` type | Two runtimes to document, test, and keep in sync. Defaults on the real type |
| Hide `RECOVERY_REQUIRED` behind retries | That is how the preprod soak double-paid. Make it loud (`awaitConfirmed`) instead |
| Default `LanePolicy.single` to a missing address | There is no address until the first item. `byFundingAddress()` *is* the single-wallet default |
| Generic `fallbackLane` on derivation errors | Can mask an ambiguous or malformed funding declaration; templates use an explicit lane |
| Infer a timer scheduler from any scheduled execution pool | Cannot tell a deliberately provisioned maintenance scheduler from the engine's execution fallback; risks timer/lease starvation |

---

## 8. Before / after (ceremony)

| | Today | Proposed Layer 0 |
|---|---|---|
| Engine construction | 4 suppliers + executor + (optional) maintenance + signers | `FlowEngine.builder(backend).executor(tasks).signerRegistry(signers)` |
| Stream construction | lane + stream executor + build + start | `TxFlowStream.open("payouts", engine)` |
| Submit | `TxWorkItem.builder(id).withTxPlan(plan).withIdempotencyKey(id).build()` | `stream.submit(id, plan)` |
| Wait | `receipt.completion().toCompletableFuture().join()` | `receipt.awaitConfirmed()` (`await()` for explicit status branching) |
| Concepts to name | ~13 | ~6 (backend, signers, executor, engine, stream id, plan) |
| Executors to create | 3 | **1** (2 when durable: tasks + maintenance) |

Layer 1 durable still looks like power-user code, and it should — crash safety is not a default:

```java
FlowEngine engine = FlowEngine.builder(backend)
        .executor(tasks)
        .maintenanceExecutor(maintenance)
        .store(execStore)
        .signerRegistry(signers)
        .build();

try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
        .stateStore(streamStore)
        .start()) {
    stream.submit("order-1", plan).awaitConfirmed();
}
```

No lane. No third executor. Fluent start. Uncertain outcomes cannot be ignored if the caller uses `awaitConfirmed()`.

---

## 9. Compatibility

The API is preview (`0.8.0-pre*`). Recommended policy:

- **Additive and defaults:** `open`, exception-safe `Builder.start()`, `submit(id, plan)`, `await*`, planner-local per-window chaining, `FlowEngine.builder(BackendService)`, the engine execution-executor getter, `TxStreamCodes`, `onStreamAborted`, and optional result accessor aliases — no source break for existing explicit configurations.
- **Deliberate preview breaks (do in the same minor, changelog loudly):**
  1. `trySubmit` validation → `REJECTED` (and `submit` throws).
  2. Missing lane no longer throws at `build()` (code that *caught* that `IllegalStateException` is theoretical).
  3. Missing stream executor no longer throws if the engine has one.
  4. The durable store registration SPI may gain an outcome-returning operation; ship core and RDBMS implementations together and document the migration for custom stores.
- **Deferred compatibility decisions:** accessor deprecation/removal and moving store FQCNs. Do not mix those broad source changes into the functional DX release.

Existing tests that pass `.lane(...).executor(...)` keep passing. Tests that omit them start exercising the new defaults — add a focused `TxFlowStreamDefaultsTest` rather than rewriting the suite.

---

## 10. Suggested rollout

Order is "beginner can run a payment" first, polish last. Each step is independently reviewable.

| Phase | Change | Internals? | Unlocks |
|---|---|---|---|
| **A** | Default `byFundingAddress()` + distinct missing/ambiguous diagnostics | lane derivation | No lane in getting-started without masking malformed work |
| **A** | Inherit engine executor via gateway | getters + `build()` | No stream executor in getting-started |
| **A** | Exception-safe `open` / `Builder.start()` + `submit(id, plan)` + `await*` / `awaitConfirmed` | lifecycle cleanup + receipt facade | Target sample compiles and startup failures do not leak an unreachable stream |
| **A** | `FlowEngine.builder(BackendService)` | none (engine class) | Four suppliers gone |
| **B** | Honest `trySubmit` validation + lifecycle-specific submission diagnostics | accept path | Sources and beginners stop treating invalid as accepted |
| **B** | Durable registration match + shared hydration for attach/reconcile | store SPI + accept/read paths | Production redelivery and repair match the javadoc |
| **C** | Planner-local per-window chaining mode/options | planner | ADR pipelining lever without a misleading global knob |
| **C** | `onStreamAborted`, `TxStreamCodes` | none | Operability |
| **D** | Effective configuration + lifecycle status snapshots | additive API | Advanced operability and explainable defaults |
| **Pre-1.0 decision** | Result accessor convention, possible store subpackage | broad source/import changes | Consistent final package/API shape |
| **E** (optional) | `FlowRuntime` | new type, owns pools | Scripts / samples with zero executor lines |

Phase A is the DX release. B is the correctness/honesty release that should ship with it. C–E can trail.

---

## 11. Documentation changes that ship with Phase A

The API is not beginner-friendly until the sample is. When Phase A lands:

1. Rewrite [txstream-getting-started.mdx](../../docs/content/preview/txflow/txstream-getting-started.mdx) to the §3 sample. Remove the "honest note on ceremony" or replace it with "durable and multi-lane are opt-in."
2. `package-info.java` beginner snippet must compile against the new defaults.
3. Teach `await()` vs `awaitConfirmed()` next to `RECOVERY_REQUIRED` in getting-started, not only in the durability guide.
4. ADR 0004 Decision 1 flagship example: drop required `.executor` on the stream; show `open` or `builder.start()`.
5. Internals doc: default lane is `byFundingAddress()`; stream executor inherited.

---

## 12. Open questions and later enhancements

1. **`FlowRuntime` in 0.8 or later.** It is the only way to get to "a wallet, a backend, `submit`" with *zero* executors, and it is also the only type that would own threads. Suggest later; Phase A already hits the ADR bar with one executor. If implemented, use a builder with explicit pool sizing/thread naming and define stream-close-before-pool-shutdown ordering.
2. **Default `maxInFlight`.** 16 is fine. A single-wallet `byFundingAddress()` stream only uses 1. No change.
3. **Rename `TxFlowStream` → `TxStream`.** Docs already say both. A rename is churn for no DX gain; keep the class name, keep "TxStream" as the product name.
4. **Effective configuration snapshot.** Once defaults are hidden, advanced users need to explain what actually ran. Consider an immutable `configuration()` view containing the effective lane policy, whether the dispatcher was inherited or explicit, planner/window policy, durability, ownership, and limits. Do not expose or transfer ownership of executor instances through it.
5. **Lifecycle status.** `TXSTREAM_CLOSED` currently covers not-started, draining/closed, unhealthy, and blocking submit to an ownership standby, while `trySubmit` already distinguishes `PAUSED`. Consider a derived status such as `NEW`, `STARTING`, `ACTIVE`, `STANDBY`, `DRAINING`, `CLOSED`, `ABORTED`, and `UNHEALTHY`, plus a distinct `TXSTREAM_PAUSED`/not-active exception for blocking standby submission before 1.0.
6. **Ignored-option validation.** Builder or planner factories should reject options that cannot affect the selected planner rather than accepting silent no-ops. This matters especially for chaining, windows, custom planners, and template execution settings.

---

## 13. Success bar

Phase A is done when:

- The getting-started sample is ≤ 20 lines of Java, names no lane and no stream executor, and runs on Yaci DevKit.
- `open()` / `Builder.start()` abort a partially started instance and rethrow the original startup failure with cleanup failures suppressed.
- A caller who uses `awaitConfirmed()` cannot mistake `RECOVERY_REQUIRED` for success; the typed exception carries the latest result and hash, while a projection already repaired to `CONFIRMED` succeeds.
- Timed waits preserve interruption and time out deterministically.
- Invalid work is never reported `OK`, counted accepted, retained, or announced through `onItemAccepted`.
- Every advanced knob that exists today is still reachable on `TxFlowStream.Builder`.
- `EngineTxFlowStream` is not split; no new dispatcher, no new thread pool, no new status machine.

That is the north star, implemented as defaults and facades on the API that already works.
