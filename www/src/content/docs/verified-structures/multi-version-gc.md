---
title: "MPF versions and garbage collection"
description: "Record versions of an MpfTrie on RocksDB, read old versions and reclaim space."
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

## Record versions

`RocksDbStateTrees` defaults to `StorageMode.MULTI_VERSION`. The mode is saved on first open and checked on every later open. The SQL backend defaults to `SINGLE_VERSION`, so pass the mode explicitly if you might use both.

```java
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
    RocksDbMptSession session = RocksDbMptSession.of(stateTrees);

    long v1 = session.write(() -> {
        trie.put("alice".getBytes(), "100".getBytes());
        return stateTrees.putRootWithRefcount(trie.getRootHash());
    });

    long v2 = session.write(() -> {
        trie.put("alice".getBytes(), "150".getBytes());
        return stateTrees.putRootWithRefcount(trie.getRootHash());
    });

    // Read an older version
    MpfTrie atV1 = new MpfTrie(stateTrees.nodeStore(), stateTrees.rootsIndex().get(v1));
    byte[] alice = atV1.get("alice".getBytes());   // "100"
}
```

If you collect garbage with mark-sweep only, `stateTrees.rootsIndex().put(version, root)` is enough to record a version.

## Keep only the latest state

```java
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf", StorageMode.SINGLE_VERSION)) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore(), stateTrees.getCurrentRoot());
    trie.put("alice".getBytes(), "100".getBytes());
    stateTrees.putRootSnapshot(trie.getRootHash());   // overwrites version 0
    stateTrees.cleanupOrphanedNodes(new GcOptions()); // keeps only the current root
}
```

## Garbage collection

Every `put` writes new nodes and leaves the old ones in place. GC deletes the nodes that no retained version reaches.

```java
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.*;

GcManager gc = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());

GcOptions options = new GcOptions();
options.deleteBatchSize = 10_000;
options.progress = deleted -> System.out.println("deleted " + deleted);

GcReport report = gc.runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(5), options);
System.out.println("deleted " + report.deleted + " of " + report.total + " nodes");
```

`runSync` declares `throws Exception`. To simulate a run, set `options.dryRun = true`. `gc.runAsync(strategy, policy, options, report -> ...)` runs on a daemon thread and passes the report to the callback.

| Strategy | Cost | Requirements |
|---|---|---|
| `OnDiskMarkSweepStrategy` | Walks every retained version and scans the namespace's nodes | None |
| `RefcountGcStrategy` | Walks the retained and dropped versions, with no full scan | Versions recorded with `putRootWithRefcount`; only those are reclaimed |

### Retention policies

```java
RetentionPolicy.keepLatestN(10);
RetentionPolicy.keepVersions(List.of(1L, 5L, 10L));
RetentionPolicy.keepRange(5, 15);                // inclusive
```

A policy can also be any lambda from the roots index to the roots to keep.

### Rules

- **Stop writers first.** Don't run GC while anything else writes to the same namespace.
- **The latest root is always kept.** With a roots index, the `LATEST` root and the root at the last version survive whatever the policy says. Commit those tries before running GC, because a root visible only in an open write batch is rejected.
- **Without a roots index,** `OnDiskMarkSweepStrategy` keeps only the policy's roots.
- **Use one strategy per namespace,** and run mark-sweep occasionally to reclaim what refcount GC leaks.
- **Refcount GC fails safe.** It never deletes a node a retained version still reaches, even with wrong counts. It leaks instead and reports those nodes in `GcReport.protectedNodes`. It refuses to run when a retained root has no refcount.
- **Refcount GC edits the index.** It removes dropped versions from the roots index, while mark-sweep leaves the index as it is.

### After a crash

Both strategies use a temporary column family named `__vds_gc_marks_<digits>`. If you open the `RocksDB` yourself, open it with all column families from `RocksDB.listColumnFamilies` and call `GcManager.dropLeftoverGcColumnFamilies` after a crash.

### Databases written by 0.8.0-pre6 or earlier

0.8.0-pre7 fixed data-loss bugs in RocksDB GC, so upgrade before running GC on an existing database.

- Refcounts written by older code counted only roots. Refcount GC now protects retained tries from them and leaks instead of deleting.
- Temporary column families left by 0.8.0-pre6 are named `marks_<digits>` and are never dropped automatically. Check them, then drop them with `GcManager.dropLegacyGcColumnFamilies`.

For the algorithms and on-disk details, see the [GC design document](https://github.com/bloxbean/cardano-client-lib/blob/master/verified-structures/merkle-patricia-forestry-rocksdb/docs/gc.md).
