# Merkle Patricia Trie - RocksDB Backend

RocksDB persistence for Merkle Patricia Trie with garbage collection support.

## Overview

This module provides a production-ready RocksDB backend for MPT, including automatic garbage collection, batch operations, and support for both single-version and multi-version storage modes.

## Key Features

- **RocksDB Persistence** - High-performance embedded database
- **Garbage Collection** - Multiple GC strategies (refcount, mark-sweep)
- **Multi-Tree Support** - Manage multiple independent tries in one database
- **Batch Operations** - Atomic batch writes with rollback
- **Storage Modes** - Single-version or multi-version
- **GC Tool** - Command-line utility for GC testing and debugging

## Quick Start

### Basic Usage

```java
import com.bloxbean.cardano.vds.mpt.rocksdb.RocksDbStateTrees;
import com.bloxbean.cardano.vds.mpt.MerklePatriciaTrie;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// Create RocksDB-backed state trees
RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpt");

// Get a trie
MerklePatriciaTrie trie = stateTrees.getTree("accounts");

// Use it
trie.put("alice".getBytes(), "balance:100".getBytes());
trie.put("bob".getBytes(), "balance:200".getBytes());

// Commit changes
stateTrees.commit("accounts");

// Cleanup
stateTrees.close();
```

### Single Trie Usage

```java
import com.bloxbean.cardano.vds.mpt.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources;

// Initialize RocksDB
RocksDbResources resources = RocksDbResources.create(Paths.get("data/mpt"));
RocksDbNodeStore nodeStore = new RocksDbNodeStore(resources.getDb());

// Create trie
HashFunction hashFn = Blake2b256::digest;
MerklePatriciaTrie trie = new MerklePatriciaTrie(nodeStore, hashFn);

// Insert data
trie.put("key1".getBytes(), "value1".getBytes());
byte[] rootHash = trie.getRootHash();

// Cleanup
resources.close();
```

### Multi-Version Mode

```java
import com.bloxbean.cardano.vds.core.api.StorageMode;

// Enable versioning
RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpt", StorageMode.MULTI_VERSION);

MerklePatriciaTrie trie = stateTrees.getTree("ledger");

// Version 1
trie.put("alice".getBytes(), "100".getBytes());
stateTrees.commit("ledger");
long v1 = stateTrees.currentVersion("ledger");

// Version 2
trie.put("alice".getBytes(), "150".getBytes());
stateTrees.commit("ledger");
long v2 = stateTrees.currentVersion("ledger");

// Rollback to version 1
byte[] rootV1 = stateTrees.getRootHash("ledger", v1);
trie.setRootHash(rootV1);

byte[] value = trie.get("alice".getBytes());
// Returns "100"
```

## Garbage Collection

MPT RocksDB includes multiple GC strategies to reclaim space from stale nodes.

### Reference Counting GC

Tracks references to each node; deletes when refcount reaches zero.

```java
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.strategy.RefcountGcStrategy;

RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpt");

// Create GC manager
GcManager gcManager = new GcManager(
    stateTrees.nodeStore(),
    stateTrees.rootsIndex()
);

// Configure GC
GcOptions options = new GcOptions();
options.deleteBatchSize = 10_000;  // Delete 10K nodes per batch
options.progress = deleted -> System.out.println("Deleted: " + deleted);

// Run GC (keep latest 5 versions)
RetentionPolicy policy = RetentionPolicy.keepLatestN(5);
GcStrategy strategy = new RefcountGcStrategy();

GcReport report = gcManager.runSync(strategy, policy, options);

System.out.println("Deleted nodes: " + report.deleted);
System.out.println("Duration: " + report.durationMillis + "ms");
```

### Mark-Sweep GC

Marks reachable nodes from retained roots, sweeps unreachable ones.

```java
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;

// Mark-sweep GC (more thorough, slower)
GcStrategy strategy = new OnDiskMarkSweepStrategy();
GcReport report = gcManager.runSync(strategy, policy, options);
```

### GC Strategies Comparison

| Strategy | Speed | Accuracy | Memory | Use Case |
|----------|-------|----------|--------|----------|
| **Refcount** | Fast | Exact | Low | Production, incremental GC |
| **Mark-Sweep** | Slow | Exact | Medium | Recovery, verification |

**Recommendation**: Use `RefcountGcStrategy` for production.

### Retention Policies

```java
// Keep latest N versions
RetentionPolicy.keepLatestN(10);

// Keep versions newer than timestamp
RetentionPolicy.keepNewerThan(System.currentTimeMillis() - 7 * 24 * 3600 * 1000); // 7 days

// Keep specific versions
RetentionPolicy.keepVersions(Set.of(1L, 5L, 10L));

// Custom policy
RetentionPolicy.custom(version -> version > 100);
```

### GC Tool (CLI)

Command-line utility for testing and debugging GC:

```bash
# Generate test data (10K keys, 5 versions)
java -cp ... com.bloxbean.cardano.vds.mpt.rocksdb.tools.GcTool \
  generate /tmp/mpt 10000 5

# Show stats
java -cp ... com.bloxbean.cardano.vds.mpt.rocksdb.tools.GcTool \
  stats /tmp/mpt

# Run refcount GC (keep latest 1 version)
java -cp ... com.bloxbean.cardano.vds.mpt.rocksdb.tools.GcTool \
  gc-refcount /tmp/mpt keep-latest 1

# Run mark-sweep GC
java -cp ... com.bloxbean.cardano.vds.mpt.rocksdb.tools.GcTool \
  gc-marksweep /tmp/mpt keep-latest 1
```

## API Overview

### RocksDbStateTrees

Multi-tree management with automatic GC support.

```java
// Constructors
RocksDbStateTrees(String path)
RocksDbStateTrees(String path, StorageMode mode)
RocksDbStateTrees(RocksDB db, StorageMode mode)

// Tree operations
MerklePatriciaTrie getTree(String name)
void commit(String name)
byte[] getRootHash(String name)
byte[] getRootHash(String name, long version)

// Version management
long currentVersion(String name)
long nextVersion(String name)

// Access internals
RocksDbNodeStore nodeStore()
RocksDbRootsIndex rootsIndex()
RocksDB db()
```

### RocksDbNodeStore

Low-level node storage implementation.

```java
// Constructor
RocksDbNodeStore(RocksDB db)
RocksDbNodeStore(RocksDB db, KeyPrefixer prefixer)

// NodeStore interface
byte[] get(byte[] hash)
void put(byte[] hash, byte[] nodeBytes)
void delete(byte[] hash)

// Batch operations
void withBatch(WriteBatch batch, Callable<?> operation)

// Access
ColumnFamilyHandle nodesHandle()
KeyPrefixer keyPrefixer()
```

### GcManager

Orchestrates garbage collection.

```java
// Constructor
GcManager(RocksDbNodeStore nodeStore, RocksDbRootsIndex rootsIndex)

// Synchronous GC
GcReport runSync(GcStrategy strategy, RetentionPolicy policy, GcOptions options)

// Asynchronous GC (returns immediately)
CompletableFuture<GcReport> runAsync(GcStrategy strategy, RetentionPolicy policy, GcOptions options)
```

### GcOptions

Configuration for GC runs.

```java
GcOptions options = new GcOptions();
options.deleteBatchSize = 10_000;        // Nodes per batch
options.progress = n -> log.info("{}", n); // Progress callback
options.dryRun = false;                  // Set true to simulate
options.parallelism = 4;                 // Threads (mark-sweep only)
```

### GcReport

Results from GC execution.

```java
GcReport {
    long marked;         // Nodes marked as reachable
    long deleted;        // Nodes deleted
    long total;          // Total nodes processed
    long durationMillis; // Execution time
}
```

## Batch Operations

### Efficient Batch Writes

```java
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

RocksDbNodeStore nodeStore = stateTrees.nodeStore();

try (WriteBatch batch = new WriteBatch();
     WriteOptions writeOpts = new WriteOptions()) {

    nodeStore.withBatch(batch, () -> {
        // All operations go into the batch
        trie.put("key1".getBytes(), "value1".getBytes());
        trie.put("key2".getBytes(), "value2".getBytes());
        trie.put("key3".getBytes(), "value3".getBytes());
        return null;
    });

    // Atomic commit
    stateTrees.db().write(writeOpts, batch);
}
```

### Session-Based Batching

```java
import com.bloxbean.cardano.vds.mpt.rocksdb.RocksDbMptSession;

// Session automatically batches writes
try (RocksDbMptSession session = new RocksDbMptSession(stateTrees)) {
    MerklePatriciaTrie trie = session.getTree("accounts");

    // Multiple operations batched
    trie.put("alice".getBytes(), "100".getBytes());
    trie.put("bob".getBytes(), "200".getBytes());
    trie.put("charlie".getBytes(), "300".getBytes());

    // Atomic commit
    session.commit("accounts");
}
```

## Storage Schema

RocksDB stores MPT data in multiple column families:

### Column Families

```
nodes:     hash → CBOR-encoded node
roots:     (tree_name, version) → root_hash
metadata:  key → value (configuration, stats)
refcounts: hash → int (for refcount GC)
```

### Key Encoding

```
Node key:     prefix + hash (32 bytes Blake2b-256)
Root key:     prefix + tree_name + version (varint)
Refcount key: prefix + hash
```

## Performance

### Benchmarks

On commodity hardware (Intel i7-12700K, NVMe SSD):

| Operation | Throughput | Latency (p50) |
|-----------|-----------|---------------|
| Insert (single) | 50K ops/sec | 20μs |
| Insert (batch 1K) | 500K ops/sec | 2ms/batch |
| Lookup | 100K ops/sec | 10μs |
| Prefix scan (100 keys) | 50K scans/sec | 20μs |
| GC (refcount) | 200K nodes/sec | - |
| GC (mark-sweep) | 50K nodes/sec | - |

### Tuning Tips

**Write-heavy workload:**
```java
Options options = new Options()
    .setWriteBufferSize(128 * 1024 * 1024)  // 128 MB
    .setMaxWriteBufferNumber(4);

RocksDbResources.builder()
    .path(path)
    .options(options)
    .build();
```

**Read-heavy workload:**
```java
BlockBasedTableConfig tableConfig = new BlockBasedTableConfig()
    .setBlockSize(64 * 1024)  // 64 KB blocks
    .setCacheIndexAndFilterBlocks(true)
    .setFilterPolicy(new BloomFilter(10));

Options options = new Options()
    .setTableFormatConfig(tableConfig);
```

## Gradle Dependency

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:merkle-patricia-trie-rocksdb:0.8.0'

    // RocksDB is included transitively
}
```

## Design Documentation

- [MPT RocksDB Design](docs/design-mpt-rocksdb.md) - Storage schema and implementation
- [Garbage Collection](docs/gc.md) - GC strategies and algorithms
- [Core MPT Design](../merkle-patricia-trie/docs/design-mpt.md) - MPT algorithm

## Production Considerations

### 1. Regular GC

```java
// Schedule GC to run daily
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

scheduler.scheduleAtFixedRate(() -> {
    try {
        GcReport report = gcManager.runSync(
            new RefcountGcStrategy(),
            RetentionPolicy.keepLatestN(7),  // Keep 1 week
            new GcOptions()
        );
        logger.info("GC completed: deleted {} nodes", report.deleted);
    } catch (Exception e) {
        logger.error("GC failed", e);
    }
}, 1, 24, TimeUnit.HOURS);
```

### 2. Monitoring

```java
// Monitor RocksDB stats
String stats = db.getProperty("rocksdb.stats");
String estimatedSize = db.getProperty("rocksdb.total-sst-files-size");

logger.info("RocksDB stats: {}", stats);
logger.info("Total size: {} bytes", estimatedSize);
```

### 3. Backup and Restore

```java
// Backup
BackupEngine backupEngine = BackupEngine.open(env, new BackupEngineOptions(backupPath));
backupEngine.createNewBackup(db);
backupEngine.close();

// Restore
BackupEngine backupEngine = BackupEngine.open(env, new BackupEngineOptions(backupPath));
backupEngine.restoreDbFromLatestBackup(dbPath, dbPath, new RestoreOptions(false));
backupEngine.close();
```

### 4. Graceful Shutdown

```java
// Ensure all writes are flushed
db.flush(new FlushOptions().setWaitForFlush(true));

// Close in correct order
stateTrees.close();  // Closes DB and resources
```

## Thread Safety

- **RocksDbStateTrees**: Thread-safe for reads, serialize writes
- **RocksDbNodeStore**: Thread-safe for reads, batch writes need coordination
- **MerklePatriciaTrie**: NOT thread-safe, use separate instances per thread
- **GcManager**: Thread-safe, can run GC concurrently with reads

## Related Modules

- [merkle-patricia-trie](../merkle-patricia-trie/) - MPT core algorithm
- [rocksdb-core](../rocksdb-core/) - RocksDB utilities
- [merkle-patricia-trie-rdbms](../merkle-patricia-trie-rdbms/) - Alternative: SQL backend

## Tools

- **GcTool** - CLI for GC testing (see `tools/GcTool.java`)
- **MptLoadTester** - Performance benchmarking tool (see `tools/MptLoadTester.java`)

## References

- [RocksDB](https://rocksdb.org/) - Embedded key-value store
- [MPT Specification](https://ethereum.github.io/yellowpaper/paper.pdf) - Ethereum Yellow Paper
