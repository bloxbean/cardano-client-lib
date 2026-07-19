# JMT Adversarial Audit — Pre-Remediation Snapshot

**Module:** `verified-structures/jellyfish-merkle` (+ `-rocksdb`, `-rdbms`, `rdbms-core`, `rocksdb-core`)
**Audited commit:** `f040b5a4` / `5e3752b2` (post proof-soundness & lifecycle hardening)
**Date:** 2026-07-19
**Method:** 8 independent auditors (one per dimension, blind to each other) → adversarial
verification of every deduplicated finding → synthesis.
**Status:** Historical findings snapshot; the release verdict below is superseded by
[ADR-002](002-production-readiness-gates.md) and the current
[security/performance audit](../docs/security-performance-audit.md). Detailed original findings in
[002-jmt-external-audit-findings.md](002-jmt-external-audit-findings.md).

> This document intentionally preserves the review performed against commits `f040b5a4` and
> `5e3752b2`. It is not an external cryptographic certification. The replay corruption, missing
> child, backend divergence, prune/rollback, configuration, lifecycle, benchmark, vector, fuzz,
> concurrency, and PostgreSQL qualification findings were addressed during ADR-002 implementation.
> Do not apply the historical "do not release" verdict to the remediated tree without reading the
> current qualification record.

## Run summary & caveats (read first)

- 8 auditors produced **54 raw findings → 53 unique → 39 confirmed/plausible** (2 critical, ~20
  medium, ~17 low), **1 refuted**.
- **The run was cut short by a session usage limit**: the synthesis agent and **8 verification
  agents failed** (limit resets 12:50pm Asia/Singapore). So 8 findings carry the finder's verdict
  without an independent second pass, and this executive summary is hand-written rather than
  agent-synthesized. **5 lower-severity findings were capped and not independently verified** (still
  listed). Treat the medium/low set as "needs confirmation" where a verifier didn't run.
- The safety classifier was unavailable for 5 verifier agents (noted; their outputs were reviewed).
- **I independently re-ran and CONFIRMED the CRITICAL finding** against the committed code (PoC below).

## Executive summary & release verdict

**Do not release.** One **critical, empirically-confirmed data-corruption bug** exists in the very
crash-recovery path that the last "lifecycle hardening" commit added: replaying an already-committed
version marks that version's *own live nodes* stale, so subsequent reads/proofs report a present key
as absent and `pruneUpTo` physically and permanently deletes live data. The genesis case erases the
whole tree. This defeats the crash-recovery guarantee the path was built to provide.

Beyond that, the recurring theme is **cross-backend divergence and prune/rollback edge cases**: the
three `JmtStore` implementations (InMemory / RocksDB / RDBMS) do not agree on stale-node visibility,
`floorNode`/`ceilingNode` semantics, value-history pruning, or SPI defaults — so "swap the backend"
is not currently safe, and several prune/truncate paths can silently drop live nodes. A cluster of
**test-coverage gaps** (no golden root vectors, no concurrency tests, no deep-prefix tests, the
attacker-reachable extension-node wire branch untested, Postgres gated off by default) means these
regressions can land unnoticed. The proof-soundness fixes from the prior round held up — no auditor
found a surviving forgery — which is the one clearly-solid area.

## CRITICAL — confirmed by re-execution

### Replaying a committed version corrupts the tree (crash-recovery path)

`JellyfishMerkleTree.findChildVersion` (~line 1058) resolves a child via a floor lookup at the
version *v currently being committed*. On a replay of an already-committed `v`, that floor lookup
returns the version-`v` nodes written by the **first** commit, and the replay treats those live
post-state nodes as the ones being replaced — `TreeCache.deleteNode` then adds the **live**
version-`v` `NodeKey` to the stale set. The commit writes both `putNode(K)` and `markStale(K,
staleSince=v)` for the same live key. `validateCommitVersion` explicitly permits this replay for
crash recovery, and the divergent-root guard does not help because an identical replay produces an
identical root.

**Consequences:** on InMemory/RDBMS (which honor stale markers on read) `getProof` returns a
non-inclusion proof for a key that `get()` still returns — an inconsistent, unsound state — and on
**every** backend `pruneUpTo(≥v)` physically deletes the live nodes (unrecoverable). Genesis replay
(`put(0, …)` twice) stale-marks the root itself, so the tree reads as empty and pruning deletes it.
Replaying an *older* committed version poisons nodes newer versions still share.

**I re-ran the audit's PoC against the committed code and both assertions failed as predicted:**
- `replay must not mark version-1 nodes stale at version 1` → **failed** (they are marked stale).
- `genesis replay must not erase the tree` → **failed** (proof value is null; the tree reads empty).

**Fix direction:** children must be resolved at `v-1`, not `v` (Diem records child versions in the
`Child` struct; the floor-substitute here uses the ceiling of the visible range). Either record child
versions, clamp the replay/base read to `baseVersion` (the `TreeCache.baseVersion` field is computed
but never wired into `getNode`), or make replay a true no-op when `rootHash(v)` already matches.
Related medium: **finding #22** — a missing child node in storage is silently rebuilt as a fresh
leaf, dropping the whole subtree from the new version without error (same `findChildVersion` area).

## MEDIUM — grouped by theme

**Cross-backend divergence (backends are not interchangeable):**
- `getNode(version,path)` stale-node visibility differs across InMemory / RocksDB / RDBMS.
- `floorNode`/`ceilingNode` return different results per backend; RocksDB uses raw byte order that
  contradicts `NodeKey`'s documented (content-first) ordering, and ignores stale markers.
- `pruneUpTo` prunes value history on RocksDB/RDBMS but **not** InMemory → `get(key,version)` and
  prune counts diverge.
- SPI default methods leak: `JmtStore.getValueAt` default silently returns the *latest* value for a
  historical query; `ceilingNode` default returns empty while its javadoc claims it delegates to
  `floorNode`; `RdbmsJmtStore.floorNode` does exact-path lookup instead of path-floor.

**Prune / truncate correctness edges:**
- `ValuePrunePolicy.AGGRESSIVE` silently deletes the current live value of keys not rewritten since
  the prune horizon (data loss with no warning).
- `truncateAfter` trusts a rollback-index completeness the store never enforces (enable-after-commits
  or disable/re-enable → silently incomplete rollback).
- No prune watermark: `truncateAfter` below an already-pruned horizon silently leaves the surviving
  tree missing nodes.
- `pruneUpTo(-1)` / negative versions treated as unsigned → deletes all history.

**Determinism / correctness:**
- `put()` takes an identity-keyed `Map<byte[],byte[]>`; two `byte[]` with equal content are distinct
  map entries, so a batch with "duplicate" logical keys yields a **nondeterministic committed root**.
- Older-version replay silently corrupts latest-value reads (InMemory) — same family as the critical.

**Config footgun:** `H2Dialect` infers MERGE key columns from table-name substrings, so a legal
`tablePrefix` breaks every commit.

**Test gaps (why the above can regress unnoticed):** no golden root-hash vectors (the whole
proof/root suite is self-referential — a silent commitment change passes every test); the
attacker-reachable extension-node wire branch has zero positive/adversarial tests; no deterministic
deep-prefix / full-depth tree tests; post-prune historical read/proof semantics unpinned; **zero
concurrency tests** across all three modules; Postgres tests gated off by default.

## LOW (selected)

`InMemoryJmtStore.ceilingNode` can infinite-loop (hang) when the newest node on a path is stale;
RDBMS commit metadata is non-atomic check-then-act (latest pointer can regress under concurrency,
contradicting the README thread-safety claim); `disableWalForBatches(true)` with default
`syncOnCommit(true)` makes every commit fail; native resource leaks (block cache / bloom filters not
closed on failed open); H2 `insertOrIgnoreSql` is last-write-wins while Postgres/SQLite are
first-write-wins; `RdbmsCommitBatch` has no closed/committed state (commit-after-close persists an
aborted batch; double-commit re-executes); autoCommit not restored before returning pooled
connections; `DbConfig.Builder` reuse-after-build closes the pool out from under the built config;
flaky timing-based perf test; jmh proof benchmarks double-hash the key and actually measure
non-inclusion; static RNG shared across property-test methods. Full list in the findings file.

## What looked solid

The proof-soundness posture held: no auditor found a surviving inclusion/non-inclusion forgery or
type-confusion in either verifier after the prior fixes. Commit atomicity on the persistent backends,
and the RocksDB prune/truncate iterator fixes, were not faulted for the single-namespace case.

## Prioritized remediation checklist

1. **Critical:** fix `findChildVersion`/replay so a committed-version replay is a true no-op (resolve
   children at `v-1` or wire in `baseVersion`); add the PoC as a regression test. Also fix the
   silent subtree-drop on missing child (#22).
2. Make the three backends contract-equivalent: unify stale-visibility, `floor/ceiling`, value-prune,
   and SPI defaults; back it with a cross-backend property test that includes prune + historical reads.
3. Prune/truncate safety: prune watermark, reject/guard negative versions, document/guard AGGRESSIVE
   data loss, enforce or detect rollback-index completeness.
4. Reject duplicate/ambiguous keys in `put()` (or take a content-keyed map) for deterministic roots.
5. Close the test gaps: golden root vectors, extension-node wire tests, deep-prefix tests,
   concurrency tests, Postgres in CI, post-prune semantics.
6. RDBMS/config hygiene: batch state machine, autoCommit restore, atomic latest update, H2 key-column
   fix, WAL/sync guard, pool-leak fixes.
