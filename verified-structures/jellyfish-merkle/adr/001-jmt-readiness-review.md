# JMT Readiness Review

**Module:** `verified-structures/jellyfish-merkle` (+ `-rocksdb`, `-rdbms` backends)
**Version reviewed:** 0.8.0
**Date:** 2026-07-19
**Status:** Review / findings — remediation applied 2026-07-19 (see "Resolution log" at end)

## Bottom line

**Not production-ready.** The happy path is well-built and fast, but there is a
**confirmed proof-soundness break** that defeats the module's core value proposition
("cryptographic proofs for trustless verification"), plus two silently-broken storage
lifecycle operations (pruning, rollback) in the RocksDB backend. All three are invisible
to the current test suite.

On-chain verification on Cardano is *feasible in principle* but **not possible with what
ships today**, and would be a substantial piece of new work.

The two highest-severity findings were verified by compiling probes against the real
classes and running them; both are reproducible.

---

## 1. Critical — proof forgery (confirmed by test)

The leaf commitment does not bind the key. In
`ClassicJmtCommitmentScheme.commitLeaf` (`commitment/ClassicJmtCommitmentScheme.java:67`)
a leaf hashes as `H(0x00 || suffixNibbles || valueHash)` — the **key hash is absent**.
Diem (the cited reference) hashes `H(key_hash || value_hash)`. Because of this,
`JmtProofVerifier` (`JmtProofVerifier.java:46`) trusts `proof.suffix()` and
`proof.conflictingKeyHash()` from the (attacker-supplied) proof without re-deriving them
from the queried key, so the only real binding is "the recomputed root matches."

Two forgeries were confirmed against a normal tree built through the public API:

- **Forged non-inclusion of a *present* key** → `JmtProofVerifier.verify(...)` returned
  `true`. An attacker who has seen any valid proof (or just knows a key's value) can prove
  that key is *absent*.
- **Forged inclusion of an *absent* key** → `verify(...)` returned `true`. In a
  single-leaf tree with empty proof steps, the queried key is never consulted, so an
  attacker can prove any key maps to the real value. With non-empty steps it needs a cheap
  shared-prefix collision (a few nibbles).

This is the headline finding: `JmtProofVerifier` is the module's documented primary
verifier (README "Proof Generation" example), and it accepts forged proofs of both types.

Note the asymmetry: the **wire** verifier `ClassicJmtProofCodec.verify`
(`proof/ClassicJmtProofCodec.java:164`) re-derives the suffix from the queried key for the
inclusion case and checks `leafNode.keyHash() == keyHash`, so **wire inclusion is sound** —
but wire non-inclusion (`proof/ClassicJmtProofCodec.java:172-177`) derives the suffix from
the attacker's leaf key hash and shares the same gap. The fix has to land in both the
commitment scheme (bind the key hash into the leaf) and the object-model verifier (derive
suffix from the queried key; never trust `proof.suffix()` / `conflictingKeyHash()`).

**Fix direction:** make the leaf hash `H(tag || keyHash || valueHash)` (Diem-compatible),
and have both verifiers reconstruct the suffix/keyHash from the queried key rather than the
proof. Add adversarial tests (tamper value, wrong root, altered sibling, mismatched key,
forged non-inclusion of a present key) — there are currently **zero** negative proof tests
anywhere.

---

## 2. Critical — storage lifecycle broken in RocksDB backend (confirmed by probe)

Both were verified by running probes against the compiled store:

- **`pruneUpTo` reclaims almost nothing.** `RocksDbJmtStore.pruneValues` iterates the
  values column family with a prefix read option (`prefixSameAsStart`) while the CF uses a
  33-byte fixed-length prefix extractor, so the scan invalidates at the first key-hash
  boundary. With 3 keys × versions {1,2,5}, `pruneUpTo(2)` pruned **1** entry; the rest of
  the history survives and grows unbounded while reporting success.
- **`truncateAfter` (rollback) corrupts state.** Same prefix-iterator root cause on the
  `nodes_by_ver` / `values_by_ver` CFs: after commits v1..v5, `truncateAfter(2)` removed
  only v3; v4/v5 nodes *and* values remained. A chain reorg would report success while
  leaving ghost state that reappears when those version numbers are reused. There is **no
  test for `truncateAfter` anywhere.** It also drops the latest-root pointer when no commit
  exists at exactly the target version, so a restart can look empty despite valid data on
  disk.

Related storage issues:

- RDBMS has no `truncateAfter` at all (throws `UnsupportedOperationException`).
- RDBMS commit isn't idempotent and fails outright on SQLite (duplicate-key handling keyed
  to Postgres/H2 SQLState `23505`); replaying a committed version fails with a PK violation.
- RocksDB reads ignore stale markers (latent only because deletes are currently disallowed;
  will resurrect stale nodes the moment deletion lands).
- The internally-built HikariCP pool is never closed (leaks a 10-connection pool per
  open/close).
- `NodeKey`'s byte encoding sorts by path *length* before *content*, so it does **not**
  match its own `compareTo` — making the `floorNode` / `ceilingNode` SPI silently wrong for
  mixed-depth paths (currently dead code, but a booby-trap).
- `InMemoryJmtStore.CommitBatch.close()` applies writes on abandon
  (`store/InMemoryJmtStore.java:295`), so a failed commit persists a partial batch — it
  diverges from both persistent backends and shouldn't be treated as the reference.

**What's genuinely good:** commit atomicity is solid on both persistent backends —
RocksDB stages nodes/stale/values/root/pointers in one synced `WriteBatch`; RDBMS uses a
single transaction with rollback; both have crash-abandon tests; SQL is fully parameterized
with identifier whitelisting (no injection).

---

## 3. Test coverage — happy path only

58 / 16 / 14 tests pass across core / rocksdb / rdbms in seconds, with a genuinely strong
three-way cross-backend root-equality test. But the gaps line up exactly with the bugs
above:

- **No adversarial/negative proof tests** — nothing asserts `verify(...) == false`. This is
  why the forgeries went unnoticed.
- **No `truncateAfter` test; prune tests only exercise single-group prunes** — why the
  storage bugs went unnoticed.
- **Postgres backend never runs in CI** (all 5 tests gated on an env var); only H2/SQLite
  semantics are checked by default.
- **No concurrency tests** despite thread-safety claims in the READMEs.
- Trees are small (≤150 keys) and keys are hashed, so extension/deep-prefix structure is
  barely exercised.
- Hygiene: `DebugTreeStructureTest` asserts nothing; a wall-clock `≥2.0x` speedup assertion
  is flake-prone; the "property" tests are fixed-seed `java.util.Random` loops (no
  jqwik/shrinking, despite ADR-001 implying otherwise).

---

## 4. On-chain verification on Cardano — feasible, but not with what ships

Short answer: **the algorithm can be ported to Aiken/Plutus, but nothing on-chain exists
today and the current design is a poor fit.**

- **Hash primitive is fine.** The tree uses Blake2b-256 (`Blake2b256` →
  `Blake2bUtil.blake2bHash256`), which is exactly Cardano's `blake2b_256` builtin. Off-chain
  and on-chain hashes would agree.
- **The commitment scheme is on-chain-hostile.** Every internal node hashes a fixed
  515-byte preimage (`0x01 || bitmap(2B) || 16×32-byte children`,
  `ClassicJmtCommitmentScheme.commitBranch:35`), and an inclusion proof must carry all 16
  child slots per level (`JmtProof.BranchStep.fullChildHashes`, a `byte[16][]`). For a proof
  of depth *d* that's ~`d × 512` bytes of siblings in the redeemer and *d* hashes over 515
  bytes each. This is the opposite of what the sibling MPF module does:
  `aiken-lang/merkle-patricia-forestry` commits each 16-way branch as a small **sparse
  Merkle tree over the 16 slots**, so each step carries only 4 sibling hashes (128 bytes) and
  hashes tiny preimages — which is why MPF is on-chain-practical and JMT-classic is not.
- **The wire format is CBOR, not `PlutusData`.** `ClassicJmtProofCodec` emits a CBOR array
  of encoded nodes decoded with `co.nstant.in.cbor`. On-chain, a validator receives `Data`
  (Constr/List/ByteString), so the proof would need a `PlutusData`-shaped codec, not the
  current CBOR-node encoding.
- **No validator or on-chain library exists.** MPF ships
  `onchain/validators/mpf_validator.ak` plus tests against the Aiken stdlib. JMT ships
  **nothing** on-chain — no `.ak`, no golden vectors, no Aiken port.
- **The soundness bug is a hard blocker.** You cannot put a proof verifier on-chain whose
  off-chain twin accepts forged proofs; the leaf-hash fix must come first, and ideally the
  on-chain and off-chain commitments must be defined to match byte-for-byte (there are
  currently no cross-implementation golden vectors, and the leaf-hash deviation means it
  isn't even Diem-compatible).

So: on-chain JMT verification is *achievable* but is essentially a new project — fix the
commitment/soundness, adopt an on-chain-efficient branch commitment (sparse-16 like MPF, or
go binary), define a `PlutusData` proof format, write and audit an Aiken validator, and lock
it down with shared golden vectors. **If the actual goal is Cardano on-chain proofs, MPF is
the intended and already-working vehicle**; JMT's real niche here is high-throughput
*versioned off-chain* state (Diem/Aptos-style), where on-chain you'd typically anchor only
the 32-byte root.

---

## 5. Recommended enhancements, prioritized

1. **Fix leaf-hash soundness** (bind key hash; verifiers derive suffix from the queried
   key) + add a negative-proof test suite. Blocks everything else.
2. **Fix the RocksDB prefix-iterator bugs** in `pruneUpTo` / `truncateAfter` (use
   `total_order_seek` or explicit iterate bounds instead of `prefixSameAsStart`) and add real
   prune + rollback tests, including reads of pruned/rolled-back versions.
3. **Decide JMT's positioning explicitly.** The top-level README calls these
   "production-ready" and the JMT README says "Experimental" — reconcile that. Given the
   findings, "experimental / off-chain versioned state" is the honest label today.
4. **Close backend gaps:** implement `truncateAfter` for RDBMS (or document its absence),
   make RDBMS commit idempotent (there's an unused `insertOrIgnoreSql` for exactly this),
   close the Hikari pool, honor `NamespaceOptions.keyPrefix`, and fix `NodeKey` byte-ordering
   (or delete the broken floor/ceiling SPI).
5. **Fix the READMEs** — both backend READMEs document constructors, methods
   (`store.deleteNode`, `initializeSchema`, `tree.get(key)` no-version), and schemas that
   don't exist.
6. **Add Postgres to CI**, concurrency tests, and a large/crafted-shape fuzz corpus.
7. **Prune dead code:** tombstone/`deleteValue` paths, half-implemented extension-node
   handling (tag `0x02` is verified but never produced), and the deprecated `RocksDbConfig`
   shell.

---

## Appendix — reproduction notes

Both critical proof forgeries were reproduced with throwaway JUnit probes placed in
`jellyfish-merkle/src/test/java/.../jmt/` and run via
`./gradlew :verified-structures:jellyfish-merkle:test`. The probes:

1. Built a single-key tree through the public API and obtained the committed root.
2. Constructed a `JmtProof` via reflection (private constructor) reusing the real leaf's
   suffix and value hash but a different claimed key hash.
3. Asserted `JmtProofVerifier.verify(...) == false`; both assertions **failed** (verifier
   returned `true`), confirming forged non-inclusion of a present key and forged inclusion of
   an absent key.

The probe files were removed after confirmation; the test suite is back to green.

---

## Resolution log (2026-07-19)

The critical and most correctness findings above have been fixed and covered by focused core and
backend tests.

**§1 Proof forgery — FIXED.** Leaf commitment now binds the key hash:
`ClassicJmtCommitmentScheme.commitLeaf(byte[] keyHash, byte[] valueHash)` →
`H(0x00 || keyHash || valueHash)`. `JmtProofVerifier` and `ClassicJmtProofCodec` derive the
suffix/key from the queried key (never from proof-supplied fields), add an on-path prefix check for
the different-leaf case, and return `false` (not throw) on malformed proofs. New `JmtProofSoundnessTest`
(9 adversarial cases incl. both original forgeries) + `JmtPropertyBasedTest` (jqwik, multi-hash).

**§2 RocksDB prune/truncate — FIXED.** `pruneValues` and the `truncateAfter` version-index scans use
total-order seek + explicit namespace-prefix filtering instead of `prefixSameAsStart`; `truncateAfter`
now repoints "latest" to the greatest surviving root and fixes the `indexCfOptions` native leak. New
`RocksDbJmtPruneTruncateTest` (proven to fail against the old code) + `RocksDbJmtPropertyTest`
(RocksDB vs in-memory agreement).

**§3 NodeKey ordering — FIXED.** Content-first byte encoding (`nibble+1` bytes, `0x00` terminator,
then version) so lexicographic byte order equals `compareTo`; unit-tested for mixed-depth paths.
**InMemory abort — FIXED.** `CommitBatch.close()` no longer applies staged writes on abandon.

**§4 backends — PARTIAL.** RDBMS commit is now idempotent (`insertOrIgnoreSql`, fixes SQLite/crash
replay) and batched (`addBatch`); RDBMS `truncateAfter` implemented; `DbConfig` is `AutoCloseable`
and closes pools it created. New `RdbmsJmtTruncateIdempotentTest`.

### Second-pass fixes (after adversarial + workflow code review)

Two independent reviews surfaced a **second proof-soundness hole** and several persistence
regressions introduced by the first pass; all confirmed items are now fixed and tested.

- **[CRITICAL] Wire proof type-confusion — FIXED.** `ClassicJmtProofCodec.verify` accepted a
  genuine non-inclusion (missing-branch / empty) wire proof when presented with `including=true`,
  forging inclusion of any value for an absent key. Added `if (including && !terminalLeaf) return
  false;`. Regression tests (`wireNonInclusionNotAcceptedAsInclusion`, `emptyTreeWireNotAcceptedAsInclusion`)
  are proven to fail without the guard.
- **[HIGH] NodeKey on-disk format break — REVERTED.** The content-first NodeKey encoding only fixed
  an unused floor/ceiling SPI while making existing stores unreadable / crashing maintenance ops.
  Reverted to the original length-prefixed encoding; floor/ceiling remains documented as not
  byte-order-correct (unused by the core). The `commitLeaf` change still changes root hashes — that
  is inherent and desirable for the security fix; document it as a breaking change for any existing
  tree (rebuild required).
- **[HIGH] RDBMS divergent / older-version replay — FIXED.** Commit now reads the stored root for the
  version: identical replay is a no-op, a different root is rejected (rolls back), and the latest
  pointer is monotonic (older-version replay no longer regresses it). Same guards mirrored into the
  RocksDB commit for parity. Tests added on both backends.
- **[HIGH] DbConfig builder pool leak — FIXED.** Overwriting an owned data source
  (`jdbcUrl()` then `dataSource()`/`jdbcUrl()`) now closes the previously-created pool.
- **[MED] Verifier swallowed config diagnostics — FIXED.** `JmtProofVerifier` validates the hash
  digest length against the commitment scheme up front and throws on mismatch (a caller bug), while
  still returning false for malformed proof contents. Test added.
- **[MED] RocksDB stale/prune/latest scans — HARDENED.** `staleNodesUpTo`, `pruneUpTo`, the
  `pruneValues`/roots scans, and the `latestRoot()` fallback now use total-order seek bounded to the
  namespace prefix (seek-to-prefix + break), closing the last prefixSameAsStart inconsistencies and
  bounding prune cost to one namespace.
- **[LOW] RDBMS truncateAfter signed/unsigned mismatch — GUARDED.** Negative `versionExclusive`
  (unsigned-huge) now deletes nothing, matching the RocksDB backend.

**Deferred (with rationale):**
- **RocksDB stale-read filter (§2 major):** latent — the tree forbids deletes, and the cross-backend
  equality test shows InMemory/RocksDB agree today. Belongs with the future delete feature (avoids
  read-path cost now).
- **RocksDB `NamespaceOptions.keyPrefix`:** needs `Options` plumbing; CF-based namespacing works today.
- **On-chain (§4):** unchanged — still a separate project (fix + on-chain-efficient commitment +
  PlutusData codec + Aiken validator + golden vectors).
- **READMEs (§5):** documentation of non-existent APIs not yet reconciled.

### Independent third pass

A subsequent independent pass added the following hardening and records the remaining release
risks in [`docs/security-performance-audit.md`](../docs/security-performance-audit.md):

- Gapped commits now stale the actual prior root node rather than a synthetic `version - 1` key.
- In-memory rollback no longer overflows at `Long.MAX_VALUE`, and in-memory divergent replay now
  fails atomically like the persistent backends.
- New historical versions below latest are rejected while existing-version replay remains allowed.
- The tree and persistent value stores enforce the documented 32-byte key hash.
- Public tree and rollback versions are consistently non-negative; invalid negative values fail fast.
- Node/proof CBOR rejects trailing top-level items, oversized proofs are rejected before decoding,
  proof path length is bounded, and decoded node fields receive stricter structural validation.
- Unsupported RocksDB key-prefix namespaces fail fast instead of silently losing isolation.
- Core/backend READMEs and the design document now describe actual constructors, lifecycle,
  single-writer behavior, custom (non-Diem-compatible) commitments, and Cardano limitations.

The independent verdict remains **experimental / not production-ready**. In particular, old stores
must be rebuilt for the key-binding commitment, no persisted format identifier enforces that
migration, and concurrent writers still require an external coordinator.
