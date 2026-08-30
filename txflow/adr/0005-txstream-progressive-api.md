# ADR 0005: Progressive TxStream API for Beginner Accessibility and Advanced Control

**Status**: Implemented (accepted after final implementation review)

**ADR Document Version**: 1.4.0

**Date**: 2026-08-23

**Last Updated**: 2026-08-27

**Review State**: Final acceptance review incorporated; implementation and documentation complete

**Target Release**: Next TxStream preview release; exact version to be decided during review

**Modules**: `txflow`, user documentation

**Related ADRs**: [ADR 0002: Portable TxFlow Contract, Compilation, Execution, and Recovery](0002-portable-txflow-contract-and-runtime.md), [ADR 0003: Relational Durable Store Extension for TxFlow](0003-relational-durable-store-extension.md), [ADR 0004: TxFlowStream v2](0004-txstream-on-flow-engine.md), [ADR 0006: Durable TxStream Registration and Hydration](0006-txstream-durable-registration-and-hydration.md)

**Source Proposal**: [TxStream API DX — Refactoring Proposal](../docs/in-progress/TXSTREAM_API_DX.md)

**Supersedes**: None. This ADR refines the public front door and selected correctness contracts of ADR 0004; it does not replace its runtime architecture.

## ADR Version History

The ADR document version is independent of the library release version.

| ADR version | Date | Author | Review state | Summary |
|-------------|------|--------|--------------|---------|
| 1.0.0 | 2026-08-23 | Bloxbean / CCL maintainers with Codex review | Initial review draft | Defines a progressively disclosed TxStream API, safe defaults, exception-safe startup, typed receipt waiting, honest validation rejection, advanced planner controls, compatibility boundaries, and a phased implementation and verification plan. |
| 1.1.0 | 2026-08-23 | Bloxbean / CCL maintainers with Codex review | External review round 1 incorporated | Promotes `FlowRuntime` as the beginner resource owner, adds account/wallet registration conveniences, renames the funding policy and settled wait, adds explicit uncertainty recovery, clarifies confirmation bounds and effective defaults, strengthens rejection/listener contracts, and moves durable registration/hydration to ADR 0006. |
| 1.2.0 | 2026-08-25 | Bloxbean / CCL maintainers with Codex review | Maintainer review round 2 incorporated | Resolves all ADR 0005 blocking questions: fixes outcome and standby codes, places a narrow `FlowRuntime` in the top-level `txflow` package, limits per-window chaining to sequential/pipelined modes, names the ambiguous-funding diagnostic, and centralizes deferred API work with revisit criteria. |
| 1.2.1 | 2026-08-25 | Bloxbean / CCL maintainers with Codex review | Maintainer clarification incorporated | Defines `FlowRuntime` as an optional managed composition root that owns and exposes one ordinary `FlowEngine`, delegates all execution to existing engine/stream code, and leaves direct `FlowEngine` use as the canonical advanced and server path. |
| 1.3.0 | 2026-08-26 | Bloxbean / CCL maintainers with Codex implementation review | C1 implementation learning incorporated | Makes pipelined per-window funding explicit and portable: generated same-lane steps form a deterministic `funding_from` chain, exposing only predecessor outputs matching the requested funding address. This avoids double-spend/insufficient-funds behavior without changing `needs(...)` or exact `flow_output` semantics. |
| 1.4.0 | 2026-08-27 | Bloxbean / CCL maintainers with external implementation review reconciled by Codex | Final review incorporated; accepted and implemented | Corrects pipelined windows to expose all earlier unspent same-lane change, restores `FlowScheduler` as an internal seam, makes premature resolution a state precondition rather than item failure, removes duplicate runtime signer validation, refreshes public/internal documentation, and adds real-source, multi-UTxO DevKit, Java 17, and additive Java 21 CI evidence. |

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
| 1 | 1.0.0 -> 1.1.0 | 2026-08-23 | External API review (Claude), reconciled by maintainers/Codex | Incorporated | Accepted the simpler runtime ownership story, signer-registration convenience, explicit uncertain-outcome recipe, `awaitSettled`, `byFundingSource`, builder `open`, effective-default documentation, rejection callback, and stronger tests. Durable correctness moved to ADR 0006. Signer inference and a second `finalCompletion` promise were not adopted because they blur authorization and settlement semantics; explicit `awaitResolution` addresses the recovery use case. |
| 2 | 1.1.0 -> 1.2.0 | 2026-08-25 | Maintainer review | Incorporated | Selected `TXSTREAM_RECOVERY_REQUIRED`, `TXSTREAM_ITEM_FAILED`, `TXSTREAM_NOT_ACTIVE`, and `TXSTREAM_LANE_AMBIGUOUS`; fixed the `FlowRuntime` package and narrow builder scope; limited `perWindow(...)` to `SEQUENTIAL`/`PIPELINED`; deferred `BATCH`, general fluent aliases, effective snapshots, and broader runtime customization. |
| 3 | 1.2.0 -> 1.2.1 | 2026-08-25 | Maintainer clarification | Incorporated | Clarified why lifecycle ownership is not added conditionally to `FlowEngine`: `FlowRuntime` is optional composition infrastructure, owns exactly one normal engine, delegates rather than reimplements behavior, and can be bypassed completely by advanced callers. |
| 4 | 1.2.1 -> 1.3.0 | 2026-08-26 | C1 implementation review against Yaci DevKit | Incorporated | A mode-only prototype exposed an unsafe gap: later same-lane steps could not see pending change and failed after the first submission. Added a fingerprinted portable funding relationship, deterministic planner chaining, address-scope filtering, and live journal-order verification. |
| 5 | 1.3.0 -> 1.4.0 | 2026-08-27 | External final implementation review (Claude), reconciled by maintainers/Codex | Incorporated; ADR accepted and implemented | Fixed the transitive pending-change correctness gap; removed incidental scheduler API; corrected resolution-state semantics; centralized runtime signer registration; refreshed current/public docs and code-family coverage; added real source rejection, three-item multi-UTxO DevKit, and additive Java 21 CI coverage. Explicit builder examples remain intentionally supported for advanced resource ownership, and existing focused `Builder.open()` abort tests were retained instead of duplicating them through a synthetic `FlowRuntime` factory. |

Reviewers should cite decision, implementation-phase, or open-question numbers. A review round is complete when every raised finding has a recorded disposition and the next ADR version captures all accepted changes.

## Executive Summary

TxStream has a strong correctness runtime but a heavy public front door. Sending one payment currently requires users to understand the engine's four backend suppliers, signer registries and URI references, multiple executors, lane scheduling, explicit lifecycle startup, `TxWorkItem`, completion stages, confirmation policy, and the special meaning of `RECOVERY_REQUIRED`. That is too much prerequisite knowledge for a beginner and conflicts with ADR 0004's principle that a backend, wallet, and `submit(...)` should be the whole beginner story.

This ADR introduces **progressive disclosure on the existing `TxFlowStream` type**:

1. The optional `FlowRuntime` composition root owns beginner executors, signer registration, one ordinary `FlowEngine`, open-stream tracking, and close ordering; direct `FlowEngine` use remains the application-owned advanced/server path.
2. `FlowEngine.builder(BackendService)` hides the standard supplier adapters, and a stream can inherit the engine's caller-owned execution executor.
3. The default `byFundingSource()` lane policy derives a safe lane from each transaction's syntactic `from` or `from_ref` funding source.
4. `TxFlowStream.open(...)` and `Builder.open()` return an already-started stream and clean up a partially started instance if startup fails.
5. `submit(itemId, TxPlan)` covers the common submission case while `TxWorkItem` remains the full advanced model.
6. `awaitConfirmed()` is the beginner default and makes failure, cancellation, and uncertain on-chain disposition loud and typed. `awaitSettled()` remains the explicit all-outcomes API, and `awaitResolution(...)` makes recovery polling explicit.
7. Eager validation rejection is never reported as accepted work and has an explicit listener signal.
8. Throughput, durability, ownership, sources, templates, custom planning, and direct executor ownership remain reachable through the same core types.

The decision does **not** create another transaction execution runtime or status machine, create threads inside `FlowEngine` or `TxFlowStream`, auto-enable unsafe batching or durability, hide uncertain outcomes, or weaken any funds-safety invariant established by ADRs 0002–0004. Despite its name, `FlowRuntime` is only an optional, explicit resource-owning composition boundary around the existing engine and stream. It creates and exposes a normal `FlowEngine`; all planning, submission, observation, and projection behavior continues through the existing `FlowEngine` and `EngineTxFlowStream` implementation.

## Context

### Current beginner cost

The current path requires approximately fifteen concepts and normally three executors before one payment moves. The signer registry and the repeated funding/signer reference are part of that cost and must not be hidden from the accounting:

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
        .signerRegistry(new DefaultSignerRegistry()
                .addAccount("account://sender", sender))
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
| `SignerRegistry` plus URI references | Portable plans separate resource identity from runtime secrets | The separation matters, but the registry type need not appear in the first-hour API |
| Repeated `from_ref` and signer ref | Funding resolution and authorization are distinct contracts | Yes when using portable references; the repetition stays explicit to avoid unsafe signer inference |
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
- resources supplied directly to an engine or stream remain caller-owned; only resources created by `FlowRuntime` are runtime-owned;
- `EngineTxFlowStream` remains the runtime implementation.

### Adjacent durable correctness gaps

Two durable-path gaps were identified during DX review because a simpler API must not make an incomplete durability contract easier to adopt:

1. after live-map eviction, a durable store may retain an item registration and projection, but same-content redelivery conflicts instead of attaching because the live state no longer carries the stored fingerprint;
2. `getItemStatus` and `reconcile` do not repair a store-only `RECOVERY_REQUIRED` row because they require a live `ItemState`.

These are correctness workstreams with store and hydration implications, not constructor polish. They are documented and planned in ADR 0006 rather than included in this ADR's acceptance scope.

## Goals

- Make the common backend + signers + payment path understandable in one screen.
- Make the default API safe for one wallet and naturally parallel for multiple disjoint funding sources.
- Make confirmed success the beginner-default waiting contract.
- Make uncertain, failed, cancelled, rejected, attached, paused, full, and closed outcomes distinguishable.
- Preserve a single public stream type and a single runtime implementation.
- Keep all current advanced controls reachable without downcasting or switching to a different API family.
- Make executor ownership explicit: application-owned with direct engine/stream construction, runtime-owned with `FlowRuntime`.
- Preserve source compatibility for existing callers that explicitly configure lanes, executors, and lifecycle.
- Keep durable documentation visibly incomplete until ADR 0006's redelivery and hydration contracts ship.
- Provide a phased implementation plan with focused regression and integration gates.

## Non-Goals

- Rewriting or splitting `EngineTxFlowStream` as part of the API release.
- Merging `FlowEngine` and `TxFlowStream` into one builder or lifecycle.
- Creating a second `SimpleTxStream` facade/runtime.
- Creating executors, schedulers, or timers inside the engine or stream. The explicit `FlowRuntime` resource owner is the only convenience boundary that may create them.
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
3. **One stream type, one execution implementation:** simple and advanced callers use `TxFlowStream`, and all work executes through the existing `FlowEngine`/`EngineTxFlowStream` implementation. `FlowRuntime` is optional lifecycle composition, not a parallel execution implementation or feature-reduced API.
4. **Loud uncertainty:** no convenience method may turn `RECOVERY_REQUIRED` into success or conclusive failure.
5. **Explicit resource ownership:** inheritance may reuse caller-owned resources but never transfer ownership. `FlowRuntime` may own resources only because its type, builder, and `AutoCloseable` contract make that ownership explicit.
6. **No silent no-ops:** an advanced option that cannot affect the selected planner/runtime should be rejected or explicitly documented.
7. **Compatibility by addition and defaults:** existing explicit configuration continues to work; intentional preview behavior breaks are narrowly documented and tested.

## Decision 1: One Progressively Disclosed `TxFlowStream`

The public API is organized conceptually into layers, but the layers are not separate implementations:

```text
Layer 0 — beginner
    FlowRuntime.builder(backend).account(ref, account).build()
    runtime.open(streamId)
    submit(itemId, plan).awaitConfirmed()

Layer 1 — production reliability
    direct FlowEngine ownership, engine/stream stores, explicit maintenance,
    awaitSettled/trySubmit, listeners, cancel, reconcile

Layer 2 — throughput
    explicit/partitioned lanes, windows, batching,
    per-window pipelining

Layer 3 — integration and extensibility
    templates, ownership, publisher sources, custom planners
```

All existing builder knobs remain available. New defaults apply only when the corresponding explicit setting is absent.

## Decision 2: Target Beginner API

The primary documentation sample becomes the success path below, followed immediately by the three typed outcome catches shown later in this decision:

```java
try (FlowRuntime runtime = FlowRuntime.builder(backend)
        .account("account://sender", sender)
        .build();
     TxFlowStream stream = runtime.open("payouts")) {
    TxPlan plan = TxPlan.from(new Tx()
                    .payToAddress(receiver, Amount.ada(2))
                    .fromRef("account://sender"))
            .withSigner("account://sender");

    TxStreamItemResult result = stream.submit("order-0042", plan)
            .awaitConfirmed(Duration.ofMinutes(5));
}
```

The stable item id is also the idempotency key for this overload. Documentation must call that out and include an anti-pattern showing why `"order-" + UUID.randomUUID()` on every retry defeats idempotency. Examples use an application intent identifier such as an order id.

Portable resource references exist so a plan can name funding and authorization without embedding secret runtime objects. `fromRef(...)` selects a funding resource; `withSigner(...)` declares authorization. The repeated reference is intentional. This ADR does **not** infer a signer from a sole funding reference: scripts, policies, multisignature plans, and distinct fee/funding/signing arrangements make that inference semantically unsafe. The beginner convenience removes the `SignerRegistry` type, not the authorization declaration.

`FlowRuntime.Builder.account(ref, account)` and `wallet(ref, wallet)` populate an internal `DefaultSignerRegistry`. The equivalent methods are also added to `FlowEngine.Builder` for applications that own their executors. They require the corresponding URI scheme, reject duplicates, and fail at `build()` if mixed with an explicitly supplied `signerRegistry`; call order must not change that result. Advanced callers continue to use `signerRegistry(...)` for custom bindings.

Callers that deliberately handle every settled outcome use:

```java
TxStreamItemResult result = stream.submit("order-0042", plan).awaitSettled();
if (!result.isSuccessful()) {
    // Inspect FAILED, CANCELLED, or RECOVERY_REQUIRED explicitly.
}
```

The canonical outcome recipe always catches uncertainty before any catch-all and shows the explicit recovery path:

```java
try {
    return stream.submit("order-0042", plan)
            .awaitConfirmed(Duration.ofMinutes(5));
} catch (TxStreamUncertainException uncertain) {
    // DO NOT RESUBMIT: reconcile the known item/transaction until resolved.
    return stream.awaitResolution(uncertain.itemId(),
            Duration.ofMinutes(5), Duration.ofSeconds(5));
} catch (TxStreamCancelledException cancelled) {
    throw cancelled;
} catch (TxStreamFailedException failed) {
    throw failed;
}
```

The getting-started page counts the runtime and account-registration lines in its line/concept budget and explains the reference model before presenting advanced registries.

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
LanePolicy.byFundingSource()
```

The policy derives the canonical lane from the single transaction's `from` address or `from_ref`:

- items using the same funding source share one canonical FIFO;
- items using different funding sources may execute concurrently, bounded by `maxInFlight`;
- an item with no funding source fails with `TXSTREAM_LANE_UNDERIVABLE` and a teaching message;
- an item declaring both `from` and `from_ref` fails with `TXSTREAM_LANE_AMBIGUOUS`;
- a supplied item lane that does not match the derived funding source remains `TXSTREAM_LANE_MISMATCH`;
- explicit `.lane(...)` and `.lanes(...)` always override the default.

The derived identity is intentionally **syntactic**, not registry-resolved: an address produces an `addr:` lane and a reference produces a `ref:` lane. Consequently, `from(address)` and `fromRef("account://sender")` may resolve to the same wallet while occupying different stream lanes and contending later in the engine. Documentation must tell applications not to mix those forms within one stream for the same funding resource. Engine spending-resource coordination remains the final safety boundary, but mixed identities reduce throughput and make ordering less obvious.

`byFundingAddress()` remains as a deprecated forwarding alias for one preview release. The clearer `byFundingSource()` name is used in all new documentation and becomes the default now, before the old name gains more adoption.

The initial release does not add a generic `fallbackLane`. A fallback selected after any `TXSTREAM_LANE_UNDERIVABLE` error could mask an ambiguous or malformed plan. If later evidence requires a default for a narrowly defined case, it must use a narrow name such as `templateLane(...)` or `defaultFundingLane(...)` and must not apply to ambiguity, mismatch, scope violation, or portability failure.

Template items continue to require an explicit lane policy because their funding can live across multiple steps. A dedicated template stream normally uses `.lane(...)`; mixed streams use an explicit policy and item lane.

The core derivation and the underivable/ambiguous branches already exist in `LanePolicy` and `EngineTxFlowStream`; this phase is a localized factory/default rename plus splitting the two branches into distinct public diagnostics, not a new lane-resolution algorithm.

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

Dispatch has a normative non-blocking invariant: a stream dispatch task may invoke or attach to engine execution but must never block its executor thread waiting for engine completion. Therefore an engine and stream sharing the same direct or single-thread executor cannot deadlock merely because the pool is shared. A dedicated one-thread shared-pool regression test pins this behavior.

Phase A does **not** infer scheduled maintenance. `FlowEngine` currently substitutes its execution executor when no maintenance executor is configured, so `instanceof ScheduledExecutorService` cannot prove that the application deliberately provisioned a maintenance scheduler. Timed windows, ownership, and periodic reconciliation continue to require an explicit stream `maintenanceExecutor`.

A later additive API may expose an optional **explicitly configured** engine maintenance executor. Only an explicitly configured `ScheduledExecutorService` may then be inherited. A plain executor is never wrapped by a hidden scheduled pool.

## Decision 6: Started Factories with Failure Cleanup

Keep `build()` unstarted. Add:

```java
static TxFlowStream open(String streamId, FlowEngine engine) {
    return builder(streamId, engine).open();
}
```

and:

```java
public TxFlowStream open() {
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

The original startup failure remains primary; cleanup failures are suppressed. `Builder.open()` is deliberately named differently from the instance lifecycle method so readers can distinguish “build and start with cleanup” from `TxFlowStream.start()`. Existing direct `stream.start()` behavior remains idempotent and unchanged for callers that intentionally use `build()`.

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
| Ownership standby | throw `TXSTREAM_NOT_ACTIVE` | `PAUSED` | No | No |
| Buffer full | block until capacity or interruption | `FULL` | No | No |
| Not started, draining, closed, aborted, or unhealthy | throw typed stream error | `CLOSED` | No | No |

A rejected item:

- has no receipt;
- does not increment accepted or failed item counters;
- does not enter retention;
- does not fire `onItemAccepted`;
- fires `onItemRejected(itemId, cause)` exactly once for both blocking and non-blocking submission, after the rejection outcome is determined;
- may be corrected and retried with the same item id.

Add the source-adapter-visible listener hook:

```java
default void onItemRejected(String itemId, TxStreamException cause) {
}
```

Listener exceptions remain isolated and cannot convert rejection into acceptance or another outcome. The callback does not imply that an item record exists.

Null items and other direct Java programming errors may still throw `NullPointerException` or `IllegalArgumentException` as documented; `trySubmit` is non-throwing for submission outcomes, not for impossible Java calls.

This is an intentional preview behavior change. It replaces today's mixed behavior where some failures are `REJECTED` while portability, lane-content, and template validation may be returned as `OK` with an already-failed receipt.

## Decision 9: Receipt Waiting and Typed Outcomes

Add to `TxStreamReceipt`:

```java
public TxStreamItemResult awaitSettled();

public TxStreamItemResult awaitSettled(Duration timeout);

public TxStreamItemResult awaitConfirmed();

public TxStreamItemResult awaitConfirmed(Duration timeout);
```

`awaitSettled()` waits until the existing item promise settles with one of:

- `CONFIRMED`;
- `FAILED`;
- `CANCELLED`;
- `RECOVERY_REQUIRED`.

It returns that settled result without interpreting it. This is the advanced branching API.

The shorter proposed name `await()` is not introduced. “Settled” teaches the distinction that this API depends on: the receipt promise has settled, but `RECOVERY_REQUIRED` is uncertain and `TxStreamItemResult.isTerminal()` remains false.

`awaitConfirmed()` waits for settlement and then requires the **latest live projection** to be `CONFIRMED`:

1. wait on the existing non-cancelling item promise;
2. read `current()` after the wait;
3. return the latest result when it is `CONFIRMED`;
4. throw `TxStreamFailedException` for `FAILED`;
5. throw `TxStreamCancelledException` for `CANCELLED`;
6. throw `TxStreamUncertainException` for `RECOVERY_REQUIRED`.

All three exceptions extend `TxStreamException` and carry the complete `TxStreamItemResult`. `TxStreamUncertainException` also exposes `itemId()` and the retained transaction hash through its result. Its message must begin `DO NOT RESUBMIT:` so a broad log or catch retains the funds-safety warning. `TxStreamItemResult` gains `isUncertain()` as the named predicate for `RECOVERY_REQUIRED`.

The post-wait `current()` read is required because the promise is a point-in-time settlement. A promise completed with `RECOVERY_REQUIRED` is not completed again after reconciliation; however, its live projection may already have advanced to `CONFIRMED` or another later repair before `awaitConfirmed()` classifies it. Classification is always based on `current()`, never the promise value. `awaitConfirmed()` does not perform hidden network reconciliation and does not continue waiting for a future repair. If the latest snapshot remains uncertain, it throws immediately.

Error-code rule for the convenience exceptions:

- a failed result preserves an underlying `TxStreamException` code when one exists, otherwise uses `TXSTREAM_ITEM_FAILED`;
- cancellation uses `TXSTREAM_ITEM_CANCELLED`;
- uncertainty uses `TXSTREAM_RECOVERY_REQUIRED`.

Timed waits:

- reject null, zero, and negative durations;
- use the duration as a total wait bound;
- throw `TxStreamTimeoutException` on expiry;
- restore the thread interruption flag and throw `TxStreamException` with `TXSTREAM_INTERRUPTED` when interrupted;
- never cancel or complete the underlying item promise.

Source inspection confirms two separate bounds that documentation must not conflate:

- engine confirmation polling is bounded to 60 seconds in the simple fallback path, or to the effective `ConfirmationConfig.timeout` when configured;
- a receipt's total time also includes stream buffering, same-lane predecessors, executor scheduling, and resource coordination, so the total accepted-to-settled wait has no universal engine bound.

For that reason the first-hour sample uses `awaitConfirmed(Duration.ofMinutes(5))`. Untimed `awaitConfirmed()` remains useful when an application supplies an outer request/lifecycle bound, but it is not the primary beginner snippet. The duration bounds the caller wait only; it does not alter engine confirmation policy and a timeout does not mean the transaction failed.

Add an explicitly reconciling helper to `TxFlowStream`:

```java
TxStreamItemResult awaitResolution(
        String itemId, Duration timeout, Duration pollInterval);
```

This helper is valid for a known item whose live projection is `RECOVERY_REQUIRED` or has already advanced from it. It calls `reconcile(itemId)` immediately and at the stated interval while the item remains uncertain. It returns only `CONFIRMED`, applies the same typed exceptions for `FAILED` and `CANCELLED`, and throws `TxStreamTimeoutException` carrying the latest projection if the total budget expires. It never submits, rebuilds, or replaces a transaction. It validates positive durations, restores interruption, runs on the caller thread, and creates no timer or maintenance task. Its name and documentation explicitly disclose network/store I/O.

A second `finalCompletion()` stage is not added. Without a maintenance observer it could remain incomplete indefinitely, while adding an observer implicitly would create hidden work. The explicit polling helper covers the beginner recovery recipe without weakening the existing point-in-time promise contract.

`completion()` remains for asynchronous/reactive composition.

## Decision 10: Advanced Throughput Is Planner-Scoped

ADR 0004 promises three Cardano-native throughput levers:

1. parallel work over disjoint lane funding scopes;
2. pipelined transactions within a lane;
3. many intents merged into one transaction.

Lanes and batching are already public. Built-in intra-lane pipelining is made reachable through the planner that creates a multi-step flow, not through a misleading stream-global setting.

The initial API is:

```java
TxStreamPlanner.perWindow(ChainingMode.PIPELINED)
```

`perWindow()` remains equivalent to `perWindow(ChainingMode.SEQUENTIAL)` for compatibility and safety. The overload accepts only `SEQUENTIAL` and `PIPELINED`; null and every other mode fail immediately with a teaching `IllegalArgumentException` rather than being ignored or downgraded. A `PerWindowOptions` type is not introduced until more than one planner-owned option is demonstrated.

For `PIPELINED`, setting the engine mode alone is insufficient and unsafe: after
the first transaction spends the lane's current UTxO, the next build must be
able to fund from its pending change. The planner therefore links its stable,
claim-key-sorted steps with portable `funding_from` relationships. Every later
generated step names all earlier generated steps in its lane, so an unconsumed
older change output remains visible even when an intervening step selected a
different base UTxO. The flow-aware supplier exposes only outputs matching the
address QuickTx requested and removes every base or pending output already
present in `FlowExecutionContext.getAllSpentInputs()`. This is transitive
availability, not transitive exact-consumption authority: it does not grant
exact-output lookup, make differently addressed outputs spendable, or change
the ordering-only contract of `needs(...)` or the exact-consumption contract
of a named `flow_output`. The relationships are part of canonical portable
encoding and therefore the compiled definition fingerprint.

A global `.chaining(...)` builder option is rejected because it would be:

- meaningless for one-step `perItem()` executions;
- meaningless for each one-step merged batching execution;
- potentially ignored by custom planners;
- incorrect as an override of registered template settings.

`ChainingMode.BATCH` is deferred. Its build-all/submit-all behavior must not be enabled for same-lane window steps until a later review proves it funds and submits safely and adds dedicated failure/uncertainty tests. See [Deferred Items](#deferred-items). Unsupported modes fail at planner construction; they do not silently downgrade.

## Decision 11: Durable Correctness Is Governed by ADR 0006

Atomic durable registration matching, partial-registration recovery, stored-item hydration, eviction attachment, and store-only reconciliation are correctness-critical but independent of the progressive DX front door. Their SPI and crash-consistency questions must not hold the beginner API hostage or be rushed to complete this ADR.

[ADR 0006](0006-txstream-durable-registration-and-hydration.md) owns those decisions, implementation phases, migration rules, and RDBMS conformance tests. This ADR preserves two boundary rules:

1. no API convenience in ADR 0005 may weaken, bypass, or falsely advertise durable guarantees from ADR 0004/0006;
2. durable getting-started/reference documentation remains explicitly marked incomplete or preview-limited until ADR 0006 is accepted and its required implementation phases ship.

The non-durable beginner API, honest rejection, startup cleanup, receipt semantics, and planner-scoped throughput can be accepted and implemented independently. Implementations may sequence ADR 0006 work in the same release, but acceptance of either ADR is not conditional on unresolved questions owned by the other.

## Decision 12: Operability Additions and Deferred API Churn

### Core error-code catalog

Add a public `TxStreamCodes` constants class covering codes emitted by the core `txflow` module. `TxStreamException.getCode()` remains supported. Extensions own extension-specific catalogs; core does not enumerate downstream RDBMS codes.

Tests must prevent core literals from drifting away from the catalog. A build-time source/catalog membership check covers every core `"TXSTREAM_..."` literal and rejects missing or orphaned constants; extension source trees are excluded. The design document and package documentation include a code table with meaning and caller action.

### Abort listener

Add:

```java
default void onStreamAborted(String streamId, AbortReport report) {
}
```

It fires exactly once after the report is published and before the existing exactly-once `onStreamClosed`. Its `quiescence()` stage may still be incomplete. Listener exceptions remain isolated. Reentrant abort cannot widen the existing report or duplicate notifications.

### Accessor convention

General fluent result aliases such as `status()` and `transactionHash()` are deferred. Existing `getX()` methods remain unchanged and are not deprecated in the DX release. Only the purpose-specific `isUncertain()` predicate is added because it directly prevents outcome misuse. A repository-wide accessor convention must be accepted before broader alias or removal work.

### Store package

Store types are not moved in the DX release. Java cannot typedef/re-export final records and interface method signatures compatibly. A package move would break custom stores and the RDBMS extension. If still desired before 1.0, it is one explicit migration with all implementations updated together.

### Effective defaults and lifecycle status

The first DX release documents its effective defaults even if an immutable programmatic configuration snapshot trails as an additive follow-up:

| Concern | Default | Operational meaning |
|---------|---------|---------------------|
| Planner | `perItem()` | One single-step engine flow and true per-item idempotency per accepted item |
| Lane policy | `byFundingSource()` | Syntactic `addr:` or `ref:` lanes; one in flight per funding identity |
| State store | in-memory, non-durable | Restart loses stream planning/projection state; do not claim durable recovery |
| Source | in-memory/direct submission | No external source starts automatically |
| `maxInFlight` | 16 | Up to 16 distinct ready lanes; never 16 concurrent transactions from one lane |
| `maxBufferSize` | 1,000 | Accepted buffered-item bound before backpressure |
| `maxRetainedSettledItems` | 10,000 | In-memory settled receipt/status retention bound |
| Window | none | Immediate per-item planning; no timer |
| Reconciliation | off | No periodic repair observer |
| Ownership | off | Single-instance acceptance unless explicitly configured |
| Maintenance | none on direct builder | Timed/durable features require an explicit scheduler; `FlowRuntime` supplies its owned scheduler |
| Confirmation | simple polling, 2-second interval, 60-second execution timeout unless configured | Receipt wait timeout remains a separate caller budget |

An immutable effective-configuration view and a derived lifecycle status are deferred. The documentation table is required in the first release and is the normative defaults reference until usage demonstrates the exact fields a programmatic snapshot needs. A future snapshot must not expose executor ownership or create a second mutable configuration surface.

## Decision 13: Optional `FlowRuntime` Owns the First-Hour Lifecycle

`FlowRuntime` is an optional managed composition root included in the first DX release for scripts, tutorials, CLI tools, and small applications. It is not required to create or use a `FlowEngine` or `TxFlowStream`:

```java
package com.bloxbean.cardano.client.txflow;

public final class FlowRuntime implements AutoCloseable {
    public static Builder builder(BackendService backend);
    public FlowEngine engine();
    public TxFlowStream open(String streamId);
    @Override public void close();
}
```

The relationship is deliberately one-way and contains no duplicated transaction behavior:

```text
FlowRuntime (optional lifecycle and resource owner)
    owns exactly one ordinary FlowEngine
    opens and tracks TxFlowStream instances
        implemented by EngineTxFlowStream
            delegates transaction execution to that FlowEngine
```

`FlowRuntime.Builder.build()` creates the same `FlowEngine` implementation that a direct caller would create, supplies runtime-owned executors and signer bindings to it, and retains that engine. `runtime.engine()` returns that exact underlying instance; it does not return a facade or a second engine. `runtime.open(streamId)` builds an ordinary `TxFlowStream` against the same engine.

`FlowRuntime` contains no planning, compilation, submission, confirmation, reconciliation, projection, retry, or status-transition logic. Those responsibilities remain in `FlowEngine`, `TxFlowStream`, and `EngineTxFlowStream`. Therefore adding `FlowRuntime` does not introduce a second execution runtime, behavior fork, or reduced-capability stream API.

Direct `FlowEngine` construction remains the canonical production/server and advanced API for dependency injection, shared pools, custom stores, and explicit lifecycle ownership. Such applications do not construct a `FlowRuntime` and incur no new lifecycle or abstraction requirement.

The separate type preserves a simple ownership rule:

- resources passed to a directly constructed `FlowEngine` or `TxFlowStream` are caller-owned, and engine/stream lifecycle operations do not shut those resources down;
- resources created by `FlowRuntime` are runtime-owned, and `FlowRuntime.close()` closes tracked streams and then those resources;
- `FlowEngine` never switches between managed and unmanaged ownership modes based on which builder overloads happened to be called.

Runtime defaults are normative and visible in documentation:

- on Java 21 or newer, use a virtual-thread-per-task executor through a Java 17-compatible capability adapter (reflection or a multi-release implementation); Java 17 source and binary compatibility remains intact;
- on Java 17–20, use a bounded fixed pool of `min(16, max(4, availableProcessors()))` task threads;
- use two platform scheduled-maintenance threads on every Java version so one blocking observation cannot by itself starve all maintenance;
- owned platform threads are non-daemon and named `txflow-runtime-<runtime-name>-task-N` or `...-maintenance-N`;
- `Builder.name(...)`, `taskParallelism(...)`, and `maintenanceThreads(...)` make these choices configurable with positive bounds;
- runtime-created executors are passed to its engine and streams; neither nested type gains ownership of them.

Beyond the required `builder(backend)` / `build()` lifecycle, the runtime builder's configuration surface is limited to `name(...)`, `taskParallelism(...)`, `maintenanceThreads(...)`, `account(ref, Account)`, and `wallet(ref, Wallet)` in the initial release. It has no generic engine or stream customizer and no caller-supplied executor hook. Advanced callers that need custom stores, policy, resources, executors, or stream configuration construct `FlowEngine` and `TxFlowStream` directly. Broader runtime customization is deferred until concrete first-hour use cases establish a safe, deterministic shape.

`open(streamId)` delegates to the exception-safe stream builder `open()`, supplies the runtime scheduler, and tracks only successfully started streams. It rejects opens after close begins. A close/open race either tracks a stream for closure or aborts it before returning; it cannot leak an untracked live stream.

`close()` performs this order:

1. atomically reject new opens;
2. gracefully close tracked streams in reverse open order, continuing after failures and suppressing later failures on the first;
3. shut down the maintenance executor;
4. shut down the task executor;
5. rethrow any collected close failure.

Graceful stream close remains unbounded because silently interrupting funds-critical accepted work is not a safe default. A later explicitly named bounded close policy may be added, but must report/abort unresolved work rather than masquerading as a successful close. The first-hour sample has already awaited its receipt, so normal resource closure is immediate.

## Compatibility and Migration

TxStream is a preview API, but compatibility remains a design objective.

### Additive or defaulted changes

- `FlowEngine.builder(BackendService)`;
- `FlowEngine.Builder.account(...)` and `wallet(...)`;
- `FlowEngine.executionExecutor()`;
- `FlowRuntime` and its builder;
- `TxFlowStream.open(...)`;
- `TxFlowStream.Builder.open()`;
- `submit(String, TxPlan)` and `trySubmit(String, TxPlan)`;
- receipt `awaitSettled`/`awaitConfirmed` methods, `awaitResolution`, `isUncertain`, and typed outcome exceptions;
- planner-local `perWindow(ChainingMode)` for `SEQUENTIAL` and `PIPELINED`;
- `TxStreamCodes`;
- `onStreamAborted` and `onItemRejected`;
- `TxStreamItemResult.isUncertain()` without general accessor churn.

Existing `.lane(...).executor(...).build(); start();` code retains its explicit behavior. Explicit builder options always override defaults.

### Intentional preview behavior changes

1. Omitting a lane no longer fails at build; ordinary plan items derive one from funding.
2. Omitting a stream executor no longer fails when the engine exposes one.
3. Eager validation changes from `OK` + failed receipt to throw/`REJECTED` with no receipt or accepted counters.
4. Blocking submit to standby throws `TXSTREAM_NOT_ACTIVE` rather than claiming the stream is closed; `trySubmit` continues returning `PAUSED`.
5. `byFundingSource()` becomes the preferred name and `byFundingAddress()` is deprecated for one preview release.

Any durable store SPI migration is governed and released under ADR 0006, not as an implicit consequence of this ADR.

### Explicitly deferred breaking changes

- removal of JavaBean getters;
- moving store SPI FQCNs;
- renaming `TxFlowStream` to `TxStream`;
- removing existing low-level constructors, factories, or builder methods.

Release notes must include before/after examples, validation migration, executor/lane default precedence, and the unchanged ownership rules.

## Deferred Items

These items are deliberately outside the first DX implementation. They are recorded here so they remain visible, but they do not block acceptance or implementation of this ADR. Each requires its own focused review before work begins; none may be introduced as incidental churn in an ADR 0005 implementation PR.

| Deferred item | Why deferred | Revisit when |
|---------------|--------------|--------------|
| `ChainingMode.BATCH` in `perWindow(...)` | Build-all/submit-all semantics may be unsafe or ineffective for same-lane steps and need dedicated failure/uncertainty analysis | A concrete throughput use case exists and DevKit tests prove funding, submission order, and recovery safety |
| General fluent result aliases and JavaBean getter removal | A local TxStream convention would deepen repository-wide inconsistency and getter removal is breaking | A repository-wide pre-1.0 accessor convention is accepted |
| Programmatic effective-configuration and lifecycle snapshots | The required field set is not yet demonstrated; the documentation table already exposes defaults | Operational users identify concrete inspection/telemetry requirements |
| Generic `FlowRuntime` engine/stream customizers or caller-supplied executors | They blur runtime ownership, configuration order, and the boundary with direct `FlowEngine` use | Repeated first-hour use cases cannot be served by the narrow builder and ownership semantics can remain deterministic |
| Store SPI package move | Java cannot compatibly re-export final records and SPI signatures | A coordinated pre-1.0 migration updates core, extensions, custom-store guidance, and persisted compatibility tests |
| Rename `TxFlowStream` to `TxStream` | High source churn with no demonstrated usability gain | Usage evidence shows the existing type name materially harms discovery or comprehension |

Deferred items are tracked independently from ADR 0006. Durable registration, hydration, and partial-write recovery are not “later DX polish”; they are the separate correctness workstream governed by ADR 0006.

## Implementation Plan

Each phase is independently reviewable. Phase A establishes the beginner front door. Phase B makes validation match the advertised acceptance contract and is a publication dependency for the beginner documentation. ADR 0006 independently plans durable registration and hydration. Later throughput/operability phases may trail without weakening the first-hour path.

### Phase 0: Baseline and contract inventory

**Work**

- Record the current public signatures and behavior tests for explicit lane/executor configurations.
- Add or identify tests for start failures, validation failure classes, uncertainty recovery, shared-pool dispatch, and listener ordering before changing behavior.
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
- Add `account(...)` and `wallet(...)` conveniences with explicit-registry conflict validation.
- Add `executionExecutor()` without changing ownership.
- Add optional gateway dispatcher exposure.
- Resolve explicit stream executor before inherited engine executor.
- Leave stream scheduled maintenance validation unchanged.

**Verification**

- explicit stream executor wins;
- omitted stream executor inherits;
- missing gateway dispatcher still fails with a teaching message;
- direct, single-thread, fixed-thread, and rejecting executors preserve dispatch failure semantics;
- an engine and stream sharing one single-thread executor cannot deadlock because stream dispatch never waits for engine completion;
- stream close never shuts down the inherited executor;
- no scheduled maintenance is inferred.

### Phase A2: Default lane and diagnostics

**Primary files**

- `TxFlowStream.Builder`
- `LanePolicy`
- `EngineTxFlowStream.resolveLane` / funding derivation

**Work**

- Add `byFundingSource()`, deprecate `byFundingAddress()` as a forwarding alias, and default a missing policy to the new name.
- Preserve all explicit lane policies and precedence.
- Preserve `TXSTREAM_LANE_UNDERIVABLE` for a missing source and emit `TXSTREAM_LANE_AMBIGUOUS` for both `from` and `from_ref`.
- Keep templates explicit; do not add fallback behavior.

**Verification**

- same `from` address serializes FIFO;
- different addresses dispatch concurrently up to `maxInFlight`;
- same `from_ref` serializes;
- address/reference forms that resolve to one wallet remain distinct syntactic lanes, and their documented mix-and-match warning is tested;
- absent funding fails with corrective guidance;
- both `from` and `from_ref` fail with `TXSTREAM_LANE_AMBIGUOUS`;
- mismatched item lane remains typed;
- template + default fails clearly and template + explicit lane works;
- existing lane enforcement and overlap suites remain green.

### Phase A3: Started factories and common submission

**Primary files**

- `TxFlowStream`
- `TxFlowStream.Builder`
- lifecycle tests

**Work**

- Add static `open(...)` and exception-safe `Builder.open()`.
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

### Phase A4: Receipt waiting and explicit resolution

**Primary files**

- `TxStreamReceipt`
- new typed exceptions
- `TxStreamTimeoutException` documentation/constructors as required

**Work**

- Add untimed and timed `awaitSettled` variants.
- Add latest-projection classification for `awaitConfirmed`.
- Add `isUncertain()` and caller-thread `awaitResolution(...)` reconciliation.
- Preserve completion-stage behavior.

**Verification**

- confirmed returns;
- failed, cancelled, and uncertain throw distinct exceptions carrying their results;
- uncertain result retains its transaction hash;
- a promise settled uncertain but live projection repaired to any later state is classified from `current()`;
- no hidden reconcile call occurs;
- `awaitResolution` reconciles at the documented interval, never resubmits, and returns only confirmed or throws a typed outcome/timeout;
- timeout does not cancel work;
- zero/negative durations fail immediately;
- interruption restores the flag and returns the typed code;
- multiple waiters do not interfere.

### Phase A5: Explicit `FlowRuntime` ownership

**Primary files**

- new `com.bloxbean.cardano.client.txflow.FlowRuntime`
- `FlowEngine.Builder`
- runtime lifecycle and Java-version tests

**Work**

- Implement the Java 21 virtual-thread capability adapter without raising the Java 17 baseline.
- Implement the Java 17–20 bounded task pool and the shared owned scheduled-maintenance pool.
- Add deterministic thread naming, registration conveniences, exception-safe open tracking, and close ordering.
- Keep the builder narrow; do not add generic engine/stream customizers or caller-supplied executor hooks.
- Keep direct `FlowEngine` construction as the server/application-owned path.

**Verification**

- Java 17 selects the fixed pool with the documented size and names;
- Java 21 selects virtual task threads while maintenance remains scheduled platform threads;
- account/wallet registration resolves funding and signing references;
- duplicate refs and explicit-registry/convenience mixing fail independent of call order;
- failed open is aborted and never tracked;
- concurrent open/close cannot leak a live stream;
- streams close before both executors and multiple close failures are preserved;
- closing nested streams never directly shuts down runtime-owned pools.

### Phase A6: Beginner documentation and live proof

**Work**

- Rewrite TxStream getting started around the target sample.
- Update `package-info.java`, ADR 0004's flagship example, README references, and internals docs.
- Explain resource references, stable item ids, and timed `awaitConfirmed` before presenting `awaitSettled`.
- Put the three typed catches and `awaitResolution` recovery recipe adjacent to the success path.
- Add the effective-default table and random-UUID idempotency anti-pattern.
- Keep durable and throughput warnings linked from the beginner page; mark durable guidance incomplete until ADR 0006 ships.

**Verification**

- compile every Java snippet;
- run the beginner payment against Yaci DevKit;
- keep the success-path sample at or below twenty substantive Java lines excluding imports/setup comments, counting runtime/account registration;
- compile the adjacent recovery recipe as part of the same example fixture;
- ensure the sample names no lane, executor, or signer-registry type.

### Phase B1: Honest validation rejection

**Primary files**

- `EngineTxFlowStream.accept`, `submit`, and `trySubmit`
- `EmitResult`
- source adapters and stats/listener tests

**Work**

- Route every eager no-work validation failure through throw/`REJECTED`.
- Remove live retained validation-failure states.
- Align counters, retention, `onItemRejected`, and retry-with-same-id behavior.
- Review transient lane-resolution and authoritative registration failure classification.

**Verification**

- portability, lane-content, unknown-template, invalid-id, idempotency-reuse, and registration failures follow the outcome table;
- rejected work produces no receipt/counter/retention/accepted callback;
- corrected same-id retry succeeds;
- rejection fires one isolated listener callback and never `onItemAccepted`;
- publisher/source adapters do not treat rejection as capacity backpressure;
- same-content accepted redelivery still attaches.

**Publication dependency:** Phase B1 must be complete before Phase A6 getting-started documentation is published. Otherwise the canonical docs would promise throw/`REJECTED` behavior that the runtime does not yet implement.

### Phase C1: Planner-local pipelining

**Primary files**

- `TxStreamPlanner`
- `BuiltInPlanners`

**Work**

- Add `perWindow(ChainingMode)` accepting only `SEQUENTIAL` and `PIPELINED`.
- Keep `perWindow()` as the sequential compatibility factory.
- Apply the mode only to the generated multi-step flow.
- In pipelined mode, give each deterministic step portable `funding_from`
  relationships to all earlier same-lane generated steps; filter already-spent
  base and pending outputs without broadening exact-output access.
- Reject null, `BATCH`, and other unsupported modes immediately without downgrading.

**Verification**

- default per-window remains sequential;
- explicit sequential is equivalent to the default factory;
- pipelined per-window emits the correct `TxFlow` settings;
- per-item, batching, custom planners, and templates are not silently overridden;
- pipelined failure/uncertainty preserves item projection truth;
- live DevKit coverage uses three payments and a fresh wallet with multiple
  UTxOs, and demonstrates that all submissions precede confirmation.

### Phase C2: Operability

**Work**

- Add core error-code constants and documentation.
- Add `onStreamAborted` with exact ordering and document the `onItemRejected` contract implemented with Phase B1.

**Verification**

- build-time catalog membership covers emitted core literals and rejects orphan constants;
- extension codes remain extension-owned;
- abort callback ordering, reentrancy, and listener isolation are pinned;
- rejection callback is exactly once for blocking/non-blocking calls and creates no item state;
- no deferred API from [Deferred Items](#deferred-items) is introduced incidentally.

## Verification Matrix

| Area | Unit/contract tests | Integration tests | Required evidence |
|------|---------------------|-------------------|-------------------|
| Beginner defaults | runtime, builder, lane, executor, identity | Yaci DevKit payment | sample compiles and confirms on Java 17 and 21 |
| Explicit compatibility | existing stream suites | existing stream ITs | explicit lane/executor behavior unchanged |
| Startup cleanup | injected failure at each start stage | durable/bootstrap where practical | abort once, original failure retained |
| Receipt outcomes | all statuses, timeouts, interruption, repair race/polling | uncertain/reconcile path | no uncertain outcome appears successful or triggers resubmission |
| Validation | every eager failure class, stats, both listeners, retry | source adapter tests | no rejected item reported accepted |
| Durable boundaries | ADR 0006 contract suites | ADR 0006 H2/PostgreSQL plan | DX docs do not overstate unshipped durability |
| Pipelining | generated settings and projections | DevKit multi-step flow | advanced lever is real and honest |
| Documentation | snippet compilation/link check | getting-started execution | docs match shipped API |

The repository's normal Gradle verification runs use Java 17. A targeted Java 21 lane additionally verifies virtual-thread selection and lifecycle. No implementation phase is complete while its focused tests or the existing explicit-configuration suite regress.

## Documentation Plan

The API is not beginner-accessible until its documentation follows the same disclosure order.

1. **Getting started:** backend, `FlowRuntime`, one account reference, `open`, stable item id, `TxPlan`, and timed `awaitConfirmed`; count every setup line.
2. **Outcome handling:** uncertainty-first three-catch recipe and `awaitResolution` first; `awaitSettled` and asynchronous `completion()` second; loud `RECOVERY_REQUIRED` warning adjacent to all three.
3. **Production reliability:** direct `FlowEngine` executor ownership first; durable engine + stream stores, redelivery, and hydration remain marked incomplete until ADR 0006 ships.
4. **Throughput:** derived lanes, explicit/partitioned lanes, windows, per-window pipelining, batching, and deduplication trade-offs.
5. **Advanced integration:** templates, sources, listeners, cancellation, custom planners, and stable identity obligations.
6. **Reference:** error codes, statuses, results/reports/outcomes, the normative effective-default table, resource-reference roles, and lifecycle behavior.

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

Rejected. Tests and advanced source-before-start wiring require an unstarted instance. `open()` and `Builder.open()` make the started path explicit while retaining `build()`.

### Create executors inside the engine or stream

Rejected. It breaks caller ownership, server resource control, deterministic tests, and executor choice. The explicit, `AutoCloseable` `FlowRuntime` is the only thread-owning convenience boundary; direct engine/stream construction remains resource-neutral.

### Enhance `FlowEngine` to conditionally own beginner resources

Rejected for the initial API. It could remove the extra class, but it would give one `FlowEngine` type two lifecycle contracts: caller-owned when executors and registries are supplied, and engine-owned when defaults are created. `FlowEngine` would then need conditional `AutoCloseable` behavior, ownership introspection, stream tracking, close ordering, and rules for mixed caller-owned and engine-created resources. Those semantics are easy to misunderstand and could regress applications that currently treat the engine as resource-neutral.

Keeping ownership in the optional `FlowRuntime` makes construction explain the lifecycle contract. Internally it still constructs and calls a normal `FlowEngine`; it is not a replacement for the engine. If future evidence shows that `FlowEngine` can offer managed ownership without conditional or ambiguous close semantics, that may be proposed separately rather than implied by convenience builder overloads.

### Infer `withSigner(fromRef)` from a single funding reference

Rejected for the core plan contract. Funding selection and authorization coincide for a simple account payment but diverge for policies, scripts, multisignature, fee delegation, and multi-party plans. Requiring the signer declaration avoids an attractive but unsafe rule. Account/wallet builder conveniences remove registry boilerplate while preserving the distinction.

### Add `engine.openStream(streamId)`

Not selected for the first release. `runtime.open(...)` is the discoverable first-hour path and static/builder `TxFlowStream.open(...)` serves application-owned engines. Adding a reverse dependency-shaped convenience on `FlowEngine` creates a third equivalent front door and makes the server API appear to own stream lifecycle. Usage evidence may justify the additive delegate later.

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

Rejected. It would hide network I/O, recovery authority, and potentially unbounded waiting inside a convenience accessor. It classifies the latest available projection and makes uncertainty loud. `awaitResolution(...)` is intentionally named and parameterized when the caller wants reconciliation I/O.

### Add a `finalCompletion()` promise

Rejected for this round. The existing receipt promise is deliberately point-in-time and settles on `RECOVERY_REQUIRED`. A final-only promise has no progress source without maintenance, so it either hangs silently or smuggles in an observer. Explicit `awaitResolution(...)` provides a bounded beginner recovery path without changing asynchronous settlement semantics.

### Put chaining on the stream builder

Rejected. The setting is ineffective or misleading for most planners and could appear to override custom/template flows. It belongs to the built-in planner that produces a multi-step flow.

### Move store types now

Deferred. Java compatibility bridges cannot faithfully re-export final records and SPI signatures. Functional DX has higher value and lower regression risk.

## Consequences

### Positive

- A beginner can submit and confirm a payment without learning supplier adapters, signer-registry types, lane configuration, executor shutdown ordering, work-item builders, or completion-stage conversion.
- The default still follows Cardano's UTXO concurrency model rather than hiding it behind a global lock.
- Advanced users retain the full engine, planner, lane, store, source, ownership, and recovery surface.
- Startup and uncertain-outcome failure modes become harder to misuse.
- The optional `FlowRuntime` makes nested resource close ordering correct by construction while leaving direct `FlowEngine` ownership explicit and unchanged.
- Validation semantics become consistent between blocking and non-blocking submission.
- Durable correctness gets its own focused acceptance process in ADR 0006.
- Existing explicit configurations remain valid.

### Negative and costs

- `FlowEngine` exposes its execution executor as a read-only public detail.
- The optional `FlowRuntime` adds one public resource-owning composition type and Java-version-sensitive executor defaults that require Java 17 and Java 21 verification; it adds no execution implementation.
- Default lane behavior shifts a missing configuration error from build time to typed item validation for plans that cannot derive funding.
- Syntactic address/reference lane identity requires a documented do-not-mix rule for the same wallet.
- Honest validation rejection intentionally changes current preview counters, callbacks, retry, and receipt behavior.
- Three typed waiting exceptions add public types to an already large stream package.
- The implementation requires careful startup-failure, runtime close-race, and recovery-polling tests, not only convenience delegates.

### Accepted residual risks

- `close()` without a duration may wait indefinitely for accepted work; changing that default would silently cancel or abandon funds-critical work. Documentation presents `close(Duration)` for bounded shutdown.
- A caller can still choose `awaitSettled()` and mishandle `RECOVERY_REQUIRED`; that method is explicitly the all-outcomes advanced surface. Beginner docs use timed `awaitConfirmed()` and the uncertainty recipe.
- Custom planners remain responsible for deterministic full-request content and correct item-to-step mapping.
- A direct `FlowEngine` caller may shut down an inherited executor too early because the application owns it; `FlowRuntime` fixes the first-hour case but cannot police server-owned resources.

## Open Questions

No blocking ADR 0005 questions remain after review rounds 2 and 3. The previously open outcome-code, standby-code, planner-mode, accessor, snapshot, and `FlowRuntime` questions are resolved in versions 1.2.0 and 1.2.1. Work intentionally postponed by those resolutions is recorded in [Deferred Items](#deferred-items), not left ambiguously open.

Any new finding from the final acceptance review must be added here with a stable number and a blocking/non-blocking classification in the next ADR version. Durable registration SPI and partial-write questions remain outside this document; ADR 0006 owns them.

## Review Checklist

Reviewers should explicitly confirm or challenge:

- whether `awaitConfirmed()` is the correct beginner default;
- whether a five-minute caller budget is an appropriate tutorial value given the separate 60-second simple confirmation and queueing bounds;
- whether reading `current()` after promise settlement is sufficient and race-safe;
- whether `awaitResolution` has the right blocking, I/O, timeout, and no-resubmission contract;
- whether cancellation deserves its own exception type;
- whether derived funding is safe for every supported single-transaction `TxPlan` form;
- whether syntactic funding identity, `TXSTREAM_LANE_AMBIGUOUS`, and the deprecated alias window are clear;
- whether explicit maintenance remains necessary for all timed stream features;
- whether abort is the correct cleanup operation for every startup failure stage;
- whether eager validation should create no retained receipt/state;
- whether `onItemRejected` is sufficiently isolated and useful for source adapters;
- whether planner-local chaining with only `SEQUENTIAL`/`PIPELINED` is the right advanced API location;
- whether the optional top-level `FlowRuntime`, its strict delegation to one ordinary `FlowEngine`, narrow builder, pool sizes, Java-version selection, and close ordering are safe and unsurprising;
- whether any existing explicit API becomes semantically different despite being source-compatible;
- whether the implementation phases can be merged independently without exposing an unsafe intermediate release.

## Acceptance and Implementation Record

The following criteria governed acceptance. Review round 5 records their final
disposition; they remain regression gates for the implemented API:

- final acceptance review confirms that review rounds 2 and 3 resolved every blocking question and introduced no new blocker;
- the beginner and advanced API examples are judged coherent together;
- runtime ownership, executor selection, and lane precedence are unambiguous;
- startup cleanup, receipt uncertainty, and explicit resolution semantics are accepted;
- ADR 0006 boundaries are clear and no durable guarantee is implied by the beginner API;
- intentional preview breaks and deferred pre-1.0 work are explicitly agreed.

Implementation completion requires:

- the getting-started success sample is at most twenty substantive Java lines, counts runtime/account setup, names no lane, executor, or signer-registry type, compiles, and confirms on Yaci DevKit;
- the adjacent uncertainty-first recipe, including timeout handling, compiles and `awaitResolution` never resubmits;
- Java 17 fixed-pool and Java 21 virtual-thread runtime paths satisfy the documented ownership and close-order tests;
- existing explicit `.lane(...).executor(...).build(); start();` tests remain green;
- `open()` and `Builder.open()` abort every partially started failure path and retain the original failure;
- `awaitConfirmed()` never reports `RECOVERY_REQUIRED` as success and carries the latest result/hash when uncertain;
- timed waits preserve interruption and do not cancel work;
- invalid work is never `OK`, counted accepted/failed, retained, or announced through `onItemAccepted`, and it emits exactly one isolated `onItemRejected`;
- planner-local pipelining is demonstrably effective and does not affect incompatible planners;
- the core error-code catalog and abort listener contracts are tested and documented;
- no new executor, scheduler, timer, status machine, or parallel transaction execution runtime is introduced inside `FlowEngine` or `EngineTxFlowStream`; all convenience-owned resources live in optional `FlowRuntime`, which delegates behavior to one ordinary `FlowEngine`.

This preserves TxStream's correctness-first core while making its common path approachable and its advanced power explicit rather than mandatory.
