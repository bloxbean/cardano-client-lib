# Garbage Collection — merkle-patricia-forestry-rocksdb

MPF tries are persistent: a `put` or `delete` writes new nodes along the changed path and leaves the old ones in
place, so every recorded root stays readable. GC deletes the nodes that no *retained* root reaches.

This document describes what the code does. Classes are in `com.bloxbean.cardano.vds.mpf.rocksdb.gc` unless stated
otherwise.

## 1. On-disk layout

All keys of a namespace start with its one-byte key prefix `[ns]` (`0x00` unless
`NamespaceOptions.keyPrefix(..)` is used). Namespaces created with `NamespaceOptions.columnFamily(..)` get their own
column families instead.

| Column family | Key | Value |
|---|---|---|
| `nodes` | `[ns][node hash]` (1 + digest bytes) | CBOR-encoded node |
| `nodes` | `[ns][0xF0][node hash]` (2 + digest bytes) | refcount, 8-byte big-endian long (refcount GC only) |
| `roots` | `[ns][version as 8-byte big-endian]` | root hash |
| `roots` | `[ns]"LATEST"` | root of the last `put` |
| `roots` | `[ns]"VERSION"` | highest version number (keeps `nextVersion()` monotonic) |
| `roots` | `[ns]"_storage_mode"` | `MULTI_VERSION` or `SINGLE_VERSION` |

Node keys and refcount keys are told apart by **length**, not by the `0xF0` byte: about one node hash in 256 starts
with `0xF0`.

Every scan of a namespace (roots index listing, sweeps, tools) seeks to `[ns]` with a total-order iterator and stops
at the first key with another prefix (`KeyPrefixer.namespaceStart()` / `createNamespaceScanReadOptions()`). Other
namespaces in a shared column family are never read or modified.

## 2. Which roots are kept

`RetentionPolicy` chooses roots from the namespace's `RocksDbRootsIndex`:

- `keepLatestN(n)` — roots of the `n` highest versions;
- `keepVersions(versions)` — roots of the given versions;
- `keepRange(from, to)` — roots of versions in `[from, to]`;
- any lambda `index -> Collection<byte[]>`.

On top of the policy, every strategy always keeps the root under `LATEST` and the root at `lastVersion()`
(`gc.internal.GcRoots`). This also applies when a strategy is called directly through `GcStrategy.run(..)` and to
`RocksDbStateTrees.cleanupOrphanedNodes(..)`.

## 3. Mark-and-sweep — `strategy.OnDiskMarkSweepStrategy`

Works on any store, including stores without refcounts.

1. **Snapshot.** With `GcOptions.useSnapshot` (default `true`) a RocksDB snapshot is taken first.
2. **Roots.** The retained roots are resolved (section 2). They must all have the same length; that length is the
   digest length used to recognise node keys (32 bytes when there is no root).
3. **Mark.** A temporary column family `__vds_gc_marks_<nanoTime>` receives every node hash reachable from a
   retained root (breadth-first walk). The mark set lives on disk, so memory stays flat for large tries.
4. **Sweep.** The node keys of the namespace are scanned through the snapshot, so node keys that did not exist at
   step 1 are not deleted. Each unmarked node is deleted together with its refcount entry, if any. Refcount entries
   of kept nodes and every other key are left alone. Deletes are flushed every `deleteBatchSize` nodes and reported
   to `progress`.
5. The marks column family is dropped. If a run crashes, it is left behind (see section 7).

The snapshot does **not** make GC safe next to writers: nodes are content-addressed, so a writer that re-puts a key
that already existed, or commits a root reusing an old unmarked node, can still lose that node.

`dryRun` reports what would be deleted without writing. The roots index is not modified: versions that were not
retained stay listed, but their tries are no longer complete.

## 4. Reference counting — `strategy.RefcountGcStrategy`

A node's count is the number of recorded versions whose trie contains it.

**Recording.** `RocksDbStateTrees.putRootWithRefcount(root)` assigns the next version, stores the root and adds one
to the count of every node reachable from it, in one batch. Called inside `nodeStore().withBatch(..)` or
`RocksDbMptSession.write(..)`, it stages everything in that open batch and reads the staged nodes, so nodes, root and
counts commit together; several calls in one batch see each other's counts and versions. The all-zero root (the empty trie,
as returned by `MpfTrie.getRootHashOrEmpty()`) is recorded without counts; `putRootWithRefcount(null)` throws, so
record an empty trie with `getRootHashOrEmpty()` rather than `getRootHash()`.

**Collecting.** `run(..)` drops every listed version whose root is not retained:

- If any retained root has no count, it throws `IllegalStateException` and changes nothing: the namespace holds
  versions that were not recorded with `putRootWithRefcount`, so refcount GC could not reclaim their nodes. Use
  mark-sweep.
- It marks every node reachable from a retained root in a temporary `__vds_gc_marks_*` column family (the same
  on-disk mark set mark-sweep uses), with `GcReport.marked` set to the number of marked nodes.
- For each dropped version it subtracts one along that version's trie. A node is deleted only when its count goes
  from 1 to 0 **and** it is not marked. A marked node whose count would reach 0 is kept with its count and reported
  in `GcReport.protectedNodes`. A node without a count is untracked: it is neither deleted nor descended into.
- All decrements of one run share the counts staged so far, so tries that share nodes are decremented correctly in
  a single batch.
- Counts are read through a snapshot when `GcOptions.useSnapshot` is set. Decrements, deletions and index updates are
  written as one atomic `WriteBatch`, so `deleteBatchSize` and `progress` do not apply; `dryRun` writes nothing.
- The dropped version entries are deleted from the roots index in the same batch (when the index and the nodes share
  one RocksDB; otherwise the index entries are deleted first). Running GC again cannot decrement a version twice.
  `LATEST` and `VERSION` are not changed.

**Safety on legacy and mixed stores.** Because of the marking, refcount GC can never delete a node that a retained
version still reaches, whatever the counts say. This matters for stores whose counts were written by pre-0.8.0 code:
the old `putRootWithRefcount` inside a session batch, and the old pattern of staging trie nodes and `incrementAll` in
one `WriteBatch`, counted only the root. On such stores refcount GC is safe but leaks (`protectedNodes > 0`);
`OnDiskMarkSweepStrategy` reclaims the leak. The price of the marking is a walk of the retained tries on every run:
in a 50k-key benchmark a steady-state run that drops one version took about 2.1x as long as without marking (still
well below a mark-sweep, which also scans every node key).

Nodes that were written but never part of a recorded root (for example intermediate nodes of several `put`s inside
one batch) are never counted, so refcount GC never deletes them either. Run mark-sweep occasionally if that matters.

**Static helpers.** `incrementAll(..)` / `decrementAll(..)` apply the counting rules to one root. Each call (and
`putRootWithRefcount` inside a batch) first replays the records currently in the `WriteBatch` for its column family
and namespace (`WriteBatch.iterate`, last write wins) and reads the database under them. So counts and nodes staged
by earlier calls or by trie writes into the same batch are seen, a node whose deletion an earlier `decrementAll`
staged is written back when a later `incrementAll` counts it again, and clearing the batch, rolling it back to a
save point, or reusing it after `db.write` is safe. The cost is one pass over the batch per call. Pass a fresh batch
or the batch you are about to write, and do not let another thread write to it during the call. The helpers do not
know the retained roots, so they cannot protect nodes the way `run(..)` does: they trust the counts. `incrementAll`
throws `IllegalStateException` when the root node is neither committed nor staged in the batch. A batch that
holds `merge`, `deleteRange` or blob-index records on the nodes column family overlapping the namespace is rejected
with `IllegalStateException` before anything is staged, since those records cannot be interpreted. Prefer `putRootWithRefcount`, which also works inside `nodeStore().withBatch(..)` / `RocksDbMptSession.write(..)`
(joining a batch opened by another node store instance of the same namespace, and throwing for a batch of another
namespace).

## 5. Rules

- **No concurrent writers.** Pause every writer of the namespace while GC runs. The snapshot only protects node keys
  that did not exist when it was taken (see section 3).
- **Prefer one strategy per namespace.** Mixing is safe but refcount GC after mark-sweep can leak; refcount GC only
  reclaims nodes of versions recorded with `putRootWithRefcount`.
- **Pre-0.8.0 refcounts.** Stores whose counts were written by older code are safe to collect with refcount GC but
  leak; run mark-sweep to reclaim the leak.
- **Single hash function per namespace.** Mark-sweep fails fast when retained roots have different lengths.

## 6. Single-version mode

In `SINGLE_VERSION` mode `putRootSnapshot(root)` overwrites version 0 and `cleanupOrphanedNodes(options)` runs
mark-sweep keeping that root.

## 7. Temporary column families and caller-managed databases

Both strategies keep their mark set in a temporary column family named `__vds_gc_marks_<digits>`, dropped at the end
of the run. A run that crashes leaves it behind:

- `RocksDbStateTrees`, `RocksDbNodeStore(String..)` and `RocksDbRootsIndex(String..)` open every existing column
  family and drop leftovers right after opening. Only names that match exactly `__vds_gc_marks_<digits>`, or
  `marks_<digits>` as created by 0.8.0-pre6, are dropped, and never a column family the store itself uses. Namespace
  column families are always named `<prefix>_nodes` / `<prefix>_roots`, so they never match these patterns.
- A caller that opens the `RocksDB` itself with a fixed column-family list will fail to open after such a crash
  ("column families not opened"). Open with every column family listed by `RocksDB.listColumnFamilies(..)` instead,
  then call `GcManager.dropLeftoverGcColumnFamilies(db, handles)` before running GC.

## 8. Internal helpers

`gc.internal.RocksDbGc` (`gcKeepLatest`, `gcKeepVersions`, `markReachable`, `sweepUnreachable`) is an in-memory
mark-and-sweep with the same namespace scoping, key handling and latest-root retention. It holds the mark set in a
`Set<String>`, so prefer `OnDiskMarkSweepStrategy` for large tries.

## 9. GcTool

```bash
./gradlew :verified-structures:load-tools:run --args="gc generate /tmp/mpt 10000 5"
./gradlew :verified-structures:load-tools:run --args="gc stats /tmp/mpt"
./gradlew :verified-structures:load-tools:run --args="gc gc-refcount /tmp/mpt keep-latest 1"
./gradlew :verified-structures:load-tools:run --args="gc gc-marksweep /tmp/mpt keep-latest 1"
```

`generate` writes `numVersions` versions, each in one `RocksDbMptSession` batch that also calls
`putRootWithRefcount`, so both strategies can be tried on the result. `stats` prints node count, version count and
the latest root.
