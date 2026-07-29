# TxFlow RDBMS store

`cardano-client-txflow-store-rdbms` is the optional JDBC implementation of
`FlowExecutionStore`. The core `txflow` artifact remains independent of JDBC drivers.

## Embedded H2

H2 is a runtime dependency of this extension. URL configuration uses non-pooling
`DriverManager` connections and defaults to schema migration. Unless the URL explicitly selects
another H2 `WRITE_DELAY`, the builder adds `WRITE_DELAY=0` so a committed transaction is flushed
for the restart-durable embedded profile:

```java
try (RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .jdbcUrl("jdbc:h2:file:./data/txflow")
        .build()) {
    // Configure FlowEngine with store.
}
```

The dialect requires H2 major version 2 or later; H2 1.x is rejected at startup. File-backed H2 is
intended for single-JVM restart durability. H2 `AUTO_SERVER`, network-server, and shared-file
multi-process deployments are not certified by this module. Explicitly choosing a non-zero
`WRITE_DELAY` trades this abrupt-restart guarantee for write throughput.

The `WRITE_DELAY=0` default is applied only to builder-managed JDBC URLs. An application-supplied
H2 `DataSource` owns its URL and must configure its required durability itself. Prefer an orderly
store close during normal shutdown even though the integration suite also verifies forcible
child-JVM termination. Protect the database files with application-appropriate filesystem
permissions, and configure H2 encryption through H2 when encryption at rest is required.

Because H2 can commit DDL between migration statements, `MIGRATE` recognizes one narrow
first-install recovery case: a compatible empty `txflow_schema_history` marker with the expected
columns and primary key, plus an empty creation-order prefix of V1 tables. It drops that
verified-empty prefix in reverse order and recreates the canonical checksummed schema, so extra
indexes, defaults, or constraints on the partial tables are discarded. Missing markers,
unexpected TxFlow tables, non-empty state, and every incompatible marker shape remain fail-closed;
the store never adopts application data as an interrupted migration.

The packaged `db/txflow/<dialect>/V1__txflow_store.sql` files are inputs to TxFlow's internal
schema manager, not Flyway migrations. They use a familiar filename only for human-readable
ordering and are tracked in the dedicated `txflow_schema_history` table, so an application's own
Flyway `V1__...` migration does not conflict. Do not add the TxFlow resource directory to the
application's Flyway locations.

## Application-managed databases

`DataSource` is the primary production boundary. It must provide ordinary local-transaction JDBC
connections: the store disables auto-commit and calls `commit` or `rollback` for every operation.
Do not supply a connection enlisted in a JTA or other ambient transaction. The application retains
ownership of its pool and JDBC driver:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .dataSource(dataSource)
        .dialect(PostgresDialect.INSTANCE)
        .schemaManagement(SchemaManagement.VALIDATE)
        .build();
```

Application-supplied data sources default to `VALIDATE`; choosing `MIGRATE` is explicit.
The store closes each borrowed connection but never closes the `DataSource` or pool.

For command-line tools and small services, URL mode also accepts separate credentials and an
optional driver class:

```java
RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
        .jdbcUrl(jdbcUrl)
        .username(jdbcUsername)
        .password(jdbcPassword)
        // JDBC 4 discovery normally makes this unnecessary.
        // .driverClassName("org.postgresql.Driver")
        .dialect(PostgresDialect.INSTANCE)
        .schemaManagement(SchemaManagement.VALIDATE)
        .build();
```

Configure exactly one of a URL or `DataSource`. URL credentials and `driverClassName` cannot be
combined with a `DataSource`, and a password requires a username. URL mode is deliberately
non-pooling; prefer an application-managed pool for normal PostgreSQL services.

The PostgreSQL JDBC driver is deliberately not a runtime dependency of this artifact. Applications
must put a driver compatible with their server and pool on their own runtime classpath. Keep
credentials in the `DataSource` or the builder's separate username/password values instead of a
logged or persisted JDBC URL, and configure TLS and credential rotation through the driver or pool.

The current automated PostgreSQL certification profile is PostgreSQL 17.x; the integration suite
currently runs against PostgreSQL 17.6. The dialect may be SQL-compatible with other maintained
PostgreSQL versions, but this release does not claim them as tested. Qualify any other major version
with the complete published store contract and database integration suite before production use.
A custom JDBC database likewise needs an explicit `TxFlowSqlDialect`, compatible schema, and that
complete verification suite.

### Schema ownership and least privilege

Use a dedicated migration/schema-owner role for `SchemaManagement.MIGRATE` in shared deployments.
It needs `USAGE` and `CREATE` on the selected schema and must own the TxFlow objects so future
migrations can alter them. Pin the connection's current schema/search path to that schema; TxFlow
does not search across application schemas.

After migration, an application using `SchemaManagement.VALIDATE` can use a narrower runtime role.
For PostgreSQL, the following is the required shape; replace `application_schema` and role names
with deployment values:

```sql
GRANT USAGE ON SCHEMA application_schema TO txflow_runtime;

GRANT SELECT, INSERT, UPDATE
    ON application_schema.txflow_execution TO txflow_runtime;
GRANT SELECT, INSERT
    ON application_schema.txflow_idempotency TO txflow_runtime;
GRANT SELECT, INSERT, DELETE
    ON application_schema.txflow_event TO txflow_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON application_schema.txflow_execution_lease,
       application_schema.txflow_resource_lease TO txflow_runtime;
GRANT SELECT, UPDATE
    ON application_schema.txflow_lease_epoch TO txflow_runtime;
GRANT SELECT
    ON application_schema.txflow_schema_history TO txflow_runtime;
```

Do not grant destructive DDL to the runtime role. PostgreSQL advisory migration locks require no
additional table grant. Reassess these grants when a later schema migration adds an object or an
operation.

## Guarantees and limits

- Idempotency claims, snapshot revisions, journal appends, fences, and compaction are committed in
  atomic JDBC transactions.
- Snapshot and event payloads use the closed, versioned `FlowStoreCodec`; Java serialization and
  polymorphic default typing are not used.
- Startup validation is scoped to the connection's effective schema and checks required columns,
  primary/foreign keys, and indexes. Metadata rows are exact-matched to the active catalog,
  schema, table, and runtime-resolvable unquoted identifiers; similarly named objects cannot mask
  drift. Foreign keys must remain inside that catalog and schema, and required named indexes must
  remain non-unique, complete, and usable. Instant columns must retain dialect-appropriate
  time-zone semantics and at least microsecond precision, payload columns must hold at least the
  codec's 16 MiB boundary, and identity text cannot use fixed-width or explicitly
  case-insensitive types. PostgreSQL additionally requires UTF-8 server encoding and rejects
  nondeterministic column collations or partial/invalid required indexes through its system
  catalog. Migration refuses to adopt pre-existing `txflow_*` objects that have no compatible
  history. Generic JDBC metadata does not certify every database-wide locale/collation setting,
  so other dialect deployments must retain deterministic, case-sensitive text equality
  consistent with Java `String.equals`.
- Persisted identities reject NUL, malformed Unicode, and values beyond the portable UTF-8 byte
  limits (255 bytes for namespaces, 512 for keys/executions/owners/steps, 1024 for resources, and
  256 for transaction hashes). These bounds also keep PostgreSQL indexed values below its B-tree
  key limit for multibyte input.
- Lease epochs are monotonically allocated and stale execution or resource owners cannot mutate
  durable state. Append samples expiry only after locking the complete mutation fence. Stored
  timestamps use microsecond precision; lease expiry is rounded upward when necessary so even a
  positive sub-microsecond requested duration remains strictly later than its acquisition time.
- The store is synchronous and creates no executor, scheduler, worker thread, or connection pool.
- Closing the store rejects new operations, closes only its store-owned H2 anchor connection,
  and never closes an application-supplied `DataSource`.
- `TXFLOW_STORE_COMMIT_UNCERTAIN` means the database may have committed even though JDBC could not
  report the result. `TXFLOW_STORE_ROLLBACK_UNCERTAIN` means JDBC could not confirm that a failed
  operation was rolled back. Treat either outcome as unknown and do not retry it automatically.
- For execution-store operations, reconcile the execution ID, idempotency claim, durable snapshot,
  and Cardano chain state before resuming or submitting transaction bytes. For schema management,
  inspect schema history, database objects, and database logs, then rerun `VALIDATE` after database
  connectivity is stable; do not rerun `MIGRATE` until the migration outcome is established.
- JDBC causes exposed by the adapter retain SQL state and vendor code but omit driver messages,
  which can contain credential-bearing URLs. Database logs remain the detailed diagnostic source.
- Database fencing cannot prevent a partitioned worker from submitting transaction bytes that
  were already signed. Active-active spending still requires external serialization or UTxO
  reservation.

## Backup, restore, and retention

Back up the TxFlow tables as one transactionally consistent unit. Snapshots, claims, journals,
leases, the global lease epoch, payloads, and schema history are one recovery boundary; restoring
only a subset can invalidate idempotency or fencing assumptions.

- For PostgreSQL, use the platform's consistent backup/snapshot tooling and include every
  `txflow_*` table plus schema history. Test restore into an isolated database before relying on it.
- For file-backed H2, take a copy only after closing the store/database or use H2's supported online
  backup facility. Copying live database files is not a supported backup procedure.
- After restoring an older backup, reconcile non-terminal and submitted executions with Cardano
  chain state before enabling execution. A database backup cannot roll back an already submitted
  Cardano transaction.
- Use `FlowExecutionStore.compactEvents` for supported terminal-journal retention. Do not manually
  delete claims, executions, events, leases, or the lease-epoch row.

Monitor pool acquisition/transaction latency, database availability, serialization/deadlock
errors, `TXFLOW_STORE_COMMIT_UNCERTAIN`, `TXFLOW_STORE_ROLLBACK_UNCERTAIN`, and executions entering
recovery-required state. The store does not create a metrics registry or background monitoring
thread.

Run the H2 contract, H2 forced-restart layer, and PostgreSQL Testcontainers layer on Java 17 with:

```shell
./gradlew :txflow-extensions:txflow-store-rdbms:test
./gradlew :txflow-extensions:txflow-store-rdbms:integrationTest
```

The PostgreSQL integration layer requires Docker and fails when Docker is unavailable; it is not
silently skipped. Release-certification CI must therefore provide Docker.
