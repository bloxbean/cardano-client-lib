# ADR 0007: Durable redelivery and reconciliation for preview

Status: Implemented preview subset of ADR 0006. This does not mark ADR 0006 complete.

## Problem

Retained registrations outlive the stream's live item map. Identical redelivery
therefore used to conflict after eviction or restart, and explicit reconciliation
could report a stored item unknown. Restart handling also called accepted work
without a persisted plan cancelled and recommended redelivery, although its
retained registration prevented that replay.

## Decisions

1. `TxStreamStateStore.registerOrMatch` atomically installs a registration or
   matches its item ID, claim key, lane, and content fingerprint. Acceptance time
   is excluded from equality. Different content throws the existing typed conflict.
   Shipped durable memory stores use one map insertion; H2/PostgreSQL use row locks
   and the database unique constraint. A concurrent insert loser rolls back and
   compares the winner in a fresh transaction. No pre-read decision permits an
   overwrite. Legacy `registerItem` keeps its reject-duplicates contract.
2. Matching never plans or starts a new execution. Terminal matches return a
   settled receipt without adding another acceptance, terminal counter, or live
   retention entry. Nonterminal matches use the persisted plan and its original
   execution identity, then reconcile engine truth. A mismatched stored plan is
   corruption, not permission to rebuild work.
3. `getStoredProjection` pairs the exact projection with its CAS watermark.
   Shipped durable stores read one immutable entry or one database row. The legacy
   fallback retries when a separately read watermark changes; stores without
   watermarks retain the previous conservative sequence floor. Custom stores
   should implement the atomic read before claiming concurrent hydration support.
4. Explicit status reads, `reconcile`, matching redelivery, and the periodic
   observer share read-only hydration using the same reconstruction function as
   restart. Hydration never submits, replaces, or regroups a flow. Snapshot truth
   repairs the projection at a higher sequence; unresolved work settles as
   `RECOVERY_REQUIRED`. Historical terminal reads do not populate the live map.
5. A registration without a recoverable plan is an explicit intervention case.
   Matching submission returns `REJECTED` / `TXSTREAM_REGISTRATION_INCOMPLETE`
   without creating a receipt or incrementing accepted counters. If an accepting
   owner is still progressing, redelivery can be retried after it persists the
   plan. Restart classifies projected incomplete work as `RECOVERY_REQUIRED` /
   `TXSTREAM_ABANDONED`, retains it in the recovery scan, and reports it in
   `ReattachReport.recoveryRequired()`. It does not repeatedly rewrite the row.
   There is no automatic pruning, tombstoning, or replay of incomplete work.

## Scope and upgrade behavior

No database migration is needed: registration fingerprints, projections, and
watermarks already exist. Item registrations remain keyed by item ID within the
store. Applications sharing a database must use globally unique item IDs across
their streams; stream-scoped registration keys require a separate schema migration.

Custom stores remain source compatible through default methods. The default
`registerOrMatch` delegates to `registerItem`, retaining legacy duplicate rejection
until the adapter explicitly implements matching. Do not infer the new guarantees
for an unqualified third-party adapter.

Existing terminal cancelled/abandoned rows are not rewritten or replayed on
upgrade. Identical redelivery attaches to their cancelled result. Operators must
inspect the source journal, registration, persisted plan/binding, and original
engine claim before deciding how to recover incomplete work. A new business ID is
not an automatic recovery strategy.

## Qualification

- Shared H2, PostgreSQL, and durable-memory contracts: concurrent matching,
  projection-only registration races, different-content conflicts, timestamp
  exclusion, and preservation of projection/watermark.
- Stream tests: redelivery after eviction and restart, no counter inflation or
  extra engine start, remote uncertainty hydration and repair, and incomplete
  registration refusal.
- H2 runtime tests: actual TxFlowStream with both relational stores, attachment
  after database reopen, and store-only repair from an engine snapshot with a
  dominating persisted projection sequence.
- Existing ownership, shared-flow, template, reconciliation, restart, and abrupt
  H2 process-termination suites remain applicable.

Full registration/binding/plan aggregate versioning, automatic pre-plan recovery,
active/active funding coordination, retention/pruning policy, and sustained
public-network failover qualification remain outside this preview subset.

### Review corrections

Healthy foreign-item reads are observations. They do not install live items or
advance the owner's projection sequence, even when the engine snapshot is absent.
Explicit foreign receipt attachment retains a local observation; poll `reconcile`
or enable the observer to refresh it. Stored recovery-required items may still be
hydrated and terminally repaired from engine truth. Observation never infers
recovery authority from a missing snapshot.

The shipped stores resolve a plan through the item's binding. The observer pages
nonterminal IDs with a cursor and charges every inspected row against its budget,
including live or abandoned rows. This keeps a pass bounded and lets later rows
progress; restart still inventories all unresolved work.

After fencing/stopping all producers and recovery workers and investigating an
abandoned registration, an operator can call
`store.acknowledgeAbandoned(streamId, itemId, expectedSequence, reason, acknowledgedAt)`.
It atomically checks the exact stored sequence and RECOVERY_REQUIRED/TXSTREAM_ABANDONED
state, marks the bookkeeping FAILED, and retains registration, binding, and hash.
A stale or already-resolved row returns false. This acknowledgement is not proof
that a transaction failed and is not permission to submit a replacement payment.
Custom stores must implement this atomic operation explicitly; it is unsupported
by default. The paging and targeted-lookup SPI methods have compatibility fallbacks.

Registration matching includes the lane name. Changing lane assignment across
deployments can therefore conflict even when the transaction payload is unchanged.
No-binding observations do not authorize automatic same-ID reacceptance: another
owner may still have accepted work queued before its write-ahead binding.
