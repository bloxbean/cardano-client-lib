# Merkle Patricia Forestry - RocksDB Backend

RocksDB persistence for Merkle Patricia Forestry (MPF) tries, with version tracking and garbage collection.

## Overview

`RocksDbStateTrees` opens (or creates) a RocksDB database holding two column families: `nodes` (trie nodes, plus
refcounts when refcount GC is used) and `roots` (version → root hash). Use its `nodeStore()` as the `NodeStore` of an
`MpfTrie`, record roots in its `rootsIndex()`, and reclaim space from old versions with a GC strategy.

Several independent tries can share one database through **namespaces** (`NamespaceOptions`): either a column-family
prefix (separate CFs) or a one-byte key prefix (shared CFs).

## Gradle Dependency

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:<version>'
}
```

## Quick Start

### Single trie, versions recorded per commit

```java
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbMptSession;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbStateTrees;

try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {   // MULTI_VERSION by default
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
    RocksDbMptSession session = RocksDbMptSession.of(stateTrees);

    // One atomic batch: trie nodes + version entry + refcounts
    long v1 = session.write(() -> {
        trie.put("alice".getBytes(), "100".getBytes());
        trie.put("bob".getBytes(), "200".getBytes());
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

`putRootWithRefcount` may be called inside the session (as above) or after it; inside, the root, the version entry and
the refcounts commit atomically with the nodes. If you collect with mark-sweep only, recording roots with
`stateTrees.rootsIndex().put(version, root)` is enough.

### Re-opening a trie

```java
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore(), stateTrees.rootsIndex().latest());
    byte[] value = trie.get("alice".getBytes());
}
```

### Single-version (snapshot) mode

```java
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf", StorageMode.SINGLE_VERSION)) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore(), stateTrees.getCurrentRoot());
    trie.put("alice".getBytes(), "100".getBytes());
    stateTrees.putRootSnapshot(trie.getRootHash());   // overwrites version 0
    stateTrees.cleanupOrphanedNodes(new GcOptions()); // mark-sweep keeping only the current root
}
```

The storage mode is persisted per namespace on first open and validated on every later open. RocksDB defaults to
`MULTI_VERSION`; the RDBMS backend defaults to `SINGLE_VERSION`.

### Namespaces

```java
// Shared column families, one-byte key prefix per trie
new RocksDbStateTrees("data/mpf", NamespaceOptions.keyPrefix((byte) 0x01));
// Separate column families per trie
new RocksDbStateTrees("data/mpf", NamespaceOptions.columnFamily("accounts"));
```

All index scans and GC runs are scoped to the namespace they were opened with. A RocksDB directory can only be opened
by one `RocksDbStateTrees` at a time; open namespaces one after another, or build `RocksDbNodeStore` /
`RocksDbRootsIndex` instances over one shared `RocksDB` handle yourself.

## Garbage Collection

Every `put` writes new nodes and leaves the nodes of older versions in place. GC deletes the nodes that no retained
version reaches. See [docs/gc.md](docs/gc.md) for the algorithms and the on-disk layout.

```java
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.*;

GcManager gc = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
GcOptions options = new GcOptions();
options.deleteBatchSize = 10_000;
options.progress = deleted -> System.out.println("deleted " + deleted);

GcReport report = gc.runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(5), options);
System.out.println("deleted " + report.deleted + " of " + report.total + " nodes in " + report.durationMillis + " ms");
```

| Strategy | Cost | Requirements |
|---|---|---|
| `OnDiskMarkSweepStrategy` | Walks every retained version and scans the namespace's nodes | None; works on any store |
| `RefcountGcStrategy` | Walks only the dropped versions | Every version recorded with `putRootWithRefcount` |

Rules:

- The `LATEST` root and the root at the last version are always kept, whatever the policy says.
- Do not run GC while another thread or process writes to the same namespace.
- Pick one strategy per namespace. Refcount GC refuses to run when a retained root has no refcount, and leaves nodes
  that were never counted (for example intermediate nodes written inside a batch) to mark-sweep.
- Stores whose refcounts were written by pre-0.8.0 code that staged trie nodes and `incrementAll` in the same
  `WriteBatch` have wrong counts; collect them with `OnDiskMarkSweepStrategy`.
- Refcount GC removes the entries of the versions it drops from the roots index; mark-sweep leaves the index as it is.

Retention policies: `RetentionPolicy.keepLatestN(n)`, `keepVersions(Collection<Long>)`, `keepRange(from, to)`, or any
lambda `index -> Collection<byte[]>` returning roots to keep.

`GcManager.runAsync(strategy, policy, options, report -> ...)` runs the same GC on a daemon thread and passes the
report to the callback.

## API Overview

### RocksDbStateTrees

```java
RocksDbStateTrees(String dbPath)
RocksDbStateTrees(String dbPath, StorageMode mode)
RocksDbStateTrees(String dbPath, NamespaceOptions namespace)
RocksDbStateTrees(String dbPath, NamespaceOptions namespace, StorageMode mode)
RocksDbStateTrees(String dbPath, NamespaceOptions namespace, StorageMode mode, RocksDbConfig config)

RocksDbNodeStore nodeStore()
RocksDbRootsIndex rootsIndex()
RocksDB db()
StorageMode storageMode()

long putRootWithRefcount(byte[] root)        // MULTI_VERSION
void putRootSnapshot(byte[] root)            // SINGLE_VERSION, always version 0
byte[] getCurrentRoot()                      // SINGLE_VERSION
GcReport cleanupOrphanedNodes(GcOptions o)   // SINGLE_VERSION
```

### RocksDbNodeStore

```java
RocksDbNodeStore(String dbPath)
RocksDbNodeStore(String dbPath, NamespaceOptions namespace)
RocksDbNodeStore(RocksDB db, ColumnFamilyHandle cfNodes)
RocksDbNodeStore(RocksDB db, ColumnFamilyHandle cfNodes, KeyPrefixer prefixer)

byte[] get(byte[] hash)
void put(byte[] hash, byte[] nodeBytes)
void delete(byte[] hash)
<T> T withBatch(WriteBatch batch, Callable<T> work)   // stage writes; the caller writes the batch
```

### RocksDbRootsIndex

```java
void put(long version, byte[] root)    // also updates LATEST and the last-version counter
byte[] get(long version)
byte[] latest()
long lastVersion()
long nextVersion()
NavigableMap<Long, byte[]> listAll()
NavigableMap<Long, byte[]> listRange(long fromInclusive, long toInclusive)
void deleteVersion(long version)
```

### RocksDbMptSession

```java
RocksDbMptSession.of(stateTrees)                                            // or of(nodeStore)
RocksDbMptSession.of(stateTrees, Options.builder().disableWal(true).build())
<T> T write(Callable<T> work)   // runs work inside nodeStore.withBatch and commits the batch
void write(Runnable work)
```

### GcOptions and GcReport

```java
GcOptions: boolean dryRun; int deleteBatchSize; boolean useSnapshot (default true); LongConsumer progress
GcReport:  long marked (-1 for refcount); long total; long deleted; long durationMillis
```

## Storage Layout

```
nodes CF:  [ns][node hash]          -> CBOR-encoded node
           [ns][0xF0][node hash]    -> refcount, 8-byte big-endian (refcount GC only)
roots CF:  [ns][version, 8-byte BE] -> root hash
           [ns]"LATEST"             -> latest root
           [ns]"VERSION"            -> last version number
           [ns]"_storage_mode"      -> MULTI_VERSION | SINGLE_VERSION
```

`[ns]` is the one-byte key prefix of the namespace (`0x00` by default). With `NamespaceOptions.columnFamily(...)`
the column family names carry the namespace instead.

## Thread Safety

- `MpfTrie` is not thread-safe; use one instance per thread or synchronize externally.
- The backend has no concurrency control: serialize writers of a namespace, and pause them while GC runs.
- Batches opened with `withBatch` / `RocksDbMptSession.write` are bound to the calling thread.

## Tools

The `load-tools` module has a GC CLI (`com.bloxbean.cardano.vds.tools.mpf.GcTool`) and a load tester
(`com.bloxbean.cardano.vds.tools.mpf.MptLoadTester`); see [load-tools/README.md](../load-tools/README.md).

## Related Modules

- [merkle-patricia-forestry](../merkle-patricia-forestry/) - MPF core
- [rocksdb-core](../rocksdb-core/) - RocksDB configuration and namespace utilities
- [merkle-patricia-forestry-rdbms](../merkle-patricia-forestry-rdbms/) - SQL backend

## References

- [RocksDB](https://rocksdb.org/)
- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry)
