# ADR 0005: Progressive TxStream API for Beginner Accessibility and Advanced Control

**Status**: Proposed

**ADR Document Version**: 1.0.0

**Date**: 2026-08-23

**Last Updated**: 2026-08-23

**Review State**: Initial review draft (review round 1)

**Target Release**: Next TxStream preview release; exact version to be decided during review

**Modules**: `txflow`, `txflow-extensions:txflow-store-rdbms`, user documentation

**Related ADRs**: [ADR 0002: Portable TxFlow Contract, Compilation, Execution, and Recovery](0002-portable-txflow-contract-and-runtime.md), [ADR 0003: Relational Durable Store Extension for TxFlow](0003-relational-durable-store-extension.md), [ADR 0004: TxFlowStream v2](0004-txstream-on-flow-engine.md)

**Source Proposal**: [TxStream API DX — Refactoring Proposal](../docs/in-progress/TXSTREAM_API_DX.md)

**Supersedes**: None. This ADR refines the public front door and selected correctness contracts of ADR 0004; it does not replace its runtime architecture.

## ADR Version History

The ADR document version is independent of the library release version.

| ADR version | Date | Author | Review state | Summary |
|-------------|------|--------|--------------|---------|
| 1.0.0 | 2026-08-23 | Bloxbean / CCL maintainers with Codex review | Initial review draft | Defines a progressively disclosed TxStream API, safe defaults, exception-safe startup, typed receipt waiting, honest validation rejection, advanced planner controls, compatibility boundaries, and a phased implementation and verification plan. |

### Versioning Rules for This ADR

- Increment the patch version for clarifications, corrections, examples, and editorial changes that do not alter a proposed decision.
- Increment the minor version when a proposed API, behavior, compatibility rule, or implementation phase changes while the ADR remains `Proposed`.
- Increment the major version when an accepted decision is replaced incompatibly.
- Add a version-history row for every version change; do not rewrite earlier history.
- Record unresolved reviewer disagreements in [Open Questions](#open-questions) until review reaches a decision.
- Expected status progression is `Proposed` -> `Accepted` -> `Implementing` -> `Implemented`. Use `Rejected` or `Superseded` where appropriate.

## Review Record

Each review round adds a row here and a corresponding ADR version-history entry. Findings are incorporated, explicitly rejected with rationale, or retained as numbered open questions; they are not silently dropped between versions.

| Review round | ADR version | Date | Reviewer | Outcome | Resolution summary |
|--------------|-------------|------|----------|---------|--------------------|
| 1 | 1.0.0 | 2026-08-23 | Pending maintainer and external review | Open | Initial decision draft prepared from the DX proposal and independent source review. |

Reviewers should cite decision, implementation-phase, or open-question numbers. A review round is complete when every raised finding has a recorded disposition and the next ADR version captures all accepted changes.

## Executive Summary

TxStream has a strong correctness runtime but a heavy public front door. Sending one payment currently requires users to understand the engine's four backend suppliers, multiple executors, lane scheduling, explicit lifecycle startup, `TxWorkItem`, completion stages, and the special meaning of `RECOVERY_REQUIRED`. That is too much prerequisite knowledge for a beginner and conflicts with ADR 0004's principle that a backend, wallet, and `submit(...)` should be the whole beginner story.

This ADR introduces **progressive disclosure on the existing `TxFlowStream` type**:

1. `FlowEngine.builder(BackendService)` hides the standard supplier adapters.
2. A stream inherits the engine's caller-owned execution executor when no stream executor is set.
3. The default lane policy derives a safe lane from each transaction's `from` or `from_ref` funding source.
4. `TxFlowStream.open(...)` and `Builder.start()` return an already-started stream and clean up a partially started instance if startup fails.
5. `submit(itemId, TxPlan)` covers the common submission case while `TxWorkItem` remains the full advanced model.
6. `awaitConfirmed()` is the beginner default and makes failure, cancellation, and uncertain on-chain disposition loud and typed. `await()` remains the explicit all-outcomes API.
7. Eager validation rejection is never reported as accepted work.
8. Throughput, durability, ownership, sources, templates, and custom planning remain reachable through the same builder and planner APIs.

The decision does **not** create another runtime, create hidden threads, auto-enable unsafe batching or durability, hide uncertain outcomes, or weaken any funds-safety invariant established by ADRs 0002–0004.

## Context

### Current beginner cost

The current path requires approximately thirteen concepts and normally three executors before one payment moves:

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
        .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
        .executor(streamExecutor)
        .build()) {
    stream.start();
    TxStreamReceipt receipt = stream.submit(
            TxWorkItem.builder("pay-1")
                    .withTxPlan(plan)
                    .withIdempotencyKey("order-1")
                    .build());
    TxStreamItemResult result = receipt.completion().toCompletableFuture().join();
}
```

Each underlying concept exists for a valid advanced use case, but most are not load-bearing for the default `perItem()`, in-memory, single-transaction path:

| Friction | Why it exists | Required for the beginner path? |
|----------|---------------|---------------------------------|
| Four engine suppliers | Pluggable and custom backend hosts | No; `BackendService` already aggregates the standard services |
| Separate stream executor | Explicit caller-owned dispatch | No; the engine already has a caller-owned execution executor |
| Maintenance scheduler | Timed windows, ownership, reconciliation, durable engine leases | No for an untimed, non-durable beginner stream |
| Required lane policy | UTXO-native scheduling | No when a transaction already names its funding source |
| `build()` followed by `start()` | Unstarted instances are useful to tests and advanced wiring | No; forgetting `start()` is a common lifecycle error |
| Full `TxWorkItem` builder | Metadata, custom idempotency keys, lanes, templates, bindings | No for one `itemId` + `TxPlan` |
| Completion-stage join | Non-blocking integration | No for a caller that wants a synchronous outcome |
| Raw settled `RECOVERY_REQUIRED` | Honest uncertain-submission state | The state is required, but it must not look successful |

### Current strengths that must remain

This decision is a public-API refinement, not a runtime rewrite. The following existing properties remain authoritative:

- engine truth owns execution outcomes;
- the stream store owns planning metadata and item-to-execution bindings;
- write-ahead binding happens before engine start;
- lane FIFO is keyed by canonical spending identity;
- explicit lane policies and `maxInFlight` expose safe parallelism;
- submitted-but-unconfirmed work becomes `RECOVERY_REQUIRED`, retaining the transaction hash;
- `perItem()` is the default and provides true per-item idempotency;
- batching and per-window planning remain opt-in because their deduplication contracts differ;
- caller-owned executors, clocks, stores, sources, and schedulers remain caller-owned;
- `EngineTxFlowStream` remains the runtime implementation.

### Remaining correctness gaps adjacent to DX

Two durable-path gaps are included because a simpler API must not make an incomplete durability contract easier to adopt:

1. after live-map eviction, a durable store may retain an item registration and projection, but same-content redelivery conflicts instead of attaching because the live state no longer carries the stored fingerprint;
2. `getItemStatus` and `reconcile` do not repair a store-only `RECOVERY_REQUIRED` row because they require a live `ItemState`.

These are correctness workstreams with store and hydration implications, not merely constructor polish.

## Goals

- Make the common backend + signers + payment path understandable in one screen.
- Make the default API safe for one wallet and naturally parallel for multiple disjoint funding sources.
- Make confirmed success the beginner-default waiting contract.
- Make uncertain, failed, cancelled, rejected, attached, paused, full, and closed outcomes distinguishable.
- Preserve a single public stream type and a single runtime implementation.
- Keep all current advanced controls reachable without downcasting or switching to a different API family.
- Preserve explicit executor ownership and avoid hidden threads.
- Preserve source compatibility for existing callers that explicitly configure lanes, executors, and lifecycle.
- Fix durable redelivery and read-through behavior before presenting the durable path as complete.
- Provide a phased implementation plan with focused regression and integration gates.

## Non-Goals

- Rewriting or splitting `EngineTxFlowStream` as part of the API release.
- Merging `FlowEngine` and `TxFlowStream` into one builder or lifecycle.
- Creating a second `SimpleTxStream` facade/runtime.
- Creating executors, schedulers, or timers inside the engine or stream.
- Defaulting to `batching()` or `perWindow()`.
- Automatically enabling a durable stream or engine store.
- Hiding `RECOVERY_REQUIRED` behind automatic rebuilds or blind resubmission.
- Adding a second transaction-construction API such as `submitPayment(address, amount)`.
- Exposing `FlowExecutionHandle` through stream receipts.
- Automatically starting streams returned by `build()`.
- Moving the public store SPI package or removing JavaBean accessors in the initial DX release.
- Solving active-active distributed UTXO reservation beyond the ownership and engine fencing contracts already documented.

## Decision Principles

1. **Progressive disclosure:** the common path names only the concepts it needs; advanced concerns appear as explicit builder or planner options.
2. **Safe defaults:** default choices must preserve per-item deduplication, UTXO serialization, honest outcomes, and explicit durability.
3. **One type, one runtime:** simple and advanced callers use `TxFlowStream`; there is no parallel feature-reduced abstraction.
4. **Loud uncertainty:** no convenience method may turn `RECOVERY_REQUIRED` into success or conclusive failure.
5. **Explicit resource ownership:** inheritance may reuse caller-owned resources but never transfer ownership or create hidden replacements.
6. **No silent no-ops:** an advanced option that cannot affect the selected planner/runtime should be rejected or explicitly documented.
7. **Compatibility by addition and defaults:** existing explicit configuration continues to work; intentional preview behavior breaks are narrowly documented and tested.

## Decision 1: One Progressively Disclosed `TxFlowStream`

The public API is organized conceptually into layers, but the layers are not separate implementations:

```text
Layer 0 — beginner
    FlowEngine.builder(backend)
    TxFlowStream.open(streamId, engine)
    submit(itemId, plan).awaitConfirmed()

Layer 1 — production reliability
    engine/stream stores, explicit maintenance, await/trySubmit,
    listeners, cancel, reconcile

Layer 2 — throughput
    explicit/partitioned lanes, windows, batching,
    per-window pipelining

Layer 3 — integration and extensibility
    templates, ownership, publisher sources, custom planners
```

All existing builder knobs remain available. New defaults apply only when the corresponding explicit setting is absent.

## Decision 2: Target Beginner API

The primary documentation sample becomes:

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

The stable item id is also the idempotency key for this overload. Documentation must call that out; examples use an application intent identifier such as an order id, not a fresh random id on every retry.

Callers that deliberately handle every settled outcome use:

```java
TxStreamItemResult result = stream.submit("order-0042", plan).await();
if (!result.isSuccessful()) {
    // Inspect FAILED, CANCELLED, or RECOVERY_REQUIRED explicitly.
}
```

## Decision 3: Standard Backend Engine Factory

Add an overload to `FlowEngine`:

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

The existing four-supplier overload remains the lower-level integration path for custom hosts and adapters. `txflow` already has an API dependency on `backend`, and `FlowExecutor.create(BackendService)` already establishes the standard mapping.

This overload does not choose an executor, signer registry, store, policy, or maintenance strategy.

## Decision 4: Safe Default Lane Policy

When no lane policy is configured, `TxFlowStream.Builder.build()` uses:

```java
LanePolicy.byFundingAddress()
```

The policy derives the canonical lane from the single transaction's `from` address or `from_ref`:

- items using the same funding source share one canonical FIFO;
- items using different funding sources may execute concurrently, bounded by `maxInFlight`;
- an item with no funding source fails with `TXSTREAM_LANE_UNDERIVABLE` and a teaching message;
- an item declaring both `from` and `from_ref` fails with a distinct ambiguity diagnostic;
- a supplied item lane that does not match the derived funding source remains `TXSTREAM_LANE_MISMATCH`;
- explicit `.lane(...)` and `.lanes(...)` always override the default.

The initial release does not add a generic `fallbackLane`. A fallback selected after any `TXSTREAM_LANE_UNDERIVABLE` error could mask an ambiguous or malformed plan. If later evidence requires a default for a narrowly defined case, it must use a narrow name such as `templateLane(...)` or `defaultFundingLane(...)` and must not apply to ambiguity, mismatch, scope violation, or portability failure.

Template items continue to require an explicit lane policy because their funding can live across multiple steps. A dedicated template stream normally uses `.lane(...)`; mixed streams use an explicit policy and item lane.

## Decision 5: Inherit Dispatch, Keep Scheduled Maintenance Explicit

Add a read-only accessor to `FlowEngine`:

```java
public Executor executionExecutor();
```

The executor remains caller-owned. The accessor does not permit the stream to close, replace, or transfer ownership of it.

Expose it through the package-private gateway seam:

```java
default Optional<Executor> dispatcher() {
    return Optional.empty();
}
```

Builder precedence is:

```text
explicit stream executor
    else engine gateway dispatcher
    else fail with the existing teaching error
```

An explicit stream executor always wins, preserving current behavior and allowing independent stream dispatch isolation.

Phase A does **not** infer scheduled maintenance. `FlowEngine` currently substitutes its execution executor when no maintenance executor is configured, so `instanceof ScheduledExecutorService` cannot prove that the application deliberately provisioned a maintenance scheduler. Timed windows, ownership, and periodic reconciliation continue to require an explicit stream `maintenanceExecutor`.

A later additive API may expose an optional **explicitly configured** engine maintenance executor. Only an explicitly configured `ScheduledExecutorService` may then be inherited. A plain executor is never wrapped by a hidden scheduled pool.

## Decision 6: Started Factories with Failure Cleanup

Keep `build()` unstarted. Add:

```java
static TxFlowStream open(String streamId, FlowEngine engine) {
    return builder(streamId, engine).start();
}
```

and:

```java
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
```

The cleanup behavior is normative. `EngineTxFlowStream.start()` marks an instance started before operations such as bootstrap, ownership acquisition, durable re-attachment, and source startup. If any operation throws, a factory that loses the local stream reference would leak a partially started instance. The factory aborts rather than gracefully closes so cleanup cannot wait indefinitely for a startup that did not complete.

The original startup failure remains primary; cleanup failures are suppressed. Existing direct `stream.start()` behavior remains idempotent and unchanged for callers that intentionally use `build()`.

## Decision 7: Common Submission Overload and Full Advanced Model

Add only the demonstrated common overload in the initial release:

```java
default TxStreamReceipt submit(String itemId, TxPlan plan) {
    return submit(TxWorkItem.fromTxPlan(itemId, plan));
}

default EmitResult trySubmit(String itemId, TxPlan plan) {
    return trySubmit(TxWorkItem.fromTxPlan(itemId, plan));
}
```

`TxWorkItem` remains the full model for:

- a distinct idempotency key;
- metadata;
- explicit lanes;
- flow steps;
- templates and bindings;
- secure binding references;
- other advanced payload configuration.

The initial release does not add `TxWorkItem.of(...)`, because `fromTxPlan(...)` is already precise, or `submit(String, FlowStep)`, because advanced callers already have `TxWorkItem.fromFlowStep(...)`. Either overload may be added later based on usage without changing the core decision.

No `submitPayment(...)` API is added. Transaction construction, signer references, funding source, change behavior, and other QuickTx semantics remain in `TxPlan`.

## Decision 8: Honest Submission Rejection

Eager validation that creates no registered or buffered work is rejection, not acceptance.

| Outcome | `submit(...)` | `trySubmit(...)` | Receipt? | Counts accepted? |
|---------|---------------|------------------|----------|------------------|
| New valid item | return receipt | `OK` | Yes | Yes |
| Same-content live/stored redelivery | return existing/attached receipt | `DUPLICATE_ATTACHED` | Yes | No new acceptance |
| Different-content reuse | throw typed conflict | `CONFLICT` | No | No |
| Eager content/configuration validation failure | throw typed cause | `REJECTED` | No | No |
| Authoritative registration failure | throw typed cause | `REJECTED` | No | No |
| Ownership standby | throw not-active/paused stream error | `PAUSED` | No | No |
| Buffer full | block until capacity or interruption | `FULL` | No | No |
| Not started, draining, closed, aborted, or unhealthy | throw typed stream error | `CLOSED` | No | No |

A rejected item:

- has no receipt;
- does not increment accepted or failed item counters;
- does not enter retention;
- does not fire `onItemAccepted`;
- may be corrected and retried with the same item id.

Null items and other direct Java programming errors may still throw `NullPointerException` or `IllegalArgumentException` as documented; `trySubmit` is non-throwing for submission outcomes, not for impossible Java calls.

This is an intentional preview behavior change. It replaces today's mixed behavior where some failures are `REJECTED` while portability, lane-content, and template validation may be returned as `OK` with an already-failed receipt.

## Decision 9: Receipt Waiting and Typed Outcomes

Add to `TxStreamReceipt`:

```java
public TxStreamItemResult await();

public TxStreamItemResult await(Duration timeout);

public TxStreamItemResult awaitConfirmed();

public TxStreamItemResult awaitConfirmed(Duration timeout);
```

`await()` waits until the existing item promise settles with one of:

- `CONFIRMED`;
- `FAILED`;
- `CANCELLED`;
- `RECOVERY_REQUIRED`.

It returns that settled result without interpreting it. This is the advanced branching API.

`awaitConfirmed()` waits for settlement and then requires the **latest live projection** to be `CONFIRMED`:

1. wait on the existing non-cancelling item promise;
2. read `current()` once after the wait;
3. return the latest result when it is `CONFIRMED`;
4. throw `TxStreamFailedException` for `FAILED`;
5. throw `TxStreamCancelledException` for `CANCELLED`;
6. throw `TxStreamUncertainException` for `RECOVERY_REQUIRED`.

All three exceptions extend `TxStreamException` and carry the complete `TxStreamItemResult`. `TxStreamUncertainException` therefore exposes the retained transaction hash through its result and must include a message warning against blind resubmission.

The post-wait `current()` read is required because the promise is a point-in-time settlement. A promise completed with `RECOVERY_REQUIRED` is not completed again after reconciliation; however, its live projection may already have advanced to `CONFIRMED` before `awaitConfirmed()` classifies it. `awaitConfirmed()` does not perform hidden network reconciliation and does not continue waiting for a future repair. If the latest snapshot remains uncertain, it throws immediately.

Error-code rule for the convenience exceptions:

- a failed result preserves an underlying `TxStreamException` code when one exists, otherwise uses a documented generic item-failed code;
- cancellation uses `TXSTREAM_ITEM_CANCELLED`;
- uncertainty uses a new documented core code such as `TXSTREAM_RECOVERY_REQUIRED` (final spelling is an open review item).

Timed waits:

- reject null, zero, and negative durations;
- use the duration as a total wait bound;
- throw `TxStreamTimeoutException` on expiry;
- restore the thread interruption flag and throw `TxStreamException` with `TXSTREAM_INTERRUPTED` when interrupted;
- never cancel or complete the underlying item promise.

`completion()` remains for asynchronous/reactive composition.

## Decision 10: Advanced Throughput Is Planner-Scoped

ADR 0004 promises three Cardano-native throughput levers:

1. parallel work over disjoint lane funding scopes;
2. pipelined transactions within a lane;
3. many intents merged into one transaction.

Lanes and batching are already public. Built-in intra-lane pipelining is made reachable through the planner that creates a multi-step flow, not through a misleading stream-global setting.

Preferred API direction:

```java
TxStreamPlanner.perWindow(ChainingMode.PIPELINED)
```

or a small immutable `PerWindowOptions` if review identifies additional planner-owned settings. The ordinary `perWindow()` factory remains sequential for compatibility and safety.

A global `.chaining(...)` builder option is rejected because it would be:

- meaningless for one-step `perItem()` executions;
- meaningless for each one-step merged batching execution;
- potentially ignored by custom planners;
- incorrect as an override of registered template settings.

The initial implementation supports `SEQUENTIAL` and `PIPELINED` for the per-window built-in. `ChainingMode.BATCH` is rejected unless the implementation review proves that its build-all/submit-all behavior is safe for same-lane window steps and adds dedicated tests. Unsupported modes fail at planner construction or stream build; they do not silently downgrade.

## Decision 11: Durable Registration Matching and Shared Hydration

The durable redelivery fix requires access to the stored registration fingerprint. `TxStreamStateStore.getItem(...)` returns a projection and is insufficient for attach-versus-conflict.

The preferred store contract is an atomic operation conceptually shaped as:

```java
TxStreamRegistrationResult registerOrMatch(TxStreamItemRecord candidate);
```

with outcomes:

```java
enum Kind {
    REGISTERED,
    MATCHED,
    CONFLICT
}
```

The result carries the authoritative stored registration for `MATCHED` and `CONFLICT`. Exact naming and whether this replaces or supplements `registerItem(...)` are open for store-SPI review, but the following semantics are normative:

- registering a previously absent item is atomic;
- identical fingerprint means match/attach, not duplicate conflict;
- different fingerprint means typed conflict and never replacement;
- concurrent registrations cannot both become authoritative;
- core in-memory, durable in-memory, and RDBMS stores share the same conformance contract;
- custom durable stores receive a documented preview migration path.

A pre-read followed by the current `registerItem` is not sufficient because it leaves a lookup/register race. A compare-after-conflict fallback may be accepted as an implementation bridge only if its concurrency behavior is equivalent and contract-tested.

Stored match handling uses one shared hydration component/helper for:

- post-eviction same-content attachment;
- store-only status reads;
- store-only `RECOVERY_REQUIRED` reconciliation;
- restart re-attachment where the same reconstruction is applicable.

Hydration must preserve:

- stream id, item id, claim key, fingerprint, and lane;
- execution and step mapping;
- the stored projection and transaction hash;
- projection CAS sequence domination;
- final-state immutability and authoritative fast-forward rules;
- exactly-once listener publication per newly applied repair;
- a normally completed receipt for an already-settled stored result.

A registration that exists without a usable projection/binding because of a crash is not blindly treated as a settled attachment. The implementation must classify and recover that partial state under the existing write-ahead protocol or reject it as typed store corruption. The exact partial-registration recovery algorithm is a blocking review item for this workstream.

## Decision 12: Operability Additions and Deferred API Churn

### Core error-code catalog

Add a public `TxStreamCodes` constants class covering codes emitted by the core `txflow` module. `TxStreamException.getCode()` remains supported. Extensions own extension-specific catalogs; core does not enumerate downstream RDBMS codes.

Tests must prevent core literals from drifting away from the catalog. The design document and package documentation include a code table with meaning and caller action.

### Abort listener

Add:

```java
default void onStreamAborted(String streamId, AbortReport report) {
}
```

It fires exactly once after the report is published and before the existing exactly-once `onStreamClosed`. Its `quiescence()` stage may still be incomplete. Listener exceptions remain isolated. Reentrant abort cannot widen the existing report or duplicate notifications.

### Accessor convention

Fluent result aliases such as `status()` and `transactionHash()` may be added, but existing `getX()` methods are not deprecated in the DX release. The API still mixes records, fluent value objects, and JavaBean models; removal is deferred until a repository-wide pre-1.0 convention is accepted.

### Store package

Store types are not moved in the DX release. Java cannot typedef/re-export final records and interface method signatures compatibly. A package move would break custom stores and the RDBMS extension. If still desired before 1.0, it is one explicit migration with all implementations updated together.

### Effective configuration and lifecycle status

An immutable effective-configuration view and a derived lifecycle status are useful advanced operability follow-ups. They are additive and do not block the beginner API. They must not expose executor ownership or create a second mutable configuration surface.

## Decision 13: Optional Thread-Owning Runtime Is Deferred

An optional future `FlowRuntime` may own executors and open streams for scripts and small applications:

```java
public final class FlowRuntime implements AutoCloseable {
    public static Builder builder(BackendService backend);
    public FlowEngine engine();
    public TxFlowStream open(String streamId);
    @Override public void close();
}
```

It is not part of the initial decision implementation. Before introduction it requires explicit pool sizing, thread naming, ownership documentation, open-stream tracking, stream-close-before-pool-shutdown ordering, failure cleanup, and Java 17-compatible defaults. Production servers continue to own their executors directly.

## Compatibility and Migration

TxStream is a preview API, but compatibility remains a design objective.

### Additive or defaulted changes

- `FlowEngine.builder(BackendService)`;
- `FlowEngine.executionExecutor()`;
- `TxFlowStream.open(...)`;
- `TxFlowStream.Builder.start()`;
- `submit(String, TxPlan)` and `trySubmit(String, TxPlan)`;
- receipt `await*` methods and typed outcome exceptions;
- planner-local per-window mode/options;
- `TxStreamCodes`;
- `onStreamAborted`;
- optional fluent accessor aliases.

Existing `.lane(...).executor(...).build(); start();` code retains its explicit behavior. Explicit builder options always override defaults.

### Intentional preview behavior changes

1. Omitting a lane no longer fails at build; ordinary plan items derive one from funding.
2. Omitting a stream executor no longer fails when the engine exposes one.
3. Eager validation changes from `OK` + failed receipt to throw/`REJECTED` with no receipt or accepted counters.
4. Blocking submit to standby should gain a lifecycle-specific not-active/paused diagnostic rather than claiming the stream is closed; exact code is reviewed before 1.0.
5. The durable store registration SPI may change to support atomic match semantics; all shipped stores migrate together.

### Explicitly deferred breaking changes

- removal of JavaBean getters;
- moving store SPI FQCNs;
- renaming `TxFlowStream` to `TxStream`;
- removing existing low-level constructors, factories, or builder methods.

Release notes must include before/after examples, validation migration, executor/lane default precedence, and the unchanged ownership rules.

## Implementation Plan

Each phase is independently reviewable. Phase A establishes the beginner front door. Phase B makes validation and durability match the advertised contracts and should ship in the same preview release. Later phases may trail without weakening Phase A.

### Phase 0: Baseline and contract inventory

**Work**

- Record the current public signatures and behavior tests for explicit lane/executor configurations.
- Add or identify tests for start failures, validation failure classes, durable eviction, store-only reconciliation, and listener ordering before changing behavior.
- Inventory every core `TXSTREAM_*` code and separate extension codes.

**Exit criteria**

- Existing explicit behavior is pinned.
- Every intentional behavior change has a before-state test that will be deliberately updated.
- No implementation PR combines unrelated accessor/package churn.

### Phase A1: Standard engine facade and executor inheritance

**Primary files**

- `txflow.exec.FlowEngine`
- `stream.EngineGateway`
- `stream.FlowEngineGateway`
- test `StubEngineGateway`
- `TxFlowStream.Builder`

**Work**

- Add `FlowEngine.builder(BackendService)` with null validation.
- Add `executionExecutor()` without changing ownership.
- Add optional gateway dispatcher exposure.
- Resolve explicit stream executor before inherited engine executor.
- Leave stream scheduled maintenance validation unchanged.

**Verification**

- explicit stream executor wins;
- omitted stream executor inherits;
- missing gateway dispatcher still fails with a teaching message;
- direct, single-thread, fixed-thread, and rejecting executors preserve dispatch failure semantics;
- stream close never shuts down the inherited executor;
- no scheduled maintenance is inferred.

### Phase A2: Default lane and diagnostics

**Primary files**

- `TxFlowStream.Builder`
- `LanePolicy`
- `EngineTxFlowStream.resolveLane` / funding derivation

**Work**

- Default missing policy to `byFundingAddress()`.
- Preserve all explicit lane policies and precedence.
- Split missing-source and ambiguous-source diagnostics.
- Keep templates explicit; do not add fallback behavior.

**Verification**

- same `from` address serializes FIFO;
- different addresses dispatch concurrently up to `maxInFlight`;
- same `from_ref` serializes;
- absent funding fails with corrective guidance;
- both `from` and `from_ref` fail as ambiguous;
- mismatched item lane remains typed;
- template + default fails clearly and template + explicit lane works;
- existing lane enforcement and overlap suites remain green.

### Phase A3: Started factories and common submission

**Primary files**

- `TxFlowStream`
- `TxFlowStream.Builder`
- lifecycle tests

**Work**

- Add `open(...)` and exception-safe `Builder.start()`.
- Add plan submission and non-blocking submission overloads.
- Improve not-started diagnostics to mention `start()` and `open(...)`.

**Verification**

- normal start returns the exact built instance;
- bootstrap, ownership, re-attach, and source-start failures abort once;
- original startup failure is rethrown;
- cleanup failure is suppressed;
- no startup failure path waits in graceful close;
- `build()` remains unstarted and current tests continue to use it;
- overload-created work has the same fingerprint and identity as `TxWorkItem.fromTxPlan`.

### Phase A4: Receipt waiting

**Primary files**

- `TxStreamReceipt`
- new typed exceptions
- `TxStreamTimeoutException` documentation/constructors as required

**Work**

- Add untimed and timed `await` variants.
- Add latest-projection classification for `awaitConfirmed`.
- Preserve completion-stage behavior.

**Verification**

- confirmed returns;
- failed, cancelled, and uncertain throw distinct exceptions carrying their results;
- uncertain result retains its transaction hash;
- a promise settled uncertain but live projection repaired confirmed returns confirmed;
- no hidden reconcile call occurs;
- timeout does not cancel work;
- zero/negative durations fail immediately;
- interruption restores the flag and returns the typed code;
- multiple waiters do not interfere.

### Phase A5: Beginner documentation and live proof

**Work**

- Rewrite TxStream getting started around the target sample.
- Update `package-info.java`, ADR 0004's flagship example, README references, and internals docs.
- Explain stable item ids and `awaitConfirmed` before presenting `await`.
- Keep durable and throughput warnings linked from the beginner page.

**Verification**

- compile every Java snippet;
- run the beginner payment against Yaci DevKit;
- keep the sample at or below twenty substantive Java lines excluding imports/setup comments;
- ensure the sample names no lane or stream executor.

### Phase B1: Honest validation rejection

**Primary files**

- `EngineTxFlowStream.accept`, `submit`, and `trySubmit`
- `EmitResult`
- source adapters and stats/listener tests

**Work**

- Route every eager no-work validation failure through throw/`REJECTED`.
- Remove live retained validation-failure states.
- Align counters, retention, callbacks, and retry-with-same-id behavior.
- Review transient lane-resolution and authoritative registration failure classification.

**Verification**

- portability, lane-content, unknown-template, invalid-id, idempotency-reuse, and registration failures follow the outcome table;
- rejected work produces no receipt/counter/retention/accepted callback;
- corrected same-id retry succeeds;
- publisher/source adapters do not treat rejection as capacity backpressure;
- same-content accepted redelivery still attaches.

### Phase B2: Durable registration outcome contract

**Primary files**

- `TxStreamStateStore` and its contract fixture
- in-memory store implementations
- `RdbmsTxStreamStateStore` and schema/query code if required
- `EngineTxFlowStream.accept`

**Work**

- Finalize the atomic register/match/conflict API.
- Implement it in all stores.
- Use the stored fingerprint to distinguish attach and conflict after eviction.
- Define and implement partial-registration recovery or typed corruption behavior.

**Verification**

- same-content after eviction attaches;
- different-content after eviction conflicts;
- concurrent same/different submissions have one authoritative result;
- restart and RDBMS cases match in-memory semantics;
- registration/binding/projection write failures remain fail-closed;
- no durable item is silently overwritten.

### Phase B3: Shared hydration and store-only reconcile

**Primary files**

- `EngineTxFlowStream` hydration/reattach/read paths
- state-store sequence APIs
- recovery and retention tests

**Work**

- Extract one package-private reconstruction path shared by re-attach, stored attach, and store-only reconciliation.
- Restore mapping, cursor/sequence, current projection, receipt settlement, and claim indexes as appropriate.
- Apply repairs through the existing authoritative projection path.

**Verification**

- store-only `RECOVERY_REQUIRED` repairs to every allowed final state;
- hash preservation and final-state immutability hold;
- repaired projection dominates the stored CAS sequence;
- listeners observe one advancing repair;
- settled hydration does not inflate cumulative acceptance counters;
- repeated get/reconcile calls are idempotent.

### Phase C1: Planner-local pipelining

**Primary files**

- `TxStreamPlanner`
- `BuiltInPlanners`
- planner option type if selected

**Work**

- Add the reviewed per-window mode/options factory.
- Apply the mode only to the generated multi-step flow.
- Reject unsupported or ineffective configuration.

**Verification**

- default per-window remains sequential;
- pipelined per-window emits the correct `TxFlow` settings;
- per-item, batching, custom planners, and templates are not silently overridden;
- pipelined failure/uncertainty preserves item projection truth;
- live DevKit coverage demonstrates the intended throughput behavior.

### Phase C2: Operability

**Work**

- Add core error-code constants and documentation.
- Add `onStreamAborted` with exact ordering.
- Add optional effective-configuration and lifecycle snapshots if accepted during review.

**Verification**

- core code catalog covers emitted core literals;
- extension codes remain extension-owned;
- abort callback ordering, reentrancy, and listener isolation are pinned;
- snapshots are immutable and do not expose executor ownership.

### Pre-1.0 API-shape review

Decide, independently of the functional release:

- fluent versus JavaBean accessor convention;
- whether store SPI types move packages;
- whether `byFundingAddress()` gains a clearer `byFundingSource()` alias/name;
- whether `TxFlowStream` retains its name permanently;
- whether `FlowRuntime` is justified by usage.

## Verification Matrix

| Area | Unit/contract tests | Integration tests | Required evidence |
|------|---------------------|-------------------|-------------------|
| Beginner defaults | builder, lane, executor, identity | Yaci DevKit payment | sample compiles and confirms |
| Explicit compatibility | existing stream suites | existing stream ITs | explicit lane/executor behavior unchanged |
| Startup cleanup | injected failure at each start stage | durable/bootstrap where practical | abort once, original failure retained |
| Receipt outcomes | all statuses, timeouts, interruption, repair race | uncertain/reconcile path | no uncertain outcome appears successful |
| Validation | every eager failure class, stats, listener, retry | source adapter tests | no rejected item reported accepted |
| Durable redelivery | shared store contract, eviction, concurrency | H2 + PostgreSQL | same attaches, different conflicts |
| Store-only repair | hydration, CAS, listener, idempotence | restart + RDBMS | projection converges to engine truth |
| Pipelining | generated settings and projections | DevKit multi-step flow | advanced lever is real and honest |
| Documentation | snippet compilation/link check | getting-started execution | docs match shipped API |

All Gradle verification runs use Java 17. No implementation phase is complete while its focused tests or the existing explicit-configuration suite regress.

## Documentation Plan

The API is not beginner-accessible until its documentation follows the same disclosure order.

1. **Getting started:** backend, signers, one executor, `open`, stable item id, `TxPlan`, `awaitConfirmed`.
2. **Outcome handling:** `awaitConfirmed` exceptions first; `await` and asynchronous `completion()` second; loud `RECOVERY_REQUIRED` warning adjacent to both.
3. **Production reliability:** durable engine + stream stores, explicit maintenance, redelivery, reconciliation, and ownership.
4. **Throughput:** derived lanes, explicit/partitioned lanes, windows, per-window pipelining, batching, and deduplication trade-offs.
5. **Advanced integration:** templates, sources, listeners, cancellation, custom planners, and stable identity obligations.
6. **Reference:** error codes, statuses, results/reports/outcomes, effective defaults, and lifecycle behavior.

Funds-critical warnings remain prominent:

- do not blindly resubmit an uncertain transaction;
- batching/per-window redelivery does not imply true per-item dedup;
- partitioned configuration is stability-critical across restarts;
- ownership/fencing changes which instance may accept new work;
- graceful `close()` drains without a default timeout, while `close(Duration)` bounds the wait.

## Alternatives Considered

### Keep the current explicit front door

Rejected. It exposes correct advanced concepts but forces them on every beginner, contradicting ADR 0004 and increasing lifecycle and outcome-handling errors.

### Add `SimpleTxStream`

Rejected. It creates two public APIs and eventually two documentation, testing, and feature-parity problems. Defaults and factories on the real type provide the same simplicity without a second abstraction.

### Auto-start on `build()`

Rejected. Tests and advanced source-before-start wiring require an unstarted instance. `open()` and `Builder.start()` make the started path explicit while retaining `build()`.

### Create hidden executors

Rejected. It breaks caller ownership, server resource control, deterministic tests, and Java 21 executor choice. A future explicit `FlowRuntime` is the only acceptable thread-owning boundary.

### Inherit any scheduled executor as maintenance

Rejected for Phase A. The engine's maintenance field may only be its execution fallback; type inspection cannot prove intentional isolation from blocking tasks.

### Default to a single unnamed lane

Rejected. No safe funding scope exists until an item is inspected. Funding-derived lanes correctly serialize one wallet and parallelize distinct wallets.

### Add a generic fallback lane

Rejected initially. It can mask ambiguous funding declarations and is unsafe for multi-funding templates. Explicit `.lane(...)` is honest.

### Default to batching or per-window planning

Rejected. Their flow-level deduplication and re-windowing consequences are inappropriate as invisible defaults. `perItem()` remains the safe default.

### Return failed receipts for eager rejection

Rejected. It makes `OK`, counters, callbacks, and “work exists” ambiguous. `trySubmit` already provides a non-throwing outcome surface.

### Make `awaitConfirmed()` automatically reconcile

Rejected. It would hide network I/O, recovery authority, and potentially unbounded waiting inside a convenience accessor. It classifies the latest available projection and makes uncertainty loud.

### Put chaining on the stream builder

Rejected. The setting is ineffective or misleading for most planners and could appear to override custom/template flows. It belongs to the built-in planner that produces a multi-step flow.

### Move store types now

Deferred. Java compatibility bridges cannot faithfully re-export final records and SPI signatures. Functional DX has higher value and lower regression risk.

## Consequences

### Positive

- A beginner can submit and confirm a payment without learning lanes, stream executors, work-item builders, or completion-stage conversion.
- The default still follows Cardano's UTXO concurrency model rather than hiding it behind a global lock.
- Advanced users retain the full engine, planner, lane, store, source, ownership, and recovery surface.
- Startup and uncertain-outcome failure modes become harder to misuse.
- Validation semantics become consistent between blocking and non-blocking submission.
- Durable redelivery and store-only repair gain explicit, testable contracts.
- Existing explicit configurations remain valid.

### Negative and costs

- `FlowEngine` exposes its execution executor as a read-only public detail.
- Default lane behavior shifts a missing configuration error from build time to typed item validation for plans that cannot derive funding.
- Honest validation rejection intentionally changes current preview counters, callbacks, retry, and receipt behavior.
- Correct durable matching likely requires a store SPI migration across core, RDBMS, and custom implementations.
- Three typed waiting exceptions add public types to an already large stream package.
- The implementation requires careful startup-failure and hydration tests, not only convenience delegates.

### Accepted residual risks

- `close()` without a duration may wait indefinitely for accepted work; changing that default would silently cancel or abandon funds-critical work. Documentation presents `close(Duration)` for bounded shutdown.
- A caller can still choose `await()` and mishandle `RECOVERY_REQUIRED`; that method is explicitly the all-outcomes advanced surface. Beginner docs use `awaitConfirmed()`.
- Custom planners remain responsible for deterministic full-request content and correct item-to-step mapping.
- A caller may shut down an inherited executor too early because the application owns it; the stream cannot prevent that.

## Open Questions

The following questions are explicit targets for the next review rounds:

1. **Store registration API shape:** Does `registerOrMatch(...)` replace `registerItem(...)`, supplement it, or become a result-returning version of the existing method? What is the least disruptive shape that remains atomic and conformance-testable?
2. **Partial registration recovery:** How does redelivery recover a durable registration written before its first projection or binding, and when is that state typed corruption rather than resumable work?
3. **Uncertain exception code:** Should the wrapper use `TXSTREAM_RECOVERY_REQUIRED`, `TXSTREAM_OUTCOME_UNCERTAIN`, or another catalog name? Failed-result code preservation must also be finalized.
4. **Per-window `BATCH`:** Is engine `ChainingMode.BATCH` safe and useful for same-lane stream steps, or should the built-in accept only `SEQUENTIAL` and `PIPELINED`?
5. **Blocking standby submit:** Should it use a new `TXSTREAM_PAUSED`, `TXSTREAM_NOT_ACTIVE`, or a specialized exception while `trySubmit` continues returning `PAUSED`?
6. **Accessor convention:** Add aliases only, standardize on fluent accessors before 1.0, or retain mixed conventions based on model type?
7. **Lane policy name:** Because `byFundingAddress()` also supports `from_ref`, should a `byFundingSource()` alias be introduced before 1.0?
8. **Effective configuration/lifecycle snapshots:** Include them in the first DX release or defer until operational usage demonstrates the required fields?
9. **FlowRuntime:** Is executor-free scripting common enough to justify a thread-owning runtime, and what conservative Java 17 defaults should it use?

## Review Checklist

Reviewers should explicitly confirm or challenge:

- whether `awaitConfirmed()` is the correct beginner default;
- whether reading `current()` after promise settlement is sufficient and race-safe;
- whether cancellation deserves its own exception type;
- whether derived funding is safe for every supported single-transaction `TxPlan` form;
- whether explicit maintenance remains necessary for all timed stream features;
- whether abort is the correct cleanup operation for every startup failure stage;
- whether eager validation should create no retained receipt/state;
- whether planner-local chaining is the right advanced API location;
- whether the durable store outcome can handle cross-instance races and partial registrations;
- whether any existing explicit API becomes semantically different despite being source-compatible;
- whether the implementation phases can be merged independently without exposing an unsafe intermediate release.

## Acceptance Criteria

This ADR is ready to move from `Proposed` to `Accepted` when:

- all blocking open questions have recorded resolutions;
- the beginner and advanced API examples are judged coherent together;
- executor and lane precedence are unambiguous;
- startup cleanup and receipt uncertainty semantics are accepted;
- the durable registration/hydration contract is precise enough for the RDBMS conformance suite;
- intentional preview breaks and deferred pre-1.0 work are explicitly agreed.

The implementation is complete when:

- the getting-started sample is at most twenty substantive Java lines, names no lane or stream executor, compiles, and confirms on Yaci DevKit;
- existing explicit `.lane(...).executor(...).build(); start();` tests remain green;
- `open()` and `Builder.start()` abort every partially started failure path and retain the original failure;
- `awaitConfirmed()` never reports `RECOVERY_REQUIRED` as success and carries the latest result/hash when uncertain;
- timed waits preserve interruption and do not cancel work;
- invalid work is never `OK`, counted accepted/failed, retained, or announced through `onItemAccepted`;
- same-content durable redelivery after eviction attaches and different-content redelivery conflicts in memory, H2, and PostgreSQL;
- store-only recovery-required rows reconcile through the normal authoritative projection path;
- planner-local pipelining is demonstrably effective and does not affect incompatible planners;
- the core error-code catalog and abort listener contracts are tested and documented;
- no new executor, scheduler, timer, status machine, or parallel stream runtime is introduced inside `EngineTxFlowStream`.

This preserves TxStream's correctness-first core while making its common path approachable and its advanced power explicit rather than mandatory.
