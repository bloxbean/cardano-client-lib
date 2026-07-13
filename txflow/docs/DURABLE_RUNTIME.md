# Durable TxFlow runtime

`FlowEngine` separates reusable definitions from executions. Applications parse with
`TxFlowCodec`, compile/preflight with `TxFlowCompiler`, and start a `FlowExecutionRequest` with a
unique execution ID. The application supplies transaction services, resource catalogs, policy,
executors, and—when crash recovery is required—a `FlowExecutionStore`.

## Executor ownership

The engine accepts a task `Executor` and an optional maintenance `Executor`. It creates and closes
neither. Java 17 deployments may supply platform-thread executors; Java 21 deployments may supply
virtual-thread executors. Lease renewal, cancellation-aware waits, retry backoff, confirmation
polling, and resource contention remain behind these executor/scheduler boundaries.

## Store contract for database adapters

`InMemoryFlowExecutionStore` is the reference semantics, not a production database. A database
adapter must provide these guarantees in one database transaction where the operation is atomic:

- `createOrGet` uniquely claims `(namespace, idempotencyKey)` and rejects a different definition or
  canonical request fingerprint for an existing claim.
- `append` compares the expected snapshot revision, validates the execution-lease epoch and every
  resource-lease epoch in `MutationFence`, binds every lease and event to the target execution
  (and every resource lease to the active owner), appends events with strictly increasing sequence
  values, and updates the snapshot atomically.
- Lease acquisition mints a monotonically increasing epoch. Renewal retains that epoch; expiry and
  release never allow the old owner to write again.
- Resource leases are uniquely held by canonical resource identity and acquired in sorted order.
- `readEvents` uses an exclusive sequence cursor and reports `EVENTS_COMPACTED` when the cursor is
  strictly before `compactedThroughSequence`; a cursor equal to the inclusive watermark safely
  requests the first retained event after it.
- Compaction is allowed only for terminal executions and atomically advances the watermark.
- Signed payload bytes or an external payload reference, SHA-256, Cardano transaction hash,
  validity bounds, and spent-input identities are durable before `SUBMITTING` is recorded.

Recommended relational constraints are unique keys on `(namespace, idempotency_key)`,
`execution_id`, `(execution_id, sequence)`, and `resource_id`. Revision and lease-epoch predicates
must be part of the same update statement that mutates the execution; a prior read followed by an
unconditional update is not sufficient fencing.

## Recovery ordering

Recovery acquires the execution lease and all recorded spending-resource leases before mutation.
It verifies persisted payload hashes in CCL, observes the prepared transaction hash, checks the
recorded validity window, and can only resubmit identical signed CBOR while the outcome remains
uncertain. It never rebuilds a different body merely because an index lookup is empty. A
`RECOVERY_REQUIRED` result leaves the attempt history and partial-success state available for a
later operator- or policy-directed recovery.

Store fencing protects TxFlow state writes. It cannot prevent a partitioned stale process from
submitting already-signed bytes to Cardano; multi-process deployments that require stronger
guarantees must also serialize spending externally or implement a UTXO reservation coordinator.
