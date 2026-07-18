# ADR 0003: Relational Durable Store Extension for TxFlow

**Status**: Implemented

**Date**: 2026-07-14

**Last Updated**: 2026-07-17

**Target Release**: To be decided

**Modules**: `txflow`, `txflow-extensions:txflow-store-rdbms`

**Related ADRs**: [ADR 0002: Portable TxFlow Contract, Compilation, Execution, and Recovery](0002-portable-txflow-contract-and-runtime.md)

## Context

ADR 0002 defines `FlowExecutionStore` as the durable boundary for execution snapshots, event
journals, idempotency claims, execution leases, and spending-resource leases. The current
`InMemoryFlowExecutionStore` is a reference implementation of those semantics. It is intentionally
process-local and loses all state when the process exits.

Applications can implement the store SPI, but doing so correctly requires more than saving a Java
object. An implementation must atomically enforce request identity, optimistic revisions, journal
ordering, lease epochs, resource ownership, and compaction watermarks. It must also preserve the
exact signed payload and typed recovery state across library and application restarts. Requiring
every application to independently implement these rules makes the most safety-sensitive part of
durable TxFlow easy to get wrong.

CCL should therefore provide a durable implementation that works with no external database server
for local and single-process deployments, while using the same API for applications that already
operate a relational database. The implementation must remain optional so the main `txflow`
artifact does not acquire a database driver, migration tool, or connection-pool dependency.

## Decision Summary

Add the optional Gradle module:

```text
txflow-extensions/
└── txflow-store-rdbms/
```

The published artifact is:

```text
com.bloxbean.cardano:cardano-client-txflow-store-rdbms
```

The module provides `RdbmsFlowExecutionStore`, an implementation of `FlowExecutionStore` backed by
JDBC. It supports two certified database profiles:

- **H2** is included as a runtime dependency and works out of the box. File-backed H2 is the
  embedded, single-JVM restart-durable profile. In-memory H2 remains a test or ephemeral profile.
- **PostgreSQL** is a certified shared-database profile. The application supplies its JDBC driver
  and normally supplies an application-managed `DataSource`.

The module exposes a dialect SPI. The work also publishes a reusable `FlowExecutionStore`
conformance test-fixtures variant from `txflow`, so the behavioral contract remains owned beside
the SPI rather than by one adapter. Other relational databases may be integrated through the
dialect SPI, but CCL does not describe an arbitrary JDBC database as compatible until the adapter
passes the conformance suite.

RocksDB is not part of this decision.

### Implementation refinement (2026-07-17)

The first post-implementation review tightened four database boundaries without changing the
public store API:

- snapshot and event timestamps are normalized to microsecond precision before payload encoding
  and JDBC binding, while lease and schema-history timestamps are normalized before binding. This
  matches the certified PostgreSQL precision and prevents driver rounding from making a valid row
  appear corrupt;
- H2 `MIGRATE` may complete an interrupted first V1 migration only when a compatible empty history
  table exists as the first-script marker, the remaining TxFlow table names form a valid V1
  creation-order prefix, and every prefix table is empty. The verified-empty prefix is dropped in
  reverse and recreated from the canonical checksummed script—discarding any extra indexes,
  defaults, or constraints on those tables—while every other partial schema remains fail-closed;
- relational envelope validation delegates readable payload versions to
  `FlowStoreCodec.supportsFormatVersion(...)`, so a future writer-version bump does not make an
  explicitly retained older reader unreachable; and
- PostgreSQL serialization/deadlock states and H2 lock-timeout states (`HYT00` or vendor code
  `50200`) map to the same retryable transaction-invalidated store error after confirmed rollback.

## Goals

- Provide a correct, maintained implementation of every `FlowExecutionStore` operation.
- Make local restart durability available with one optional CCL dependency and an H2 JDBC URL.
- Support PostgreSQL without requiring its driver in CCL's dependency graph.
- Preserve the same engine and store API when moving from embedded H2 to PostgreSQL.
- Make SQL and database-specific behavior explicit and testable through dialects.
- Version both the relational schema and the encoded TxFlow recovery payload.
- Provide reusable behavioral tests for CCL and third-party store implementations.
- Retain application ownership of execution and maintenance executors.

## Non-Goals

- Adding JDBC or H2 to the main `txflow` artifact.
- Providing a connection pool or replacing an application's persistence framework.
- Claiming that every JDBC database is compatible.
- Making embedded H2 an active-active or multi-host database.
- Solving the partitioned-worker transaction-submission window identified by ADR 0002.
- Implementing distributed UTXO reservation or a general workflow scheduler.
- Persisting arbitrary application objects through Java serialization.
- Providing RocksDB support in this module.

## Public Configuration Boundary

`DataSource` is the primary production configuration. JDBC URL configuration is a convenience for
embedded use, command-line tools, and small services.

The intended API shape is:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .jdbcUrl("jdbc:h2:file:./data/txflow")
        .schemaManagement(SchemaManagement.MIGRATE)
        .build();
```

An application-managed database uses:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .dataSource(dataSource)
        .dialect(PostgresDialect.INSTANCE)
        .schemaManagement(SchemaManagement.VALIDATE)
        .build();
```

The builder follows these rules:

- Exactly one of `DataSource` or JDBC URL configuration is required.
- JDBC 4 driver discovery is used by default. An optional driver class name is accepted for legacy
  or isolated class-loader environments.
- H2 and PostgreSQL dialects may be detected from a known JDBC URL or database metadata. An
  explicitly configured dialect wins, and an unknown database fails fast.
- JDBC URL configuration may include credentials as separate builder values. Secrets and complete
  URLs containing secrets are never written to logs or exception messages.
- URL-based connections are deliberately simple and do not create a hidden connection pool.
  Production PostgreSQL applications should supply a pooled `DataSource` when appropriate.
- The store never closes an application-supplied `DataSource`.
- `RdbmsFlowExecutionStore` is `AutoCloseable` and closes only resources it created. Closing it
  rejects new operations and does not await background work because the store owns none.

## Dialect Boundary and Certified Compatibility

The public `TxFlowSqlDialect` SPI contains only behavior that genuinely differs among relational
databases, including:

- database identification and dialect-specific database/version validation;
- migration resource selection and migration-lock acquisition;
- row-locking syntax used by claims, leases, and snapshot appends;
- deterministic-text, timestamp-precision, and required-index validation hooks;
- unique-constraint classification;
- bounded event-page SQL.

Common transaction orchestration, domain validation, payload encoding, error codes, and result
mapping remain in `RdbmsFlowExecutionStore`; they are not reimplemented by each dialect.

CCL makes compatibility claims at the dialect level:

| Profile | Driver ownership | Supported deployment claim |
|---------|------------------|----------------------------|
| H2 in-memory | CCL extension runtime | Ephemeral tests and local development |
| H2 file | CCL extension runtime | Single-JVM, local-disk restart durability |
| PostgreSQL | Application | Shared durable state, concurrent claims, and multi-process state fencing |
| Custom dialect | Application | No CCL support claim until the full conformance suite passes |

The current automated PostgreSQL certification profile is PostgreSQL 17.x (currently tested with
17.6). Other PostgreSQL major versions require the same conformance and integration qualification
before this release makes a compatibility claim for them. The H2 dialect rejects pre-2.x servers.

PostgreSQL state fencing does not make Cardano submission itself transactional with the database.
A stale, partitioned process can still submit already-signed bytes after losing its database lease.
Deployments requiring race-free active-active spending must additionally serialize transaction
submission or implement the UTXO reservation/coordinator protocol deferred by ADR 0002.

H2 file mode is not certified for multiple application processes, including configurations that
expose the file to several JVMs. H2 `AUTO_SERVER` or network-server deployment is outside the
embedded profile unless a later ADR and conformance run explicitly certify it.

## Relational Transaction Semantics

The adapter must satisfy the existing `FlowExecutionStore` contract rather than weaken it to the
lowest common JDBC behavior. At minimum, the relational schema represents:

- executions and their immutable definition/request fingerprints;
- the unique `(namespace, idempotency_key)` claim;
- the current snapshot revision, lifecycle state, journal tail, and compaction watermark;
- the versioned durable execution payload;
- ordered execution events with unique `(execution_id, sequence)` identity;
- execution leases with owner, monotonically increasing fence epoch, and expiry;
- resource leases keyed by canonical resource identity, with execution, owner, epoch, and expiry;
- schema migration history with version and checksum.

The following operations are indivisible database transactions:

1. `createOrGet` claims the idempotency tuple and creates the initial execution, or returns a
   fingerprint-compatible existing execution. A competing execution ID or incompatible
   fingerprint produces the existing stable conflict error.
2. `append` verifies the expected revision, validates the unexpired execution fence and every
   resource fence, appends a contiguous event batch, updates the snapshot, and advances revision
   and journal metadata atomically.
3. Lease acquisition chooses a new monotonically increasing epoch. Renewal retains the epoch;
   release and expiry can never make an old epoch current again.
4. Event compaction removes only an allowed terminal prefix and advances the watermark atomically
   without resetting sequence allocation.

Isolation and locking may differ by dialect, but correctness may not depend on an unprotected read
followed by an unconditional write. Deadlock and serialization failures are mapped to stable typed
store errors; the adapter does not silently retry an operation whose outcome is uncertain.

Relational columns in the certified profiles preserve timestamps to microseconds. The adapter
truncates top-level snapshot, event, and migration-history instants before encoding, binding, and
returning them. Lease expiry is instead rounded up to the next representable microsecond when
needed, so every positive duration remains strictly later than the caller's `now`. Column/payload
cross-checks therefore compare the same persisted value without relying on driver-specific
sub-microsecond rounding.

## Durable Payload Format

The current snapshot contains a generic `Map<String, Object>`. A database adapter must not rely on
Jackson's default map conversion or Java native serialization: both lose type identity or bind
persisted state to implementation classes in unsafe, incompatible ways.

Before the RDBMS adapter is implemented, `txflow` will define a closed, typed durable execution
model and a CCL-owned codec. The codec has these properties:

- a stable format identifier and monotonically increasing payload version are stored alongside
  every encoded snapshot;
- all supported generic values use explicit tags and fields, including scalar values, collections,
  bindings, and attempt snapshots;
- inclusion records, inline/external signed payloads, and instant fields are encoded within their
  owning snapshot, event, or attempt structures; byte arrays and instants are not accepted as
  arbitrary generic-map values;
- inline CBOR uses an explicit binary/Base64 representation and retains the recorded SHA-256 and
  Cardano transaction hash;
- unknown tags, duplicate fields, malformed values, and unsupported newer versions fail closed
  with a stable store error;
- reads support every payload version still covered by the library's migration policy;
- writes use only the current version; upgrading a value is deterministic and never changes the
  signed payload bytes or request fingerprints;
- arbitrary polymorphic class names and Java serialization metadata are forbidden.

The relational row stores payload format and version separately from the encoded bytes so a store
can reject an unsupported value before decoding it. Database schema version and payload version
are independent; a schema migration does not implicitly rewrite all execution payloads.

## Schema Management and Migrations

The extension supports three explicit modes:

- `MIGRATE`: validate migration history and apply pending forward migrations;
- `VALIDATE`: require a compatible current schema without modifying it;
- `NONE`: perform no startup schema action; normal operations still fail clearly if the schema is
  absent or incompatible.

An internally configured embedded H2 store defaults to `MIGRATE`. A shared or
application-managed `DataSource` defaults to `VALIDATE`; choosing `MIGRATE` for it is explicit.

Migration scripts are owned by the RDBMS module, versioned per certified dialect, and carry stable
checksums. Migration coordination must ensure that two starters cannot apply the same change
concurrently. A database with a schema newer than the running library fails fast. Automatic
downgrade and destructive repair are not supported. Every released schema version remains in the
upgrade test matrix for the documented compatibility window.

H2 can commit DDL between script statements. To avoid permanently bricking the embedded profile
after a first-run process stop, `MIGRATE` recognizes only a compatible empty
`txflow_schema_history` table as the first-script marker plus a valid creation-order prefix of the
remaining V1 tables. The marker must have the expected columns and primary key. Every prefix table
must be empty and no unexpected TxFlow table may exist.
The adapter drops that verified-empty prefix in reverse dependency order, recreates the canonical
schema from the checksummed script, validates it, and only then records the history row. Dropping
first discards extra indexes, defaults, or constraints on a verified-empty prefix rather than
allowing them to survive repair. `VALIDATE`, PostgreSQL, a missing marker with other tables,
non-empty partial state, and unexpected TxFlow tables never use this recovery path.

Schema or table-name configuration, if exposed, accepts identifiers rather than SQL fragments and
is validated and quoted by the dialect. All domain values are passed through prepared statements.

## Threading, Blocking, and Lifecycle

The store is a synchronous persistence component. It creates no execution executor, maintenance
executor, scheduler, lease-renewal task, or worker thread. JDBC work runs on the caller's thread,
which is already selected by `FlowEngine`'s caller-owned executor boundary. Applications on Java
21 may therefore use virtual-thread executors without a store API change; Java 17 applications may
use platform-thread executors.

An application-supplied JDBC driver or connection pool can own its own housekeeping resources;
those remain outside TxFlow ownership. The adapter documents this distinction and never attempts
to shut down an application-owned pool.

Closing a store-created embedded H2 resource performs an orderly close. Abrupt process termination
is covered separately by kill/restart tests and relies on committed database transactions, not on
an in-memory shutdown callback.

## Security and Operational Requirements

- Credentials, signed CBOR, payload references, JDBC query parameters, and owner tokens are not
  logged. Error messages contain stable codes and safe database context only.
- Every value is parameter-bound; configurable identifiers are validated and dialect-quoted.
- Store identities use shared UTF-8 byte limits, reject NUL and malformed Unicode, and stay within
  both relational column sizes and PostgreSQL indexed-key bounds for multibyte input.
- Deserialization is allow-listed by the durable codec and imposes size/count limits before large
  allocations.
- Applications control database transport security, encryption at rest, backups, retention, and
  credentials. The documentation provides least-privilege grants for each certified dialect.
- H2 file encryption may be configured through supported H2 settings, but CCL does not invent or
  retain an encryption key.
- PostgreSQL TLS and credential rotation remain `DataSource` or driver configuration concerns.
- Inline signed CBOR is supported for safe recovery. Applications with stricter size or secret
  handling requirements may use `SignedPayload.ExternalCbor`; CCL still verifies the resolved
  SHA-256 and transaction hash before resubmission.
- Database time is not silently mixed with engine time. Lease comparisons follow one documented
  time source per operation and retain the `FlowExecutionStore` expiry semantics.

## Verification Strategy

The `txflow` module will publish a reusable store conformance contract as a test-fixtures variant,
without adding JUnit or adapter dependencies to its runtime artifact. The same tests run against
`InMemoryFlowExecutionStore`, H2, PostgreSQL, and any candidate custom adapter. They cover:

1. Atomic idempotency claims and execution-ID/fingerprint conflicts under concurrency.
2. Revision compare-and-set, event ordering, mutation fencing, and all-or-nothing rollback.
3. Execution/resource lease acquisition, renewal, expiry, takeover, release, and stale-owner
   rejection.
4. Sorted multi-resource acquisition and contention without leaked partial ownership.
5. Event pagination, terminal-only compaction, monotonic watermark behavior, and compacted cursors.
6. Typed payload round trips, defensive binary handling, unknown versions, and corrupted payloads.
7. Representative mutation failures proving atomic rollback at durable transaction boundaries.

The RDBMS module adds these integration layers:

- H2 in-memory tests for fast behavioral feedback;
- JDBC transaction-phase failure injection covering operation rollback, commit-result uncertainty,
  rollback uncertainty, and connection-cleanup failures;
- H2 file close/reopen tests proving committed execution, event, lease, and payload recovery;
- child-JVM hard-kill tests at the prepared, submitting, submitted, included, and terminal
  boundaries, followed by recovery from the same H2 file;
- Testcontainers PostgreSQL tests for the same contract, real row locking and transaction
  isolation, server-raised PostgreSQL serialization-failure classification, and
  sub-microsecond timestamp normalization including rollover;
- concurrent workers using independent PostgreSQL store instances and JDBC connections, showing
  that a stale owner cannot mutate state without relying on process-local synchronization;
- migration tests from every supported released schema and payload version.
- H2 interrupted-first-migration tests covering safe empty completion and rejection of non-empty,
  malformed, or unexpected partial state.

No real PostgreSQL credentials are required for the normal build; Testcontainers supplies the
certification database. Testing a separately operated PostgreSQL service is an optional deployment
smoke/soak layer rather than part of this adapter's portable configuration surface.

## Implementation Sequence

1. Correct the core engine failure-persistence, active execution-ID validation, and executor
   rejection lifecycle paths before treating a durable adapter as production evidence.
2. Replace or encapsulate the generic snapshot-data map with the typed, versioned durable model and
   add codec compatibility tests in `txflow`.
3. Extract a reusable `FlowExecutionStore` conformance suite and run it against the in-memory
   implementation.
4. Add `txflow-extensions:txflow-store-rdbms`, the common JDBC transaction implementation, schema
   manager, and dialect SPI.
5. Implement and certify H2, including file reopen and child-process kill/restart tests.
6. Implement and certify PostgreSQL with Testcontainers, leaving the driver application-supplied
   for normal consumers.
7. Add documentation, migration/backup guidance, operational metrics, and optional external
   PostgreSQL smoke and soak verification.

Each step is independently reviewable. H2 or PostgreSQL is not called production-ready until its
own required test layer passes on Java 17.

## Alternatives Considered

### Leave Every Durable Store to Applications

Rejected. The SPI remains available, but atomic claims, fencing, journals, and recovery payloads
are sufficiently subtle that CCL should provide at least one maintained implementation and a
conformance suite.

### Add H2 Directly to `txflow`

Rejected. It would impose a database driver and persistence policy on all TxFlow users. The
extension module keeps the core runtime small and storage-neutral.

### Create an H2-Only Module

Rejected. H2 and PostgreSQL share the relational transaction model, schema lifecycle, codec, and
most SQL behavior. One RDBMS module with narrow dialects avoids duplicating safety-critical logic
while retaining explicit compatibility claims.

### Treat JDBC as Fully Portable Without Dialects

Rejected. Locking, upserts, binary types, error classification, pagination, and migration locking
are not sufficiently portable. Unknown databases must fail fast or supply a tested dialect.

### Use RocksDB

Rejected for this scope. The store contract is naturally relational and benefits from unique
constraints, row-level transactions, inspectable migrations, and PostgreSQL's shared-state model.
RocksDB would add native/JNI distribution, custom index maintenance, and different writer/process
constraints without a demonstrated workload need. It can be reconsidered only through a separate
ADR backed by measurements.

## Consequences

### Positive

- Applications can obtain local restart durability without implementing a store.
- The same `FlowExecutionStore` integration can move from H2 to PostgreSQL.
- Database-specific correctness is visible, bounded, and independently testable.
- The main `txflow` artifact remains free of database dependencies.
- A reusable conformance suite improves third-party adapters as well as CCL's implementations.

### Costs and Tradeoffs

- CCL owns relational migrations and payload compatibility across releases.
- H2 and PostgreSQL require separate dialect and integration-test coverage.
- H2's convenient embedded profile must be documented carefully to avoid overstating HA safety.
- PostgreSQL provides state coordination, but a separate protocol is still required for fully
  race-free active-active Cardano transaction submission.
- A typed durable model introduces an explicit compatibility surface that must evolve
  conservatively.

## Acceptance Criteria

This ADR is implemented only when:

- the extension is a separate published module and `txflow` has no H2/PostgreSQL dependency;
- typed payload encoding round-trips every recovery value and rejects unknown or corrupted input;
- the full store conformance suite passes for in-memory, H2, and PostgreSQL implementations;
- H2 file close/reopen and child-JVM kill/restart recovery tests pass;
- PostgreSQL concurrency, revision, idempotency, lease, fencing, and migration tests pass using a
  real PostgreSQL engine;
- schema upgrades from every supported released version are verified;
- no store-owned execution or maintenance threads are introduced;
- lifecycle ownership, deployment guarantees, security configuration, and the active-active
  submission limitation are documented for users.

Version 1 is the first relational schema and durable payload release, so the initial implementation
has no older released version to upgrade. Its compatibility matrix consists of fresh v1 migration,
v1 validation/checksum enforcement, newer-version rejection, and byte-stable v1 payload fixtures;
later releases must add every retained predecessor to that matrix before changing this status.
