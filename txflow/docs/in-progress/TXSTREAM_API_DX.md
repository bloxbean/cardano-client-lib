# TxStream API DX — Refactoring Proposal

- **Date:** 2026-08-21
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
| `completion().toCompletableFuture().join()` | Reactive-friendly receipt | No — most callers want `await()` |
| `join()` on `RECOVERY_REQUIRED` looks like "done" | Honest uncertain state | **Yes, but the API does not make the danger loud** |
| `trySubmit` `OK` + already-`FAILED` receipt | Eager validation still "accepted" | No — that is a wart |
| Mixed `getItemId()` vs `itemId()` | Two eras of style | No |
| ~50 public types in one package | Store SPI next to the front door | No |
| Error codes as string literals | Grew with slices | No |

The internals are not the problem. The **front door does not apply defaults the runtime already implements.**

---

## 2. Design principles

1. **Simple things are one screen.** Backend, signers, one executor, `open`, `submit`, `await`. A lane is not a beginner word.
2. **Defaults must be the safe defaults.** `perItem()` (true per-item dedup), `byFundingAddress()` (serialize one wallet, parallelize many), inherit the engine executor (no hidden threads), in-memory store (no false durability). Never default to `batching()` or `perWindow()`.
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

    TxStreamItemResult result = stream.submit("order-0042", plan).await();
    if (!result.isSuccessful()) {
        // FAILED, CANCELLED, or RECOVERY_REQUIRED — see result.status() / hash
    }
}

tasks.shutdown();
```

What disappeared: four supplier wrappers, the stream executor, the lane, `TxWorkItem.builder`, `start()`, `CompletionStage` ceremony.

What remains, and should: a backend, signers, **one caller-owned executor**, a stable stream id, a portable `TxPlan`, an idempotency id, and an explicit check of the outcome.

A caller who wants "throw unless confirmed, and never mistake uncertain for failure":

```java
TxStreamItemResult result = stream.submit("order-0042", plan).awaitConfirmed();
// throws TxStreamFailedException or TxStreamUncertainException (hash on the latter)
```

---

## 4. Progressive disclosure

```
Layer 0 — Beginner (open + submit + await)
    TxFlowStream.open(streamId, engine)
    stream.submit(itemId, plan).await() / awaitConfirmed()
    close() drains

Layer 1 — Everyday production
    FlowEngine.builder(backend).store(execStore).maintenanceExecutor(maint)
    TxFlowStream.builder(id, engine).stateStore(streamStore).start()
    trySubmit, listener, cancel, reconcile

Layer 2 — Throughput
    .lanes(LanePolicy.partitioned(...))  or  .lanes(LanePolicy.explicit()).laneResolver(...)
    .window(WindowPolicy.countOrTime(n, d)).planner(TxStreamPlanner.batching())
    .chaining(ChainingMode.PIPELINED)    // built-ins honor this

Layer 3 — Reach
    .template(id, flow)
    .ownership(token, ttl)
    .source(TxWorkSource.fromPublisher(...))
    custom TxStreamPlanner
```

One type, one runtime. Layers are builder knobs, not new classes (except the optional `FlowRuntime` in §6.12, which is explicitly a later convenience and owns threads).

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

**Template items** currently fail under `byFundingAddress()` (`TXSTREAM_LANE_REQUIRED`). That would make `open()` unusable for templates. Small internals change in `resolveLane` (not a rewrite):

```java
public Builder fallbackLane(ResolvedLane lane);   // used only when a lane cannot be derived
```

Resolution order:

1. Explicit `LanePolicy` if the caller set one (`single` / `explicit` / `partitioned` / `byFundingAddress`).
2. Else `byFundingAddress()`.
3. If derivation fails (template, or a plan with no funding source) **and** `fallbackLane` is set → use it.
4. Else fail typed as today (`TXSTREAM_LANE_UNDERIVABLE` / `TXSTREAM_LANE_REQUIRED`).

Beginners sending `TxPlan`s never set a lane. Template streams set `.fallbackLane(...)` or `.lane(...)` (keep `lane(ResolvedLane)` as the "whole stream is this wallet" shorthand — it still forces `LanePolicy.single`, which is what you want for a dedicated template stream).

Existing `.lane(ResolvedLane)` / `.lanes(...)` calls are unchanged.

### 5.2 Inherit the engine executor (and scheduled maintenance when possible)

Today the stream requires its own `Executor`. The engine already has one. Tests already pass `Runnable::run`.

Add on `FlowEngine` (tiny, generally useful):

```java
public Executor executor();
public Executor maintenanceExecutor();
```

Expose them on `EngineGateway` so `EngineTxFlowStream` does not take a `FlowEngine` dependency beyond the gateway:

```java
default Optional<Executor> dispatcher() { return Optional.empty(); }
default Optional<Executor> maintenance() { return Optional.empty(); }
```

`FlowEngineGateway` returns the engine's; `StubEngineGateway` can return `Runnable::run` so unit tests that omit `.executor(...)` still run deterministically.

Builder logic:

```
stream.executor
    ?? engine.dispatcher()
    else fail with today's teaching message

stream.maintenanceExecutor
    ?? (engine.maintenance() instanceof ScheduledExecutorService s ? s : null)
    ?? (stream.executor instanceof ScheduledExecutorService s ? s : null)
    else required only when a time window, ownership, or reconciliation observer is configured
```

Type mismatch to be honest about: engine maintenance is `Executor`, stream timers need `ScheduledExecutorService`. Do **not** wrap a plain `Executor` in a new scheduled pool (that would create threads). The beginner path (`open`, `perItem`, no window) needs **no** maintenance executor at all — that is already true. Inheritance only has to solve the **dispatch** executor for Layer 0.

### 5.3 `FlowEngine.builder(BackendService)`

`txflow` already depends on `backend`, and `FlowExecutor.create(BackendService)` already does this wrap. Copy it onto the engine so the stream's getting-started path does not teach four supplier types:

```java
public static Builder builder(BackendService backend) {
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
        /** build() + start(); the beginner/advanced shared entry. */
        public TxFlowStream start() {
            TxFlowStream stream = build();
            stream.start();
            return stream;
        }
    }
}
```

Keep `build()` unstarted — tests, and callers who need to wire a source before start, still can.

`try`-with-resources around `open` / `Builder.start()` is the documented path. `close()` remains graceful drain.

### 5.5 Submission sugar (keep `TxWorkItem` as the full model)

```java
// 90% case: item id is the idempotency key
TxStreamReceipt submit(String itemId, TxPlan plan);
TxStreamReceipt submit(String itemId, FlowStep step);

// already exists; keep
TxStreamReceipt submit(TxWorkItem item);
EmitResult trySubmit(TxWorkItem item);

// matching non-blocking sugar
default EmitResult trySubmit(String itemId, TxPlan plan) {
    return trySubmit(TxWorkItem.fromTxPlan(itemId, plan));
}
```

Do **not** add `submitPayment(receiver, amount)` that builds a `Tx` internally. Signers, `from` vs `fromRef`, and change addresses belong on `TxPlan`. Hiding them would create a second, incomplete transaction API.

Optional alias, no new type:

```java
public static TxWorkItem of(String itemId, TxPlan plan) {
    return fromTxPlan(itemId, plan);
}
```

### 5.6 Receipt: `await()`, `await(Duration)`, `awaitConfirmed()`

This is the second-highest DX win after defaults. Today's `completion().toCompletableFuture().join()` is both noisy and unsafe: `RECOVERY_REQUIRED` completes the future.

```java
public final class TxStreamReceipt {

    /** Block until the promise settles (CONFIRMED / FAILED / CANCELLED / RECOVERY_REQUIRED). */
    public TxStreamItemResult await();
    public TxStreamItemResult await(Duration timeout);   // throws TxStreamTimeoutException

    /**
     * Block until CONFIRMED.
     * FAILED / CANCELLED → TxStreamFailedException (code + message + result).
     * RECOVERY_REQUIRED → TxStreamUncertainException (hash + "do not resubmit blindly").
     */
    public TxStreamItemResult awaitConfirmed();
    public TxStreamItemResult awaitConfirmed(Duration timeout);

    public CompletionStage<TxStreamItemResult> completion(); // keep
}
```

New exception types, both subclasses of `TxStreamException`:

| Type | When | Caller action |
|---|---|---|
| `TxStreamFailedException` | `FAILED` or `CANCELLED` | Inspect `result()`, retry only with a new intent if the failure is conclusive |
| `TxStreamUncertainException` | `RECOVERY_REQUIRED` | Reconcile `transactionHash()` on chain. Never rebuild the item until the hash cannot land |

`await()` stays for callers who want to branch on `status()`. `awaitConfirmed()` is what getting-started should show second, once the first sample has introduced `isSuccessful()`.

Implementation is a thin wrapper over the existing promise. No runtime change.

### 5.7 Make `trySubmit` honest about validation

Today a non-portable item is `EmitResult.OK` with a receipt already `FAILED`. `isAccepted()` is true. Sources and beginners treat that as "in the pipeline."

Proposed contract (preview, so we can change it):

| Outcome | Today | Proposed |
|---|---|---|
| Buffer accepted, will run | `OK` + receipt | `OK` + receipt |
| Eager validation failed | `OK` + settled FAILED receipt | **`REJECTED`** + cause (`getRejection()`), optional `getReceipt()` still available for logging |
| Duplicate content | `CONFLICT` | unchanged |
| Same-content redelivery | `DUPLICATE_ATTACHED` | unchanged |
| Standby | `PAUSED` | unchanged |
| Full / closed | `FULL` / `CLOSED` | unchanged |

`submit(item)` can keep returning a settled FAILED receipt so a single `await` loop still works for mixed valid/invalid batches — **or** throw, matching `TXSTREAM_IDEMPOTENCY_KEY_REUSE`. Recommendation: **throw on `submit`, `REJECTED` on `trySubmit`.** One rule: if no work was created, it is an error, not an accepted item. Fire-and-forget callers who want "never throw" already have `trySubmit`.

Add `boolean isWorkCreated()` if `isAccepted()` must stay for a release, then deprecate it. Prefer a clean break: preview is allowed.

### 5.8 Fluent result accessors

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

Keep `getItemId()` etc. as `@Deprecated` one-line delegates for one preview cycle, then remove before 1.0.

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
    // ... every TXSTREAM_* the stream emits
    private TxStreamCodes() {}
}
```

`TxStreamException.code()` keeps returning the string. Internals can switch to the constants. Add a table to `package-info` / the design doc (already in [TXSTREAM_DESIGN.md](TXSTREAM_DESIGN.md) §20).

### 5.10 Listener: `onStreamAborted`

```java
default void onStreamAborted(String streamId, AbortReport report) {}
```

Close/drain/ownership are already signalled. Abort is not. Default method, no break.

Also tighten `submit`-not-started error text: *"Stream 'payouts' has not been started; call start() or use TxFlowStream.open(...)"* instead of the generic `TXSTREAM_CLOSED`.

### 5.11 Store types out of the front-door package

Move, do not redesign:

```
com.bloxbean.cardano.client.txflow.stream.store
    TxStreamStateStore
    TxStreamItemRecord, TxStreamBinding, TxStreamPlannedRecord
    TxStreamStoreCodec
    InMemoryTxStreamStore, InMemoryDurableTxStreamStore
    StreamOwnershipLease, BindingOutcome
```

Re-export with `@Deprecated` typedefs in `stream` for one preview cycle if anything external imported them (unlikely; still preview). `TxFlowStream.Builder.stateStore(...)` signature stays — importers update.

`PlannedExecution`, `TxStreamPlanner`, `StableIdFactory` stay in `stream`: they are the planner SPI, which advanced app developers implement.

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

## 6. Small internals exceptions that are worth it

These are not a rewrite of `EngineTxFlowStream`. Each is a localized change that the defaults above need, or that makes a power feature reachable without a custom planner.

### 6.1 `resolveLane` fallback (supports §5.1)

A few lines in `EngineTxFlowStream.resolveLane`: if mode is `BY_FUNDING_ADDRESS` (including the new default) and derivation fails, use `fallbackLane` when present. Template + `open()` becomes possible with `.fallbackLane(ResolvedLane.ofFundingRef(...))`.

### 6.2 Built-in pipelining knob (ADR Decision 2's missing lever)

Beginners do not need this. Advanced users currently cannot get intra-lane pipelining without a custom planner, because `perWindow()` / `batching()` emit `TxFlow`s with empty settings → `ChainingMode.SEQUENTIAL`.

```java
public Builder chaining(ChainingMode mode);   // default null = SEQUENTIAL (today)
```

`TxStreamPlanningContext` grows an optional `chainingMode()`. `BuiltInPlanners` copies it onto `TxFlow.builder(...).withChainingMode(...)`. `perItem()` ignores it (one step). Custom planners already build their own `TxFlow`.

This is the third UTXO lever the ADR promised. It does not change the beginner path.

### 6.3 Durable attach-after-eviction (correctness, not DX — do it anyway)

Documented in the readiness report: after live-map eviction a durable store still has the row, but `accept()` treats a redelivery as `registerItem` conflict. Fix in `accept()`: live miss → `stateStore.getItem` → attach-or-conflict from stored fingerprint. Same method, no new API. Call it out here because shipping nicer constructors on top of a broken redelivery path would be cosmetic.

### 6.4 `getItemStatus` / `reconcile` store-only `RECOVERY_REQUIRED`

Read-through repair should run when the row exists only in the durable store. Otherwise `open()` + restart looks like "status is stuck" to beginners who did the right thing (durable store). Small, same methods.

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

---

## 8. Before / after (ceremony)

| | Today | Proposed Layer 0 |
|---|---|---|
| Engine construction | 4 suppliers + executor + (optional) maintenance + signers | `FlowEngine.builder(backend).executor(tasks).signerRegistry(signers)` |
| Stream construction | lane + stream executor + build + start | `TxFlowStream.open("payouts", engine)` |
| Submit | `TxWorkItem.builder(id).withTxPlan(plan).withIdempotencyKey(id).build()` | `stream.submit(id, plan)` |
| Wait | `receipt.completion().toCompletableFuture().join()` | `receipt.await()` / `awaitConfirmed()` |
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

- **Additive and defaults:** `open`, `Builder.start()`, `submit(id, plan)`, `await*`, `fallbackLane`, `chaining`, `FlowEngine.builder(BackendService)`, engine executor getters, `TxStreamCodes`, `onStreamAborted`, result accessor aliases — no break.
- **Deliberate preview breaks (do in the same minor, changelog loudly):**
  1. `trySubmit` validation → `REJECTED` (and `submit` throws).
  2. Missing lane no longer throws at `build()` (code that *caught* that `IllegalStateException` is theoretical).
  3. Missing stream executor no longer throws if the engine has one.
- **Deprecate then remove before 1.0:** `getItemId()` style accessors; old `stream` FQCN of moved store types.

Existing tests that pass `.lane(...).executor(...)` keep passing. Tests that omit them start exercising the new defaults — add a focused `TxFlowStreamDefaultsTest` rather than rewriting the suite.

---

## 10. Suggested rollout

Order is "beginner can run a payment" first, polish last. Each step is independently reviewable.

| Phase | Change | Internals? | Unlocks |
|---|---|---|---|
| **A** | Default `byFundingAddress()` + `fallbackLane` | `resolveLane` only | No lane in getting-started |
| **A** | Inherit engine executor via gateway | getters + `build()` | No stream executor in getting-started |
| **A** | `open` / `Builder.start()` / `submit(id, plan)` / `await*` / `awaitConfirmed` | none | Target sample compiles |
| **A** | `FlowEngine.builder(BackendService)` | none (engine class) | Four suppliers gone |
| **B** | Honest `trySubmit` validation + started-message on `TXSTREAM_CLOSED` | accept path | Sources and beginners stop treating invalid as accepted |
| **B** | Durable attach-after-eviction + store-only reconcile | `accept` / `getItemStatus` | Production redelivery matches the javadoc |
| **C** | `chaining(ChainingMode)` on built-ins | planners + context | ADR pipelining lever |
| **C** | `onStreamAborted`, `TxStreamCodes` | none | Operability |
| **D** | Result accessor unification, store subpackage | imports | Package shape |
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

## 12. Open questions (product, not design blockers)

1. **`submit(id, plan)` throw vs settled FAILED receipt.** This proposal throws. If bulk loaders prefer "never throw, always await," keep returning the settled receipt on `submit` and only fix `trySubmit`. Recommend throw: one rule with `trySubmit` as the non-throwing mirror.
2. **`FlowRuntime` in 0.8 or later.** It is the only way to get to "a wallet, a backend, `submit`" with *zero* executors, and it is also the only type that would own threads. Suggest later; Phase A already hits the ADR bar with one executor.
3. **Default `maxInFlight`.** 16 is fine. A single-wallet `byFundingAddress()` stream only uses 1. No change.
4. **Rename `TxFlowStream` → `TxStream`.** Docs already say both. A rename is churn for no DX gain; keep the class name, keep "TxStream" as the product name.

---

## 13. Success bar

Phase A is done when:

- The getting-started sample is ≤ 20 lines of Java, names no lane and no stream executor, and runs on Yaci DevKit.
- A caller who uses `awaitConfirmed()` cannot mistake `RECOVERY_REQUIRED` for success without catching a typed exception that carries the hash.
- Every advanced knob that exists today is still reachable on `TxFlowStream.Builder`.
- `EngineTxFlowStream` is not split; no new dispatcher, no new thread pool, no new status machine.

That is the north star, implemented as defaults and facades on the API that already works.
