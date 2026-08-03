# JMT Security, Performance, and Cardano Suitability Audit

**Scope:** `jellyfish-merkle`, `jellyfish-merkle-rocksdb`, `jellyfish-merkle-rdbms`,
`rdbms-core`, `rocksdb-core`, qualification tests, and operator tooling

**Review date:** 2026-07-19

**Review type:** adversarial source review, regression construction, backend lifecycle testing,
fuzzing, and independent-vector verification

**Status:** conditionally production-ready for the bounded off-chain profiles in ADR-002

This is a third-party-style engineering review performed during implementation. It is not a
commissioned independent cryptographic certification and must not be represented as one.

## Executive verdict

No known proof-forgery or persistent-state corruption issue remains after the remediation and
qualification pass. RocksDB and PostgreSQL are suitable for serialized, off-chain Cardano state
when all deployment conditions in [ADR-002](../adr/002-production-readiness-gates.md) are met.

The word "production-ready" is deliberately narrow:

- one logical writer per tree namespace, enforced with fail-fast leases;
- committed-version reads may overlap copy-on-write updates, while prune/rollback is exclusive;
- the durable RocksDB production option profile, or PostgreSQL with its advisory-lock provider and
  a connection pool of at least two connections;
- an application-authenticated mapping from JMT version/root to Cardano chain point;
- no native key deletion and no Cardano on-chain proof verification.

High-value authorization remains conditional on an independent specialist review of the exact
v1 commitment/proof profile. H2 and SQLite remain development/test backends.

## Threat model

The verifier may receive attacker-controlled keys, values, object proofs, and CBOR proof bytes.
The expected root must come from an authenticated source. Accidental crashes, retries, concurrent
calls, pruning, chain rollback, partial restoration, and process restart are in scope. A malicious
root publisher, process compromise, database administrator compromise, and cryptanalysis of
Blake2b-256 are outside the library threat model.

## Security review

### Closed critical/high findings

| Severity | Finding | Resolution |
| --- | --- | --- |
| Critical | Leaf commitments did not bind the key hash, allowing proof substitution. | The v1 leaf commitment is `H(0x00 || keyHash || valueHash)`; object and wire verifiers derive and bind the queried key. |
| Critical | A wire non-inclusion proof could be presented as inclusion. | Inclusion requires a terminal leaf matching both the queried key and supplied value. |
| Critical | Replaying a committed version could stale its own live nodes; a later prune physically deleted them. | Replay reads are clamped to pre-state, only the latest version may be replayed, write/stale overlap is rejected, and every backend makes a committed latest-version raw batch a whole-batch no-op. Genesis/latest replay-plus-prune and raw-SPI regressions are permanent tests. |
| High | Missing or damaged child storage could be replaced with one new leaf, silently dropping the rest of a subtree. | A bitmap-present child must exist and recompute to the exact parent commitment before mutation continues. |
| High | Two writers could calculate from the same base and both reach persistence. | `UPDATE` covers base validation, calculation, and commit; competing writers fail fast. PostgreSQL also arbitrates across processes. |
| High | RocksDB prune/rollback prefix scans could omit records. | Namespace-bounded total-order scans and differential/backend regressions cover multi-key histories. |
| High | Persistent stores could be opened under incompatible commitment or rollback-index assumptions. | Mandatory v1 format and feature metadata fail closed before a non-empty namespace is exposed. |
| High | A prune followed by rollback below the retained horizon could expose an incomplete tree. | The prune watermark is persisted atomically; old roots are removed and unsafe rollback is rejected. |

Additional hardening includes:

- duplicate byte-equivalent keys in one batch are rejected and public key/value arrays are copied;
- every commit requires a correctly sized root; immutable-version/latest-only replay rules are
  enforced at both tree and raw-store boundaries;
- RDBMS publication uses a transactional compare-and-set on the observed latest root, so separate
  store instances calculating from one base cannot both commit;
- malformed/non-canonical varints and `NodeKey` encodings fail closed;
- node/proof CBOR requires canonical lengths and has bounded depth, collection sizes, byte lengths,
  and total wire size;
- stable wire verification rejects extension nodes and uncommitted compressed-path metadata that
  the tree never emits;
- negative/future maintenance horizons and unsupported range SPI calls fail loudly;
- RocksDB WAL/sync-invalid combinations are rejected, production mutations are synced, and native
  block-cache/Bloom-filter resources follow database/options lifetime ordering;
- RDBMS commit, auto-commit/isolation restoration, table-prefix keys, builder ownership, and
  historical-value behavior have explicit regression coverage.

### Concurrency model

A namespace has one ordered history. Therefore, concurrent proof readers are supported, but
concurrent writers are rejected rather than queued. The access matrix is:

| Requested operation | May overlap |
| --- | --- |
| `READ` | other reads and one copy-on-write update |
| `UPDATE` | reads, but no other update or maintenance |
| `MAINTENANCE` | nothing |

Compatibility applies between independent operations. Same-thread cross-mode nesting is rejected
to avoid lock upgrades and PostgreSQL connection-pool exhaustion; same-mode store calls are
reentrant.

RocksDB normally has one process owner because of its directory lock. Wrappers around an externally
owned handle must share an explicit coordinator. PostgreSQL uses namespace-scoped transaction
advisory locks so separate JVMs have the same access semantics. H2/SQLite fail-fast leases are
process-local, but transactional latest-root compare-and-set still prevents two cross-instance
commits from the same base from both publishing. Cross-process maintenance exclusion remains a
PostgreSQL-only qualification.

### Parser and proof assurance

The Java golden-vector suite pins roots, nodes, inclusion proofs, non-inclusion proofs, and invalid
cases for `classic-radix16-blake2b256-v1`. A separate Python verifier consumes the vectors without
calling the Java commitment or encoding implementation. Coverage-guided targets exercise
`NodeKey`, node CBOR, wire decoding/verification, and in-memory/RocksDB differential traces.

This combination is useful defense against implementation drift, but it is not formal verification.
The custom radix-16 scheme is not commitment-compatible or wire-compatible with Diem/Aptos JMT.

## Residual security and usage constraints

### Independent cryptographic review

The current review found no remaining known forgery, but the commitment and proof formats are
custom. If a root can release funds, authorize consensus-critical state, or protect similarly high
value, commission an independent cryptographic review of domain separation, inclusion and
non-inclusion rules, malleability, parser limits, rollback semantics, and the published vectors.

### Root authentication

A valid proof only establishes consistency with the supplied root. The library does not prove who
published that root or whether a transition was authorized. The application must persist and
authenticate `{chain point, JMT version, JMT root}` atomically with its chain-sync checkpoint or
otherwise protect it with the relevant trust/consensus mechanism.

### No native deletion

The tree API supports insert/update, not deletion. A tombstone value proves inclusion of that
tombstone; it is not a cryptographic non-membership proof. This is a material limitation for a UTxO
set or any index whose semantic contract requires spent entries to become absent.

### Retention boundary

Pruning intentionally makes roots below the horizon unavailable. Rollback below the persisted
prune watermark fails. Operators must retain more history than the maximum Cardano rollback and
operational recovery horizon, and must restore/re-sync rather than force a deeper rollback.

### Replay contract

Retrying the latest committed version with identical input is supported for crash recovery.
Replaying an older committed version or changing a committed version's root is rejected. Callers
must rollback first before constructing a different future.

## Performance assessment

### Strengths

- Copy-on-write updates persist changed paths while retaining immutable version history.
- RocksDB commits nodes, values, roots, stale records, metadata, and rollback indexes in one synced
  `WriteBatch` under the production profile.
- RDBMS commits are batched and transactional; PostgreSQL locking is fail-fast rather than a hidden
  indefinite wait.
- Values are stored separately, so trusted point reads avoid proof construction.
- The wire proof omits empty child slots and all operator scans are explicitly bounded or selectable.

### Costs and likely bottlenecks

- Each internal commitment hashes a fixed `1 + 2 + 16 * 32 = 515` byte preimage, even for a sparse
  branch. This favors simple deterministic verification over minimum CPU cost.
- Object proof construction expands each visited branch to sixteen slots and currently performs
  neighbor-node reads for metadata the verifiers do not require. Removing or lazily calculating
  that metadata is the clearest proof-latency optimization.
- RDBMS proof traversal performs multiple point queries. Network round trips will dominate for
  deep paths; connection and statement metrics should be measured on the deployment topology.
- `FULL` integrity checking materializes a bounded inspection snapshot and recomputes retained
  roots. Schedule it off the ingestion hot path and set `maxRecords` to a safe operational bound.
- Pruning is proportional to retained history and can build a large atomic backend batch. Use
  conservative retention windows and measure memory, compaction pressure, and tail latency on the
  real dataset.
- Long common prefixes produce paths up to 64 nibbles; deterministic depth-63/64 regressions exist,
  but production capacity planning should still use the application's real key distribution.

Timing assertions were removed from unit tests. JMH and load tools now pass raw keys to proof APIs
instead of accidentally double-hashing them. The repository makes no fixed throughput claim;
publish hardware, dataset, batch size, history, durability options, and latency percentiles with
any benchmark result.

## Cardano suitability

### Qualified fit: off-chain versioned state/index services

The design is a reasonable fit for accounts, snapshots, registries, or append/update-oriented
indexes when a 32-byte Blake2b root is anchored separately. A deployment should:

1. Use one namespace and one serialized writer per logical state tree.
2. Use an internal monotonic apply sequence as the JMT version; store Cardano block hash, slot, and
   block number separately because chain rollback makes those values unsuitable as a simple
   ever-increasing version.
3. Use `RocksDbJmtStore.Options.production()` or qualified PostgreSQL DDL/pooling.
4. Commit the chain point and root association in the application's recovery protocol.
5. Retain state beyond rollback/finality and backup-recovery horizons before pruning.
6. Run a startup `QUICK` check and scheduled/offline `FULL` checks, alerting on any issue.

### Not qualified: Cardano on-chain proof verification

There is no Aiken/Plutus verifier, PlutusData codec, validator cost model, or transaction-size and
execution-unit qualification. The generic nested CBOR proof is not the `Data` representation a
validator naturally consumes, and the 515-byte branch preimage has non-trivial per-level cost.

Use the repository's MPF/Aiken path when on-chain insert/update/delete proofs are required, or treat
a JMT on-chain verifier as a separate protocol project with its own vectors, cost benchmarks, and
independent audit.

## Qualification evidence

- deterministic core access/conflict, replay, corruption, deep-prefix, proof-soundness, and
  integrity-checker tests;
- in-memory, RocksDB, H2, SQLite, and PostgreSQL backend/property suites;
- external-handle RocksDB coordination and cross-connection PostgreSQL contention tests;
- child-JVM abrupt termination during commit and prune/truncate races followed by reopen checks;
- Java/Python golden-vector agreement;
- bounded Jazzer parser/proof campaigns and RocksDB differential operation traces;
- mandatory PostgreSQL, vector, and fuzz jobs in CI.

See [ADR-002](../adr/002-production-readiness-gates.md) for exact deployment gates and checklists.
