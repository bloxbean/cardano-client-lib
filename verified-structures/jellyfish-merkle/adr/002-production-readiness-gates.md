# ADR-002: Production Readiness Gates and Single-Writer Coordination

**Status:** Proposed
**Date:** 2026-07-19
**Modules:** `jellyfish-merkle`, `jellyfish-merkle-rocksdb`, `jellyfish-merkle-rdbms`
**Related:** [ADR-001](001-jmt-readiness-review.md),
[security and performance audit](../docs/security-performance-audit.md)

## Context

The proof-soundness and persistence defects recorded in ADR-001 have been fixed. There are no
pre-release databases or applications that require migration, so the corrected commitment and
storage formats can become the first supported format.

The remaining production risks are operational rather than a known break in the core proof
algorithm:

- A versioned authenticated tree has one logical history. Two writers must not derive competing
  versions from the same root and both publish them.
- A RocksDB `WriteBatch` is atomic, but the current sequence of reading the latest root, building a
  new tree, checking for a divergent version, and writing the batch is not one conditional storage
  operation.
- The public API does not prevent two threads or two tree objects that share a store from writing
  concurrently.
- A persistent store has no format marker. Future incompatible changes could therefore open an old
  store without a precise compatibility error.
- Current tests do not exercise process termination during persistence, deliberate concurrent
  mutation, malformed input fuzzing, or a continuously-running PostgreSQL backend.
- There is no tool that recomputes roots and reports persistent-store corruption or index drift.
- The custom radix-16 commitment and proof format has not been independently implemented or
  externally reviewed.

The initial production target is a serialized, off-chain Cardano state/index service. On-chain
Plutus or Aiken verification, canonical UTxO deletion/absence semantics, and unrestricted
multi-writer operation are outside this decision.

## Decision

### 1. Production scope

The RocksDB backend may be described as production-ready only for a **single logical writer per
tree namespace**, with durable writes, rollback indexes enabled when rollback is required, and an
application-authenticated association between JMT version/root and Cardano chain point.

Independent namespaces may write concurrently because they represent independent tree histories.
Reads and proof generation may run concurrently against committed versions, subject to the
maintenance exclusion described below.

The following remain unsupported production profiles:

- competing writers for the same namespace;
- network-filesystem sharing of a RocksDB directory;
- use of `disableWalForBatches(true)` or disabled sync settings where committed-state durability is
  required;
- canonical UTxO absence based on logical tombstone values;
- on-chain verification using the current generic CBOR proof codec.

PostgreSQL receives a production-ready label only after its locking and continuously-running CI
gates are complete. H2 and SQLite remain development/test targets unless separately qualified.

### 2. Fail-fast access coordination

Introduce a per-tree-namespace `JmtAccessCoordinator`. It issues scoped, thread-owned leases in
three modes:

| Mode | Operations | Compatible with |
| --- | --- | --- |
| `READ` | Proof generation and integrity traversal | `READ`, `UPDATE` |
| `UPDATE` | Build and commit a new version | `READ` |
| `MAINTENANCE` | Truncate/rollback, prune, repair | none |

Only one `UPDATE` lease may exist. A `MAINTENANCE` lease excludes all other leases. Proof reads are
allowed while a copy-on-write update is built and atomically committed, but not while old nodes may
be deleted or versions removed.

Lease acquisition for mutations is fail-fast. It does not wait indefinitely: an incompatible
active lease causes `JmtConcurrentMutationException`. The exception reports the requested
operation/version and non-sensitive diagnostics about the active operation. Callers may retry with
their own bounded policy.

The `UPDATE` lease starts **before** reading or validating the base root and ends only after the
batch commits or aborts. Locking only `CommitBatch.commit()` is insufficient because two writers
could already have calculated competing roots.

Conceptual usage:

```java
try (JmtAccessLease ignored = coordinator.tryAcquireUpdate("put", version)) {
    validateBaseVersion(version);
    TreeUpdateBatch update = buildUpdate(version, values);
    persistAtomically(update);
}
```

The coordinator is reentrant for the owning thread so a tree-level update can call a guarded store
batch without deadlocking. A lease must be closed by its acquiring thread. Batch abandonment must
release every nested lease.

All supported mutation entry points participate:

- `JellyfishMerkleTree.put` acquires `UPDATE` around validation, calculation, and commit.
- Direct `JmtStore.beginCommit` acquires `UPDATE` for the lifetime of its batch, protecting callers
  that use the store SPI directly.
- `pruneUpTo` and `truncateAfter` acquire `MAINTENANCE`.
- `getProof` and the integrity checker acquire `READ`.
- Simple point reads of already-committed values/roots do not require a lease.

For RocksDB:

- A standalone `RocksDbJmtStore` owns one coordinator for its namespace. RocksDB's directory lock
  additionally prevents a second normal process from opening the same database path.
- Constructors that wrap an externally-owned `RocksDB` handle must require an explicit coordinator
  shared by every store wrapper for the same namespace. Creating an uncoordinated wrapper must fail
  closed or be confined to a clearly marked unsafe/testing API.
- Different namespaces use different coordinators and may update concurrently.
- The commit API carries the expected base version/root and revalidates them while the `UPDATE`
  lease is held. A mismatch fails without writing.
- Plain RocksDB `WriteBatch` does not provide compare-and-swap. Supporting independently
  coordinated writers would require `TransactionDB`/`OptimisticTransactionDB` or another durable
  arbitration mechanism and is deliberately outside this release.

For RDBMS:

- The same in-process coordinator protects threads sharing a store.
- PostgreSQL additionally takes a namespace-scoped database lock for the full update or maintenance
  lease so separate JVMs cannot bypass the local coordinator. The implementation may use a locked
  metadata row with `NOWAIT` semantics or a PostgreSQL advisory lock, but it must be transaction- or
  session-owned and reliably released on connection failure.
- Lock acquisition failure maps to the same fail-fast exception rather than blocking.

### 3. Persistent format descriptor

Every persistent namespace stores a mandatory format descriptor before accepting tree data. A
non-empty namespace without a descriptor fails to open; there is no legacy auto-migration because
the project has no released JMT stores.

At minimum, the descriptor records:

- a magic identifier and storage schema version;
- node and `NodeKey` encoding versions;
- commitment profile identifier and hash output length;
- radix/key-hash length;
- enabled persistence features that change required column families or tables, including rollback
  indexes.

The built-in production profile receives a stable identifier such as
`classic-radix16-blake2b256-v1`. Custom hash or commitment implementations must supply an explicit,
stable profile identifier; class names are not stable format identifiers.

Opening behavior is fail closed:

1. An empty namespace atomically installs the expected descriptor using durable settings.
2. A matching descriptor opens normally.
3. A missing, malformed, newer, older, or otherwise incompatible descriptor throws
   `JmtFormatMismatchException` before reads or writes are exposed.
4. Format upgrades require an explicit migration/rebuild tool and a new descriptor version. They
   are never inferred from existing bytes.

The descriptor is stored in a metadata column family/table rather than overloading user keys. Its
creation and all subsequent upgrades are atomic and idempotent.

### 4. Full-store integrity checking

Add a read-only `JmtIntegrityChecker` with `QUICK` and `FULL` modes. It returns a structured report
and never repairs data implicitly.

`QUICK` verifies:

- format descriptor compatibility;
- latest-version/latest-root pointer consistency;
- root and key/hash lengths;
- presence of rollback indexes when declared by the descriptor;
- basic decoding of retained roots and a configurable sample of nodes.

`FULL` additionally:

- traverses every retained root or a selected version range;
- recomputes every reachable node commitment and root;
- validates node path/depth/version relationships and referenced children;
- hashes stored values and compares them with leaf value commitments;
- checks latest-root monotonicity, stale-node records, rollback indexes, missing references, and
  unreachable/orphaned nodes;
- reports duplicate, malformed, or out-of-namespace records.

Backend inspection must be exposed through a narrow read-only SPI rather than leaking native
RocksDB/JDBC objects into the core checker. The checker supports limits, progress reporting, and
cancellation so an operator can bound production impact. A repair tool, if added later, is a
separate explicit operation requiring a `MAINTENANCE` lease.

### 5. Verification gates

#### Crash and fault injection

Use child JVMs and real persistent stores, not only mocked exceptions. Tests terminate a writer
before commit, during repeated commits, immediately after commit, during prune, and during
truncate. After reopening, the database must be in either the complete old state or complete new
state, never a mixture; the latest pointer and a full integrity check must agree.

For RocksDB, run the campaign with production durability defaults and separately demonstrate that
benchmark-only durability options are excluded from the production profile. Include repeated
forced termination (`Runtime.halt`/OS kill), reopen, rollback, and proof verification.

#### Concurrency

Deterministic barrier-based tests cover:

- two threads attempting the same next version;
- two `JellyfishMerkleTree` objects sharing one store;
- two wrappers around one externally-owned RocksDB handle and namespace;
- proof generation overlapping an update;
- proof generation overlapping prune/truncate;
- update overlapping prune/truncate;
- independent namespaces updating concurrently;
- lease release after exceptions and abandoned batches.

For the same namespace, exactly one competing update succeeds and the other fails before changing
storage. The surviving root must pass a full integrity check.

#### PostgreSQL CI

Add a mandatory PostgreSQL service/job that runs the RDBMS integration suite without environment-
based skips. It covers idempotent replay, divergent replay, rollback, prune, format initialization,
cross-connection lock contention, transaction failure, and pool shutdown. A skipped PostgreSQL
suite fails that CI job.

#### Cross-implementation vectors

Publish versioned golden vectors under `jellyfish-merkle/src/test/resources`. Each vector contains
the profile identifier, raw keys/values, their hashes, ordered operations/versions, expected roots,
inclusion and non-inclusion proofs, encoded nodes/proofs, and deliberately invalid cases.

At least one verifier independent of the Java implementation must consume the vectors and produce
the same results. It must not reuse Java commitment or encoding code. Changing a stable vector
requires a format/profile version change or a documented correction.

#### Fuzzing

Keep state-machine property tests for update/proof/rollback behavior and add coverage-guided fuzz
targets for:

- `NodeKey.fromBytes`;
- node CBOR decoding;
- wire-proof decoding and verification;
- maximum depth, maximum wire size, truncated input, duplicate/unknown fields, and malformed hash
  lengths;
- differential operation traces across in-memory and RocksDB stores.

Fuzz targets must enforce memory and time bounds and preserve every security/crash corpus input as
a regression seed.

### 6. Independent cryptographic review

If JMT roots authorize or protect high-value state, obtain an independent review of the exact
production profile. Its scope includes commitment preimages/domain separation, inclusion and
non-inclusion rules, proof malleability, decoding limits, root trust assumptions, rollback behavior,
and the golden vectors.

This ADR and the existing internal source audit are readiness work, not substitutes for an external
cryptographic assurance report. Findings from an external review block the high-value production
profile until resolved or explicitly accepted.

## Consequences

### Positive

- Concurrent mutation becomes an explicit, observable error instead of a race.
- The lock covers the actual optimistic calculation window, not only persistence.
- Versioned reads can remain concurrent with copy-on-write updates.
- Maintenance cannot delete nodes while a proof or integrity traversal uses them.
- Unsupported shared-handle configurations fail closed.
- Future storage and commitment changes have a deterministic compatibility boundary.
- Operators gain a non-mutating way to verify persisted roots and indexes.
- Production claims become backend- and workload-specific.

### Costs and constraints

- Mutation and embedded-store APIs change before release.
- Callers must handle `JmtConcurrentMutationException` and decide whether/when to retry.
- Long proof or integrity reads can delay maintenance; maintenance remains fail-fast rather than
  waiting without a caller-controlled policy.
- PostgreSQL needs a backend-specific cross-process lock implementation.
- Full integrity scans are I/O intensive and require operational scheduling.
- Golden-vector and fuzz infrastructure adds maintenance work.

## Alternatives considered

### Document single-writer behavior only

Rejected. It leaves a correctness invariant unenforced and allows accidental misuse by otherwise
valid Java code.

### Synchronize only `commit()`

Rejected. Writers can calculate competing roots before either reaches commit, and a read-then-write
divergence check is not compare-and-swap.

### Block until a writer lock is available

Rejected as the library default. Hidden blocking can stall chain-sync or deadlock application lock
orders. Fail-fast acquisition gives the application control over retry, backpressure, and shutdown.

### Use one global lock

Rejected. Independent namespaces do not share history and should retain parallelism.

### Require RocksDB `TransactionDB` immediately

Deferred. It would change native database construction and embedded-handle compatibility. The
supported RocksDB model already has one process opening a directory and can enforce a shared
per-namespace coordinator. Transactional RocksDB remains the path if true independent multi-writer
arbitration becomes a requirement.

### Automatically infer or migrate old formats

Rejected. There are no released stores to preserve, and guessing commitment/encoding details is
unsafe. Explicit format identifiers and explicit future migrations are simpler and safer.

## Implementation plan

### Phase 1 — Coordination and commit preconditions

1. Add `JmtAccessCoordinator`, `JmtAccessLease`, access modes, and
   `JmtConcurrentMutationException` to the core module.
2. Extend `JmtStore` so the tree and all mutating store entry points use the same coordinator.
3. Acquire `UPDATE` at the start of `JellyfishMerkleTree.put`; acquire `READ` in proof generation;
   acquire `MAINTENANCE` in prune/truncate.
4. Carry expected base version/root into commit and revalidate it while the lease is held.
5. Wire one coordinator per RocksDB namespace. Require explicit sharing for externally-owned
   RocksDB handles.
6. Add deterministic concurrency and lease-cleanup tests for core and RocksDB.

**Exit gate:** competing same-namespace writes cannot both reach storage; independent namespaces
still update concurrently; all current suites remain green.

### Phase 2 — Format descriptor

1. Define the stable built-in profile and `JmtFormatDescriptor` encoding.
2. Add metadata persistence to RocksDB and RDBMS schema helpers.
3. Validate/install the descriptor before exposing a persistent store.
4. Add empty, matching, missing, malformed, incompatible, and partial-initialization tests.
5. Document the first released format as version 1.

**Exit gate:** an incompatible or unmarked non-empty namespace cannot be opened.

### Phase 3 — Integrity checker

1. Define the read-only inspection SPI and structured issue/report model.
2. Implement `QUICK`, then `FULL`, for in-memory and RocksDB.
3. Add RDBMS inspection without backend-specific types in the core checker.
4. Test deliberate corruption of roots, nodes, values, indexes, and latest pointers.
5. Add an example/CLI entry point that exits non-zero on integrity failure.

**Exit gate:** deliberate corruption is detected and correctly localized without modifying storage.

### Phase 4 — Crash and concurrency qualification

1. Build reusable child-JVM crash fixtures.
2. Run commit/prune/truncate kill-and-reopen campaigns against RocksDB.
3. Run all concurrency scenarios from this ADR with deterministic barriers.
4. Run full integrity verification after every recovered final state.
5. Add the production RocksDB profile to CI.

**Exit gate:** no tested termination point produces a mixed committed version, bad latest pointer,
or integrity failure.

### Phase 5 — PostgreSQL qualification

1. Implement and test namespace-scoped cross-process fail-fast locking.
2. Add an always-on PostgreSQL CI job and remove skip-as-success behavior from that job.
3. Exercise rollback, replay, failure, pool lifecycle, and format behavior across connections.

**Exit gate:** PostgreSQL tests cannot silently skip and distributed lock contention has one winner.

### Phase 6 — Independent vectors and fuzzing

1. Freeze the v1 golden-vector schema and representative corpus.
2. Implement an independent verifier and run it in CI.
3. Add parser/proof coverage-guided fuzz targets and state-machine differential tests.
4. Promote discovered failures to permanent regression tests.

**Exit gate:** Java and the independent verifier agree on all valid/invalid vectors, and the fuzz
campaign completes its configured CI budget without a crash, unbounded allocation, or false proof.

### Phase 7 — Release review

1. Update production documentation with the supported profile and operational checklist.
2. Review every exit gate and record evidence in a release-readiness document.
3. Commission external cryptographic review when required by the value/risk profile.
4. Mark this ADR accepted only after the applicable backend gates pass.

**Exit gate:** the release documentation states the qualified backend and workload precisely; it
does not imply on-chain, deletion, or multi-writer support.

## Production deployment checklist (RocksDB profile)

- One coordinator per tree namespace; every wrapper uses it.
- Rollback index enabled at initial creation when chain rollback is supported.
- WAL enabled; commit, prune, and truncate sync enabled.
- Local supported filesystem; one RocksDB process owns the database path.
- JMT version/root stored with and authenticated against the Cardano chain point.
- Retention exceeds the application's rollback horizon.
- Prune/truncate scheduled through the maintenance lease.
- Startup `QUICK` integrity check and scheduled/offline `FULL` check.
- Metrics/alerts for lock contention, failed commits, rollback, pruning, and integrity findings.
- Tested backup/restore and crash-recovery procedure.
