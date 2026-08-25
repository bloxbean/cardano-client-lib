# TxStream API DX Phase 0 Baseline

This document records the implementation baseline immediately before ADR 0005 changes the TxStream public front door. It is a characterization artifact, not a user guide or a new API contract. Later milestone branches must update the named tests deliberately when they implement an intentional behavior change.

## Verification environment

- Required build JVM: Java 17.
- Baseline command: `JAVA_HOME=<java-17-home> ./gradlew :txflow:test`.
- Baseline result on 2026-08-25: 993 tests passed before Phase 0 additions.
- Java 25 is not a valid baseline for this repository: the current Mockito/Byte Buddy combination produces instrumentation failures there. This is an environment/tooling incompatibility, not a TxStream behavior failure.

## Public front door before ADR 0005

`TxFlowStream` currently exposes:

- lifecycle: `start`, `drain`, `awaitDrain`, `abort`, `close`, `close(Duration)`, and `isHealthy`;
- submission: `submit(TxWorkItem)` and `trySubmit(TxWorkItem)`;
- observation/recovery: `getItemStatus`, `reconcile`, `getBatchStatus`, `getStats`, `ownership`, `reattach`, and `bootstrap`;
- control: `flush`, `cancelItem`, `cancel`, and `cancelExecution`;
- construction: `builder(String, FlowEngine)` followed by `build()` and an explicit `start()`.

The builder currently requires an explicit lane policy and an explicit caller-owned stream executor. It has no started factory, `TxPlan` submission overload, receipt blocking helper, or managed resource owner. The following explicit controls already exist and must remain available: lane/lane policy and resolver, source, state store, listener, planner, templates, window, maintenance scheduler, buffer/in-flight/retention bounds, reconciliation observer, ownership, executor, and clock.

## Characterized behavior and change map

| Contract | Before ADR 0005 | Characterization evidence | Planned change |
|----------|-----------------|---------------------------|----------------|
| Explicit lane and executor | Both required by `Builder.build()` | `TxFlowStreamDxBaselineTest.builderCurrentlyRequiresAnExplicitLaneAndExecutor` | A1 inherits an engine executor; A2 supplies the funding-derived default lane |
| Explicit executor ownership | Stream close never shuts it down | `TxFlowStreamDxBaselineTest.explicitConfigurationPreservesListenerOrderingAndCallerExecutorOwnership` | Preserved by A1 and all later phases |
| Listener lifecycle ordering | started before acceptance; terminal update before drained; drained before closed | Same Phase 0 test plus `TxFlowStreamTest.throwingListenerNeverBreaksDispatchOrDrain` | Preserved; C2 adds abort/rejection callbacks |
| Non-portable item validation | `submit` returns a failed receipt, calls `onItemAccepted`, increments accepted/failed, and retains it | `TxFlowStreamDxBaselineTest.portabilityValidationCurrentlyReturnsAnAcceptedFailedReceipt` and `TxFlowStreamTest.javaFactoryPayloadFailsTypedAtSubmitAndIsNeverRegistered` | B1 deliberately changes this to throw/`REJECTED` with no receipt, counters, retention, or accepted callback |
| Invalid identifier/text-policy validation | `submit` throws and `trySubmit` returns `REJECTED`; no retained state | `TxFlowStreamFailurePathTest.invalidIdempotencyKeyIsTypedInvalidItemOnSubmitAndRejectedOnTrySubmit` | B1 unifies eager no-work rejection without weakening this behavior |
| Registration failure | blocking submission throws; non-blocking submission rejects; no engine start | `TxFlowStreamFailurePathTest.registerItemRuntimeFailureThrowsOnSubmitAndRejectsOnTrySubmit` | B1 aligns counters, callbacks, and retry behavior |
| Start/bootstrap/ownership/source failures | Explicit `start()` owns the current failure behavior | `TxFlowStreamOwnershipTest`, `TxFlowStreamPartitionedTest`, `TxFlowStreamDurableReattachTest`, and source tests | A3 adds exception-safe `open()` and abort-on-start-failure cleanup |
| Dispatch rejection/failure | Item settles with typed failure; systemic rejection cannot strand buffered work | `TxFlowStreamFailurePathTest.gatewayStartThrowingSynchronouslyFailsItemTypedAndKeepsStreamHealthy` and `itemBufferedDuringSystemicFailureIsFailedTypedNotStranded` | A1 must preserve it under inherited/shared executors |
| Shared/direct dispatch | Direct, manual, and fixed-pool executors are exercised; stream tasks do not wait for engine completion | `TxFlowStreamTest`, `TxFlowStreamConcurrencyTest`, `TxFlowStreamByFundingAddressTest`, and `TxFlowStreamAbortTest` | A1 adds a dedicated shared one-thread engine/stream deadlock regression test |
| Uncertain outcome | Receipt promise settles once as `RECOVERY_REQUIRED`; `current()` may later repair | `TxFlowStreamTest.inProgressStepInsideTerminalFlowBecomesRecoveryRequiredWithHash` and `TxFlowStreamRecoveryTest` | A4 adds typed waits and explicit reconciliation polling without changing promise semantics |
| Funding-derived lanes | Address and reference sources already derive distinct syntactic lanes; both ambiguous forms currently share the underivable diagnostic | `TxFlowStreamByFundingAddressTest` | A2 renames the policy, defaults it, and separates `TXSTREAM_LANE_AMBIGUOUS` |
| Existing explicit lane behavior | Single, explicit/resolved, partitioned, and funding-derived policies enforce identity and FIFO | lane enforcement, concurrency, partitioned, and overlap suites | Preserved throughout |

## Core error-code inventory

The following 52 `TXSTREAM_*` literals are emitted or documented by production code in the core `txflow` module before ADR 0005:

```text
TXSTREAM_ABANDONED
TXSTREAM_ABORTED
TXSTREAM_BATCH_INELIGIBLE_ITEM
TXSTREAM_BINDING_FAILED
TXSTREAM_BINDING_MISSING
TXSTREAM_BOOTSTRAP_CONFIG_DRIFT
TXSTREAM_BOOTSTRAP_FAILED
TXSTREAM_CLOSED
TXSTREAM_DISPATCH_FAILED
TXSTREAM_DRAIN_FAILED
TXSTREAM_DUPLICATE_ITEM
TXSTREAM_EXECUTION_CANCELLED
TXSTREAM_EXECUTION_FAILED
TXSTREAM_EXECUTION_UNOBSERVABLE
TXSTREAM_IDEMPOTENCY_KEY_REUSE
TXSTREAM_INTERRUPTED
TXSTREAM_INVALID_ITEM
TXSTREAM_ITEM_CANCELLED
TXSTREAM_ITEM_UNKNOWN
TXSTREAM_LANE_MISMATCH
TXSTREAM_LANE_REQUIRED
TXSTREAM_LANE_SCOPE_OVERLAP
TXSTREAM_LANE_SCOPE_VIOLATION
TXSTREAM_LANE_UNDERIVABLE
TXSTREAM_LANE_UNRESOLVED
TXSTREAM_NON_PERSISTABLE_SECRET
TXSTREAM_NON_PORTABLE_ITEM
TXSTREAM_OWNERSHIP_FENCED
TXSTREAM_OWNERSHIP_LOST
TXSTREAM_PLANNED_ENCODE_FAILED
TXSTREAM_PLANNED_WRITE_FAILED
TXSTREAM_PLANNER_FAILED
TXSTREAM_PLAN_CROSS_LANE
TXSTREAM_PLAN_INVALID
TXSTREAM_PLAN_OMITTED
TXSTREAM_PROJECTION_FAILED
TXSTREAM_REATTACH_CANCELLED
TXSTREAM_REATTACH_FAILED
TXSTREAM_REATTACH_STEP_MISSING
TXSTREAM_REATTACH_UNCONFIRMED
TXSTREAM_REGISTRATION_FAILED
TXSTREAM_SOURCE_FAILED
TXSTREAM_STORE_CODEC_CORRUPT
TXSTREAM_STORE_CODEC_DECODE_FAILED
TXSTREAM_STORE_CODEC_ENCODE_FAILED
TXSTREAM_STORE_CODEC_UNSUPPORTED
TXSTREAM_STORE_CODEC_UNSUPPORTED_VERSION
TXSTREAM_SUBSCRIBER_OVERFLOW
TXSTREAM_TEMPLATE_DRIFT
TXSTREAM_TEMPLATE_UNKNOWN
TXSTREAM_TIMEOUT
TXSTREAM_UNHEALTHY
```

Phase C2 will replace literal drift with a core `TxStreamCodes` catalog and a membership test. ADR 0005 additionally selects `TXSTREAM_RECOVERY_REQUIRED`, `TXSTREAM_ITEM_FAILED`, `TXSTREAM_NOT_ACTIVE`, and `TXSTREAM_LANE_AMBIGUOUS` for new behavior.

## Extension-owned codes

The RDBMS extension reuses the core codes `TXSTREAM_BINDING_MISSING`, `TXSTREAM_ITEM_UNKNOWN`, and `TXSTREAM_OWNERSHIP_FENCED`. It additionally owns these database-specific codes; they must not be moved into the core catalog merely because they share the prefix:

```text
TXSTREAM_STORE_CLOSED
TXSTREAM_STORE_CLOSE_FAILED
TXSTREAM_STORE_COMMIT_UNCERTAIN
TXSTREAM_STORE_CONFIGURATION_FAILED
TXSTREAM_STORE_CORRUPT
TXSTREAM_STORE_OPERATION_FAILED
TXSTREAM_STORE_PROJECTED_ERROR
TXSTREAM_STORE_ROLLBACK_UNCERTAIN
TXSTREAM_STORE_SERIALIZATION_FAILURE
TXSTREAM_STORE_UNAVAILABLE
TXSTREAM_STORE_UNIQUE_CONFLICT
```

SQL identifiers such as `TXSTREAM_ITEM` and `TXSTREAM_SCHEMA_HISTORY` are not diagnostic codes.

## Phase 0 guardrails

- Phase 0 changes no production behavior.
- Existing explicit configuration remains the compatibility oracle.
- Intentional expectation changes occur only in their owning milestone and cite this baseline.
- General accessor aliases, store-package movement, and unrelated API cleanup remain deferred under ADR 0005.
