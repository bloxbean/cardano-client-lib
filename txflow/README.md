# TxFlow

TxFlow compiles and executes reusable, multi-transaction Cardano workflows. The current API
separates a portable flow definition from run-specific inputs and runtime infrastructure:

```text
YAML or JSON definition -> TxFlowCodec -> TxFlowCompiler/preflight -> FlowExecutionRequest -> FlowEngine
                                                                                         -> result/events/store
```

TxFlow requires Java 17. Applications own every executor supplied to the runtime. An application
running on Java 21 can therefore use virtual threads without TxFlow taking a compile-time
dependency on Java 21 or creating hidden thread pools.

## Choose the right API

| Use case | API | Status |
| --- | --- | --- |
| New portable or server-side flows | `TxFlowCodec`, `FlowExecutionRequest`, `FlowEngine` | Current |
| Programmatic definition construction | `TxFlow`, `FlowStep`, exactly-one-transaction `TxPlan` | Supported portable subset |
| Existing preview integrations | `FlowExecutor`, `FlowResult`, legacy YAML | Compatibility API |

New applications should use `FlowEngine`. `FlowExecutor` remains available for compatibility, but
it combines definition and execution concerns and is not the canonical durable-runtime API.
Programmatic definitions submitted to `FlowEngine` follow the same portable boundary as YAML/JSON:
Java factories, multi-transaction or variable-bearing `TxPlan` values, legacy dependencies, and
step-local retry overrides remain compatibility-only constructs.

## Dependencies

Use the same CCL version for every module:

```gradle
def cclVersion = "0.8.0-pre5-SNAPSHOT"

dependencies {
    implementation "com.bloxbean.cardano:cardano-client-txflow:${cclVersion}"

    // Choose the backend/provider modules required by the application.
    implementation "com.bloxbean.cardano:cardano-client-backend-blockfrost:${cclVersion}"
}
```

Add the optional relational store only when restart durability is required:

```gradle
dependencies {
    implementation "com.bloxbean.cardano:cardano-client-txflow-store-rdbms:${cclVersion}"
}
```

H2 2.x is included by the RDBMS extension. PostgreSQL applications provide their own JDBC driver
and preferably an application-managed `DataSource` or connection pool.

## Portable definition

Portable documents use `txflow.cardano-client.dev/v1alpha1`. Each step embeds the QuickTx
single-transaction projection (`tx` plus optional `context`). During compilation, TxFlow wraps
that projection in a QuickTx `TransactionDocument` and creates a fresh, one-transaction executable
`TxPlan`; it is not a second independent transaction definition.

```yaml
api_version: txflow.cardano-client.dev/v1alpha1
kind: TxFlow
metadata:
  name: send-payment
spec:
  network: preview
  parameters:
    beneficiary:
      type: address
      required: true
  execution:
    confirmation:
      min_confirmations: 1
      check_interval: 1s
      timeout: 2m
    rollback:
      action: FAIL
      monitoring_horizon: UNTIL_FLOW_TERMINAL
      minimum_consistent_absence_observations: 2
  steps:
    - id: payment
      transaction:
        tx:
          from_ref: account://sender
          intents:
            - type: payment
              address: '${{ inputs.beneficiary }}'
              amounts:
                - unit: lovelace
                  quantity: 2000000
        context:
          signers:
            - ref: account://sender
              scope: payment
```

`needs` expresses step ordering. When a later step must spend a particular output produced by an
earlier step, name the output and consume it with `flow_output`; ordering alone does not select a
UTxO.

The published `quicktx-transaction-v1alpha1.schema.json` describes the embedded projection backed
by `TransactionDocument.TxContent` and `TransactionDocument.TxContext`. It should not be described
as a full schema for every programmatic `TxIntent`: some intent implementations do not yet have
portable JSON/YAML entries. Treat the portable schema and codec as the source of truth for what can
be authored in a document.

The historical `tx.from_wallet` field is parsed for schema compatibility but is not materialized by
the current QuickTx `TxPlan` projection. Compilation rejects it with
`TXFLOW_TRANSACTION_FROM_WALLET_UNSUPPORTED`; use `tx.from_ref` and an application resource resolver.
Portable compilation also rejects fields unknown to the concrete QuickTx transaction or intent
model with `TXFLOW_TRANSACTION_FIELD_UNKNOWN` and runs the intent's existing model validation before
fingerprinting. This compiler-local strictness does not alter standalone legacy QuickTx parsing.

## Parse, preflight, and execute

Parse untrusted input with explicit bounded options and report all diagnostics before calling
`requireFlow()`:

```java
FlowParseResult parsed = TxFlowCodec.standard()
        .parse(source, FlowParseOptions.serverDefaults());

if (parsed.hasErrors()) {
    throw new IllegalArgumentException(parsed.getDiagnostics().toString());
}

TxFlow definition = parsed.requireFlow();
FlowBindings bindings = FlowBindings.builder()
        .put("beneficiary", receiverAddress)
        .build();
```

Create the engine once from application-owned services and executors. The example below uses a
CCL `BackendService`; applications can supply any implementations of the four core interfaces.

```java
ExecutorService flowTasks = Executors.newFixedThreadPool(8);

DefaultSignerRegistry signers = new DefaultSignerRegistry()
        .addAccount("account://sender", senderAccount);

FlowEngine engine = FlowEngine.builder(
                new DefaultUtxoSupplier(backendService.getUtxoService()),
                new DefaultProtocolParamsSupplier(backendService.getEpochService()),
                new DefaultTransactionProcessor(backendService.getTransactionService()),
                new DefaultChainDataSupplier(backendService))
        .executor(flowTasks)
        .signerRegistry(signers)
        .build();

FlowExecutionRequest request = FlowExecutionRequest.builder(definition)
        .executionId("payment-2026-000123")
        .bindings(bindings)
        .idempotency("payments", "order-000123")
        .spendingResource("account://sender")
        .build();

FlowCompilationResult preflight = engine.preflight(request);
if (preflight.hasErrors()) {
    throw new IllegalArgumentException(preflight.getDiagnostics().toString());
}

FlowExecutionHandle handle = engine.start(request);
FlowExecutionResult result = handle.await();

if (!result.isSuccessful()) {
    // RECOVERY_REQUIRED is deliberately different from a conclusive failure.
    log.warn("TxFlow {} ended in {}: {}", result.executionId(), result.state(), result.error());
}
```

The application shuts down `flowTasks`; `FlowEngine` never closes it. With Java 21, a caller can
pass `Executors.newVirtualThreadPerTaskExecutor()` instead. Keep the public integration typed as
`Executor`/`ExecutorService` so the TxFlow code remains portable across Java 17 and Java 21.

## Runtime rules that matter

- Use a stable execution ID for correlation. Reusing an active ID with a different request
  fingerprint is rejected.
- Use an application namespace and key for idempotent commands. Reusing a claim for different
  definition, bindings, secure references, or spending controls is a conflict.
- Declare a stable canonical identity for each shared spending resource. TxFlow serializes those
  resources in sorted order unless policy explicitly permits an opt-out.
- Register signers, scripts, and other server-owned resources by logical reference. Do not embed
  private signing material in portable documents.
- In durable mode, every resolved sensitive binding needs a secure reference. The raw value is not
  stored, but its external reference and an unsalted SHA-256 fingerprint are; protect the database
  and backups, especially when values have low entropy.
- Treat `RECOVERY_REQUIRED` as an uncertain outcome. Reconcile the recorded transaction hash and
  exact signed payload before retrying or rebuilding.
- Consume handle events with an exclusive sequence cursor: `handle.getEventsAfter(lastSequence)`.
  Cancellation is cooperative and does not interrupt worker threads.

## Durable execution

`InMemoryFlowExecutionStore` is useful for tests and reference semantics; it does not survive a
process restart. Use the RDBMS extension for durable deployments.

### Embedded H2

File-backed H2 is the supported single-JVM embedded profile:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .jdbcUrl("jdbc:h2:file:./data/txflow")
        .build();

ExecutorService flowTasks = Executors.newFixedThreadPool(8);
ExecutorService maintenanceTasks = Executors.newFixedThreadPool(4);

FlowEngine engine = FlowEngine.builder(utxoSupplier, protocolParamsSupplier,
                transactionProcessor, chainDataSupplier)
        .executor(flowTasks)
        .maintenanceExecutor(maintenanceTasks)
        .store(store)
        .build();
```

The independent maintenance executor is required in durable mode so lease renewal cannot be
starved while a flow task waits. The size above is only an example: bound and size it for concurrent
renewals, JDBC connection-acquisition latency, and the database pool. Avoid both a single shared
renewal thread and an unbounded cached pool. The application owns and closes both executors and the
store.

The built-in engine/store path persists signed CBOR inline. External payload references are a
codec and recovery extension point for custom or pre-seeded persistence; the engine builder does
not externalize payloads automatically.

### PostgreSQL

For PostgreSQL, let the deployment own pooling, credentials, TLS, and driver lifecycle:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .dataSource(dataSource)
        .dialect(PostgresDialect.INSTANCE)
        .schemaManagement(SchemaManagement.VALIDATE)
        .build();
```

Run migration with a schema-owner role, then use `VALIDATE` and a least-privilege runtime role.
Back up all TxFlow tables and schema history as one transactionally consistent recovery unit.
Database fencing protects durable state changes, but it cannot stop a partitioned process from
submitting transaction bytes it already signed; active-active deployments still need external
UTxO reservation or equivalent submission serialization.

For command-line tools and small services, URL mode also supports `username(...)`, `password(...)`,
and optional `driverClassName(...)`; JDBC 4 discovery is the default. URL mode is non-pooling, and
those options cannot be combined with `dataSource(...)`.

See [the RDBMS store guide](../txflow-extensions/txflow-store-rdbms/README.md) for supported H2 and
PostgreSQL profiles, schema management, grants, backup/restore, monitoring, and failure semantics.

## Recovery

Durable recovery acquires fenced execution and spending-resource leases, verifies the persisted
payload, and observes the recorded transaction hash. It can resubmit only the identical verified
signed CBOR; it never substitutes a newly built transaction merely because an index lookup is
empty. The caller must supply a fresh authoritative Cardano **slot**—not a block height or wall-clock
value—and should fail closed if it cannot obtain one. TxFlow does not independently verify the
freshness of `currentSlot`.

```java
FlowRecoveryRequest recoveryRequest = FlowRecoveryRequest.builder()
        .executionId(executionId)
        .stepId(stepId)
        .attemptNumber(attemptNumber)
        .currentSlot(currentSlot)
        .resubmitSafetyMargin(20)
        .build();

FlowRecoveryResult recovery = engine.recover(recoveryRequest);
```

Select `stepId` and `attemptNumber` explicitly in operator and cross-process recovery workflows.
If omitted, the engine chooses the highest attempt number among all matching steps; that is safe
only when the target is unambiguous. The lower-level attempt-based request is useful when the
application already holds the exact uncertain snapshot.

`recover(...)` reconciles and records that one attempt. It does not automatically restart the
remaining steps or resume the whole business flow; the application must explicitly decide and
invoke its continuation or operator workflow from the recovery result.

Recovery enforces the safety margin only when the persisted attempt has `validToSlot`. The stock
coordinator can resubmit identical bytes without an upper validity bound. Controlled server
deployments should use
`FlowExecutionPolicy.builder().requireValidityInterval(true).build()` and obtain a fresh slot
from an authoritative source before recovery.

## TxStream (streaming submission)

`TxFlowStream` (package `com.bloxbean.cardano.client.txflow.stream`) is the streaming submission
API on top of `FlowEngine`: it accepts a continuous feed of work items, executes each as an
idempotent engine execution on **lanes** (a lane is a funding scope; different lanes run
concurrently, one lane runs serial FIFO), and reports status as an honest projection of engine
truth — `SUBMITTED` is never asserted before the backend, a known transaction hash is never
dropped, and an uncertain outcome is `RECOVERY_REQUIRED`, never a false failure. Reach for it
when submitting many transactions over time where each must land exactly once and survive
failures; use plain QuickTx for one-off transactions and a `TxFlow` definition for one multi-step
workflow.

```java
try (TxFlowStream stream = TxFlowStream.builder("payouts", engine)
        .lane(ResolvedLane.ofFundingRef("payouts", "account://sender"))
        .executor(streamExecutor)
        .build()) {
    stream.start();

    TxStreamReceipt receipt = stream.submit(TxWorkItem.builder("payment-1")
            .withTxPlan(plan)                     // portable payload
            .withIdempotencyKey("order-1")        // redelivery attaches, never double-pays
            .build());

    TxStreamItemResult outcome = receipt.completion().toCompletableFuture().join();
}   // close() drains accepted work gracefully; nothing is cancelled
```

Optional layers: count/time windows with `perWindow()`/`batching(...)` planners (transaction
merging), partitioned fan-out lanes, a durable stream store (`RdbmsTxStreamStateStore`) with
restart re-attach, and active/standby ownership for HA. The multi-item planners give flow-level
dedup only — read the durability guide before pairing them with a redelivering source. Guides:

- [TxStream Getting Started](../docs/content/preview/txflow/txstream-getting-started.mdx)
- [TxStream: Durability & Exactly-Once](../docs/content/preview/txflow/txstream-durability.mdx)
- [TxStream: Lanes, Batching & Throughput](../docs/content/preview/txflow/txstream-throughput.mdx)

## Documentation map

- [Current public guide](../docs/content/preview/txflow/overview.mdx)
- [Portable authoring migration](docs/PORTABLE_TXFLOW_MIGRATION.md)
- [Durable runtime contract](docs/DURABLE_RUNTIME.md)
- [Detailed design and compatibility reference](docs/DESIGN_AND_USAGE.md)
- [ADR 0002: portable contract and runtime](adr/0002-portable-txflow-contract-and-runtime.md)
- [ADR 0003: relational durable store extension](adr/0003-relational-durable-store-extension.md)

The public guide is the recommended learning path. The design document and legacy public pages
remain useful when maintaining an existing `FlowExecutor` integration.

## Developer map

The implementation is organized by responsibility so a change normally has one clear starting
point:

| Area | Package or module | Owns |
| --- | --- | --- |
| Definition graph | `com.bloxbean.cardano.client.txflow` | `TxFlow`, `FlowStep`, legacy dependencies, retry value objects |
| Portable I/O | `txflow.codec` and `src/main/resources/schema` | format detection, bounded parsing, diagnostics, versioned writing, schemas |
| Portable definition values | `txflow.model` | parameter declarations, bindings, transaction templates, output selectors |
| Compilation | `txflow.compile` | binding, policy/resource preflight, fresh per-run `TxPlan` materialization, fingerprints |
| Runtime | `txflow.exec` | request admission, spending coordination, execution, events, results, error mapping |
| Requested/server policy | `txflow.config` | confirmation, retry, rollback, validity, and host-enforced limits |
| Logical resources | `txflow.resource` | normalized references, public descriptors, capabilities, contention identities |
| Durable contract | `txflow.store` | snapshots, attempts, journals, idempotency, leases, fences, store codec |
| Reconciliation | `txflow.recovery` | attempt selection, signed-payload verification, chain observation, safe resubmission |
| JDBC adapter | `txflow-extensions/txflow-store-rdbms` | H2/PostgreSQL dialects, schema manager, JDBC transactions, store implementation |

Keep ownership boundaries visible in changes:

- The codec validates document syntax; the compiler validates bound semantics and server policy.
- QuickTx owns `TransactionDocument`, `TxIntent`, signer/script resolution, and the executable
  `TxPlan`. TxFlow owns workflow ordering and runtime state.
- The core store API defines behavior. Database adapters implement it without changing the
  engine's correctness model.
- Transaction uncertainty crosses layers only as a typed result. Do not convert an unknown
  submission, ambiguous absence, persistence uncertainty, or stale fence into an ordinary retry.
- Thread creation and shutdown stay in the embedding application. New concurrency work should
  accept `Executor`, scheduler, clock, or interface seams rather than choosing a thread type.

When changing a portable field, update the model/codec, both JSON Schemas when the embedded
transaction shape is affected, conformance fixtures, compiler checks, documentation, and the ADR
compatibility statement together. When changing durable behavior, run the reusable
`FlowExecutionStoreContract` plus the H2 forced-restart and PostgreSQL integration layers; a unit
test against only the in-memory store is not a database compatibility claim.

## Build and test

Use Java 17:

```shell
./gradlew :txflow:test
./gradlew :txflow:integrationTest
./gradlew :txflow-extensions:txflow-store-rdbms:test
./gradlew :txflow-extensions:txflow-store-rdbms:integrationTest
```

TxFlow's Yaci DevKit integration tests require a reachable external DevKit. The PostgreSQL
integration layer requires Docker and deliberately fails rather than silently skipping when Docker
is unavailable.
