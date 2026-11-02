---
title: "MPF production guide"
description: "Atomic writes, RocksDB tuning, thread safety, backup and shutdown for MpfTrie."
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

## Atomic writes

`RocksDbMptSession` runs your work in one RocksDB write batch and commits it at the end. Nodes, the version entry and refcounts land together.

```java
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbMptSession;

MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
RocksDbMptSession session = RocksDbMptSession.of(stateTrees);

long version = session.write(() -> {
    trie.put("alice".getBytes(), "100".getBytes());
    trie.put("bob".getBytes(), "200".getBytes());
    return stateTrees.putRootWithRefcount(trie.getRootHash());
});
```

Inside a session, write only through the store APIs. A batch is bound to the calling thread.

For a bulk load that you can repeat, you can trade durability for speed by turning off the write-ahead log with `RocksDbMptSession.of(stateTrees, Options.builder().disableWal(true).build())`. Writes that aren't flushed yet are then lost on a crash or close, and they aren't in a backup.

## Storage layout

`RocksDbStateTrees` keeps two column families:

```text
nodes:  [ns][node hash]          -> CBOR-encoded node
        [ns][0xF0][node hash]    -> refcount (refcount GC only)
roots:  [ns][version]            -> root hash
        [ns]"LATEST"             -> latest root
        [ns]"VERSION"            -> last version number
        [ns]"_storage_mode"      -> MULTI_VERSION or SINGLE_VERSION
```

`[ns]` is the namespace's one-byte key prefix, `0x00` by default. To keep several tries in one database, pass `NamespaceOptions.keyPrefix((byte) 0x01)` or `NamespaceOptions.columnFamily("accounts")` to the constructor.

A RocksDB directory can be opened by only one `RocksDbStateTrees` at a time. Open namespaces one after another, or build `RocksDbNodeStore` and `RocksDbRootsIndex` instances over one shared `RocksDB` handle.

## RocksDB tuning

`RocksDbConfig` has the profiles `highThroughput()`, `balanced()`, `lowMemory()` and `defaults()`, plus a builder. Pass one to the constructor, and measure with your own workload:

```java
RocksDbStateTrees(String dbPath, NamespaceOptions namespace, StorageMode mode, RocksDbConfig config)
```

See the [rocksdb-core README](https://github.com/bloxbean/cardano-client-lib/tree/master/verified-structures/rocksdb-core) for its options.

## Thread safety

- `MpfTrie` isn't thread-safe. Use one instance per thread, or synchronize access.
- The backend has no concurrency control: serialize writers of a namespace.
- Pause writers while [garbage collection](/verified-structures/multi-version-gc/) runs. Its snapshot doesn't protect re-puts of existing node keys.

## Monitoring

```java
String stats = stateTrees.db().getProperty("rocksdb.stats");
String size = stateTrees.db().getProperty("rocksdb.total-sst-files-size");
```

## Backup and restore

Use RocksDB's own backup engine, with writers paused:

```java
try (BackupEngine backup = BackupEngine.open(Env.getDefault(), new BackupEngineOptions(backupPath))) {
    backup.createNewBackup(stateTrees.db(), true); // true flushes first
}

try (BackupEngine restore = BackupEngine.open(Env.getDefault(), new BackupEngineOptions(backupPath));
     RestoreOptions options = new RestoreOptions(false)) {
    restore.restoreDbFromLatestBackup(dbPath, dbPath, options);
}
```

## Shutdown

Close `RocksDbStateTrees`, ideally with try-with-resources. It closes the database and its resources.
