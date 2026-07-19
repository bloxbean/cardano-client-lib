# JMT Security, Performance, and Cardano Suitability Audit

**Scope:** `jellyfish-merkle`, `jellyfish-merkle-rocksdb`, `jellyfish-merkle-rdbms`, and shared persistence code
**Review date:** 2026-07-19
**Review type:** independent source review, adversarial tests, property tests, and backend lifecycle tests
**Status:** experimental; not approved for production or on-chain verification

## Executive verdict

The remediation pass closes the confirmed proof-forgery defect and the confirmed RocksDB
prune/rollback defects. Inclusion and non-inclusion proofs now bind the full key hash, malformed
wire proofs are bounded and fail closed, and all three backends agree on the tested sequential
workloads.

The implementation should nevertheless remain **experimental**. The most important release
blockers are operational rather than another known proof forgery: the new leaf commitment is
incompatible with every tree created by the previous code and there is no persisted commitment
scheme identifier; writes require a single external coordinator; PostgreSQL is not exercised by
the default test run; and there is no Cardano/Aiken proof verifier or cross-language golden-vector
suite.

This is a custom, Diem-inspired radix-16 authenticated tree. It is not wire-compatible or
commitment-compatible with Aptos/Diem JMT. Documentation and API consumers must not assume that a
proof from one implementation can be verified by the other.

## Threat model

The proof verifier is assumed to receive attacker-controlled keys, values, object proofs, and CBOR
proof bytes. The expected root must come from an authenticated source. Storage is assumed not to
be malicious, but crashes, retries, pruning, chain rollback, and process restart are in scope.
Compromise of the process, hash-function cryptanalysis, database administrator attacks, and a
malicious root publisher are out of scope.

## Findings

### Closed by this remediation

| Severity | Finding | Resolution |
|---|---|---|
| Critical | Leaf commitments omitted the key hash, permitting forged inclusion and non-inclusion proofs. | Leaf is now `H(0x00 || keyHash || valueHash)` and both verifiers derive the queried key hash themselves. |
| Critical | Wire non-inclusion proofs could be presented as inclusion proofs. | Inclusion now requires a terminal leaf matching both queried key and supplied value. |
| Critical | RocksDB prefix iterators truncated prune and rollback scans. | Maintenance uses namespace-bounded total-order scans; regression tests cover multiple keys and versions. |
| High | RDBMS had no rollback and replay was not idempotent. | Transactional `truncateAfter`, immutable-root replay checks, and batched insert-or-ignore writes were added. |
| High | In-memory abandoned batches committed staged writes. | Closing without `commit()` now discards the batch. |
| High | Gapped versions marked a synthetic, nonexistent root version stale. | `TreeCache` resolves and stales the actual persisted root node key. |
| Medium | In-memory divergent replay differed from persistent stores. | It now rejects a different root before mutating staged nodes or values. |
| Medium | `truncateAfter(Long.MAX_VALUE)` overflowed in memory and erased history. | Exclusive `NavigableMap.tailMap(version, false)` ranges avoid arithmetic overflow. |
| Medium | Proof/node CBOR accepted trailing items and wire proofs had no input bound. | Decoders require one top-level item; proof bytes are capped at 1 MiB and path nodes at digest-nibble depth plus a terminal leaf. |
| Medium | Hash-size assumptions failed late and differently by backend. | Tree and persistent value stores now require the documented 32-byte key hash. |

### Open release blockers and residual risks

#### High — no safe migration from the old commitment

Changing the leaf commitment changes every non-empty root. Continuing an old database creates a
hybrid tree: untouched child hashes use the old leaf rule while updated paths use the new rule.
Proofs for untouched keys can then fail against newly committed roots.

**Required release action:** use new/empty column families or tables and rebuild from authoritative
state. Before a stable release, persist a format identifier containing at least the node encoding,
commitment scheme, hash algorithm, and version. Opening a non-empty store with an unknown or
mismatched identifier should fail.

#### High — writes are single-writer only

`put()` is a read/compute/commit sequence, not one storage transaction. Two tree instances can read
the same base root and commit different later versions. RDBMS connection pooling and RocksDB's
thread-safe API do not make the tree update protocol safe for concurrent writers. Prune and
truncate must also not race with reads or writes.

**Required usage rule:** one writer/coordinator per namespace; serialize commit, prune, and rollback.
For multi-process RDBMS deployments, add an advisory/row lock or compare-and-set on the expected
latest version inside the commit transaction.

#### Medium — roots and transitions are not authenticated by this library

A valid proof says only that a claim matches a supplied root. It does not establish who published
the root or that a new root is an authorized transition from the old one. Applications must bind
roots to a signed checkpoint, consensus result, or validator-controlled Cardano UTxO datum.

#### Medium — deletion is not implemented by the tree API

The persistence SPI contains tombstones, but `JellyfishMerkleTree` supports only insert/update.
This prevents native non-membership after deleting an account or spent UTxO. Encoding a logical
tombstone as a value proves inclusion of the tombstone, not cryptographic non-inclusion.

#### Medium — rollback is opt-in for RocksDB

`RocksDbJmtStore.Options.enableRollbackIndex` defaults to `false`. A Cardano chain follower that
must handle rollbacks needs it enabled from database creation. Enabling it later does not backfill
old index entries.

#### Medium — floor/ceiling SPI is not portable

The persisted `NodeKey` encoding groups paths by encoded length, while logical ordering compares
nibble content first. RocksDB range implementations cannot provide the documented logical
floor/ceiling behavior for mixed-depth paths. The tree core does not use these methods; they should
be removed from the JMT SPI or implemented through a separate logical index before public use.

#### Medium — no database corruption/integrity envelope

Node and root records are not checksummed beyond RocksDB's internal protection or database storage
guarantees. A damaged node is often detected only when decoding or when a generated proof fails.
There is no full-store root audit command. Add an offline verifier that walks each retained root,
recomputes commitments, checks key/path consistency, and verifies value hashes.

#### Low — verification gaps

The default suite does not run PostgreSQL, there are no multi-process concurrency tests, no
cross-language golden vectors, and no long-running crash/fault-injection campaign. Property tests
cover small trees; deliberately crafted long common-prefix keys and million-key stores remain
important coverage targets.

## Performance assessment

### Strengths

- Copy-on-write updates persist only changed paths and keep immutable historical nodes.
- RocksDB commits nodes, values, roots, stale markers, and rollback indexes in one synced
  `WriteBatch` by default.
- RDBMS writes are batched and transactional.
- Direct values are stored separately, making trusted value lookup cheaper than proof traversal.
- Namespace-bounded maintenance avoids scanning unrelated RocksDB namespaces.

### Costs and bottlenecks

- Each branch commitment hashes a fixed `1 + 2 + 16 * 32 = 515` byte preimage. Even a branch with
  one child pays for all sixteen slots. This is not Aptos/Diem's binary sparse-Merkle commitment.
- The object proof allocates a 16-slot matrix at every level. The CBOR wire format omits null slots,
  so its size depends on populated children, but verification expands every branch and hashes all
  sixteen slots.
- Proof generation performs extra neighbor-node reads to populate `BranchStep` metadata that the
  current object and wire verifiers do not consume. Removing or lazily computing this metadata is
  a worthwhile read-latency optimization after API compatibility is decided.
- RDBMS proof generation opens independent connections/statements for node reads. It has no
  snapshot transaction spanning root lookup, node traversal, and value lookup. Immutable versions
  limit correctness risk, but round trips dominate latency.
- Pruning is linear in retained namespace history. Run it in bounded background windows and expose
  scanned/deleted counts and latency; do not run it synchronously on a block-ingestion hot path.
- Claims such as “2M ops/sec”, fixed proof speedups, or O(1) database reads are not supported by a
  reproducible benchmark in this repository. Real complexity includes LSM/SQL lookup and history
  depth. Publish JMH/load-test hardware, dataset shape, durability settings, and percentiles before
  making throughput claims.

## Cardano suitability

### Good fit: off-chain, versioned state with an anchored root

Blake2b-256 matches an available Aiken/Plutus hash primitive, and a 32-byte root is inexpensive to
place in a datum or checkpoint. A practical off-chain deployment should:

1. Use one namespace and one serialized writer per logical state tree.
2. Map JMT versions to a local monotonically increasing apply sequence; separately persist Cardano
   block hash, slot, and block number for rollback lookup.
3. Enable RocksDB rollback indexes at database creation, or use the transactional RDBMS rollback.
4. Retain nodes and values beyond the application's rollback/security horizon before pruning.
5. Authenticate the root and its update authority; never trust a root supplied alongside its proof.
6. Rebuild into a new namespace for this commitment change.

The missing delete operation is a serious limitation for a UTxO set or any Cardano index whose
entries disappear. The current API is better suited to append/update-oriented snapshots unless
logical tombstones are acceptable to the application.

### Poor fit today: Cardano on-chain proof verification

There is no Aiken implementation, PlutusData proof codec, validator, cost benchmark, or golden
vector suite. The shipped wire format is generic CBOR containing nested CBOR byte strings, not the
`Data` shape a validator naturally receives. The 515-byte branch preimage also makes each proof
level more expensive than a commitment designed for on-chain execution.

If on-chain membership, absence, insert, update, or delete is the goal, the repository's MPF module
is the safer starting point. The Aiken MPF package already defines 32-byte roots and proof-driven
operations. Cardano transaction size and execution-unit limits are protocol parameters, so any new
JMT validator must be measured against the target network rather than assumed to fit.

## Recommended release plan

1. Keep all JMT artifacts marked experimental.
2. Add and enforce a persisted format/commitment identifier; require rebuild from the old format.
3. Define a single-writer protocol and add storage-level compare-and-set/locking.
4. Decide whether deletion is a required feature; implement and audit it as one coherent change.
5. Remove/fix floor/ceiling and remove unused proof-neighbor metadata or make it lazy.
6. Run PostgreSQL in CI plus concurrency, crash/fault-injection, and crafted-prefix fuzz tests.
7. Publish reproducible JMH and backend benchmarks before making performance claims.
8. For Cardano on-chain use, either standardize on MPF or create a separate commitment/PlutusData
   codec/Aiken package with shared golden vectors and an independent validator audit.

## Primary references

- [Diem Jellyfish Merkle Tree paper](https://developers.diem.com/papers/jellyfish-merkle-tree/2021-01-14.pdf)
- [Aptos sparse Merkle proof verifier](https://github.com/aptos-labs/aptos-core/blob/main/types/src/proof/definition.rs)
- [Aiken Merkle Patricia Forestry package](https://aiken-lang.github.io/merkle-patricia-forestry/)
- [Aiken Blake2b-256 API](https://aiken-lang.github.io/stdlib/aiken/crypto.html)
- [Cardano protocol parameter guide](https://docs.cardano.org/about-cardano/explore-more/parameter-guide)
