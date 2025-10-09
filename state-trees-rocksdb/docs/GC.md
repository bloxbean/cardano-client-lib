# GC Strategies for RocksDB-backed MPT

This module provides two GC strategies, each optimized for different workloads:

## Package Structure

```
gc/
├── GcManager.java           (main orchestrator)
├── GcStrategy.java          (strategy interface)
├── GcOptions.java           (configuration)
├── GcReport.java            (result metrics)
├── RetentionPolicy.java     (version retention policy)
│
├── strategy/                (GC strategy implementations)
│   ├── RefcountGcStrategy.java
│   └── OnDiskMarkSweepStrategy.java
│
└── internal/                (internal utilities - not public API)
    ├── NodeRefParser.java
    └── RocksDbGc.java
```

**Import patterns:**
```java
// Core GC API
import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.*;

// Strategy implementations
import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.*;
```

## SPI
- `GcStrategy`: runs GC against a `RocksDbNodeStore` and `RocksDbRootsIndex`.
- `RetentionPolicy`: resolves root hashes to retain (keepLatestN, keepVersions, keepRange).
- `GcOptions`: tuning knobs (dryRun, deleteBatchSize, useSnapshot, progress callback).
- `GcReport`: summary metrics (marked, deleted, total, durationMillis).
- `GcManager`: runs strategies sync or async.

## Which Strategy Should I Use?

### For On-Chain Blockchain Applications → Use `RefcountGcStrategy`

**Best for:** Smart contracts, validators, on-chain state machines

**Characteristics:**
- ✅ **Predictable latency**: GC runs in hot path (blocking commits)
- ✅ **Incremental**: Work scales with Δ (changed nodes), not total dataset size
- ✅ **Atomic**: GC integrated into commit transaction (no race conditions)
- ❌ **Moderate throughput**
- ❌ **Hot path overhead**

**Use when:**
- Block production with ~20 second slots (Cardano)
- Updates affect <5k accounts per block
- Predictable, bounded latency is critical

### For Off-Chain High-Throughput Applications → Use `OnDiskMarkSweepStrategy`

**Best for:** Indexers, off-chain databases, data aggregation pipelines

**Characteristics:**
- ✅ **High throughput**
- ✅ **Low memory**: Mark set persisted to disk (temporary CF), not RAM
- ✅ **Background GC**: Runs async during off-peak hours (doesn't block commits)
- ❌ **Variable latency**: GC can take 30-45 minutes for large datasets
- ❌ **Requires scheduling**: Need to plan GC windows

**Use when:**
- Indexing blockchain history at high throughput (>50k ops/s target)
- Commit latency can be *<100ms (no GC in hot path)
- Can schedule GC during off-peak h*ours

---

## Strategy Details

### 1) RefcountGcStrategy (Incremental Reference Counting)
**Implementation:** `com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.RefcountGcStrategy`

**How it works:**
- Maintains refcounts in the `nodes` CF using prefixed keys (`0xF0 + nodeHash -> u64 refcount`)
- On commit of new root: `incrementAll(newRoot)` traverses new nodes, increments refcounts
- When pruning old root: `decrementAll(oldRoot)` traverses old nodes, decrements refcounts
- Nodes with refcount == 0 are deleted immediately

**Recommended workflow:**
```java
try (var wb = new WriteBatch(); var wo = new WriteOptions()) {
  store.withBatch(wb, () -> {
    // 1. Mutate MPT
    trie.put(key, value);

    // 2. Persist new root
    long version = roots.nextVersion();
    roots.put(version, trie.getRootHash());

    // 3. Increment refcounts for new root (in same batch)
    RefcountGcStrategy.incrementAll(store.db(), store.nodesHandle(),
                                    store.nodesHandle(), trie.getRootHash(), wb);

    // 4. Optionally decrement old root (if keeping only latest N)
    if (version > N) {
      byte[] oldRoot = roots.get(version - N - 1);
      RefcountGcStrategy.decrementAll(store.db(), store.nodesHandle(),
                                      store.nodesHandle(), oldRoot, wb);
    }
    return null;
  });
  store.db().write(wo, wb);
}
```

**Performance characteristics:**
- Commit latency: ~250ms (includes GC overhead)
- Throughput: ~6.2k ops/s
- Memory: O(changed nodes) in-memory seen set during traversal
- GC cost: Amortized across commits (no separate GC phase)

---

### 2) OnDiskMarkSweepStrategy (Background Mark-and-Sweep)
**Implementation:** `com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.strategy.OnDiskMarkSweepStrategy`

**How it works:**
- **Mark phase**: BFS traversal from retained roots; write "seen" node hashes to temporary `marks` CF
- **Sweep phase**: Iterate all nodes in `nodes` CF; delete nodes NOT in `marks` (batched deletes)
- Optional RocksDB snapshot for consistent view during GC
- Drops `marks` CF at end to reclaim space

**Recommended workflow:**
```java
// Run GC during off-peak hours (scheduled job)
var manager = new GcManager(store, roots);
var policy = RetentionPolicy.keepLatestN(1); // or keepRange(from, to)
var opts = new GcOptions();
opts.deleteBatchSize = 20_000;
opts.useSnapshot = true; // Consistent view
opts.progress = deleted -> {
  if (deleted % 10000 == 0) {
    log.info("GC progress: {} nodes deleted", deleted);
  }
};

// Run async in background thread
GcReport report = manager.runAsync(new OnDiskMarkSweepStrategy(), policy, opts)
  .get(); // or register callback
```

**Performance characteristics:**
- Commit latency: ~100ms (no GC in hot path)
- Throughput: 23k ops/s single-threaded, 93k ops/s with 4 threads
- Memory: O(1) heap usage (mark set on disk)
- GC duration: 30-45 minutes for 50M nodes (depends on dataset size)

---

## Atomic Batch Composition

Both strategies support atomic composition with application writes:

- `RocksDbNodeStore.withBatch(WriteBatch, Callable)` - Execute MPT operations in a batch
- `RocksDbRootsIndex.withBatch(WriteBatch, Callable)` - Execute root index operations in a batch

This allows composing MPT mutations + GC operations + your app writes into one atomic `db.write(writeOptions, batch)`.

**Example: Atomic commit with RefcountGC**
```java
try (var wb = new WriteBatch(); var wo = new WriteOptions()) {
  store.withBatch(wb, () -> {
    // Mutate MPT
    trie.put(accountKey, accountData);

    // Persist new root
    roots.withBatch(wb, () -> {
      long v = roots.nextVersion();
      roots.put(v, trie.getRootHash());
      return null;
    });

    // Increment refcounts for new root (atomic with commit)
    RefcountGcStrategy.incrementAll(store.db(), store.nodesHandle(),
                                    store.nodesHandle(), trie.getRootHash(), wb);

    // Optionally decrement old root
    // RefcountGcStrategy.decrementAll(..., oldRoot, wb);

    return null;
  });
  store.db().write(wo, wb); // Atomic commit
}
```

---

## Retention Policies

Choose how long to retain historical versions:

- **`RetentionPolicy.keepLatestN(n)`**: Keep only the latest N versions
  ```java
  RetentionPolicy.keepLatestN(1); // Single-version deployment (smallest disk footprint)
  RetentionPolicy.keepLatestN(100); // Keep last 100 blocks for reorg handling
  ```

- **`RetentionPolicy.keepVersions(list)`**: Retain specific versions
  ```java
  RetentionPolicy.keepVersions(List.of(1000L, 2000L, 3000L)); // Snapshots at milestones
  ```

- **`RetentionPolicy.keepRange(from, to)`**: Retain version range
  ```java
  RetentionPolicy.keepRange(900L, 1000L); // Keep epoch 9 for auditing
  ```

---

## Testing and Dry-Run

Before running GC in production, always test with `dryRun` mode:

```java
GcOptions opts = new GcOptions();
opts.dryRun = true; // Reports what WOULD be deleted, but doesn't actually delete

GcReport report = manager.runSync(new OnDiskMarkSweepStrategy(), policy, opts);
System.out.println("Would delete " + report.deleted + " nodes (" +
                   (report.deleted * 100.0 / report.total) + "%)");
```

---

## Performance Tuning

### OnDiskMarkSweepStrategy Tuning
- **`deleteBatchSize`**: Number of nodes to delete per RocksDB write (default: 10,000)
  - Increase for faster sweep (but larger write batches)
  - Decrease for lower memory during sweep
  ```java
  opts.deleteBatchSize = 50_000; // Faster sweep, higher memory
  ```

- **`useSnapshot`**: Use RocksDB snapshot for consistent GC view
  - Enable for production (prevents race conditions during GC)
  - Disable for testing (slightly faster)
  ```java
  opts.useSnapshot = true; // Recommended for production
  ```

- **`progress`**: Monitor GC progress
  ```java
  opts.progress = deleted -> {
    if (deleted % 10000 == 0) {
      logger.info("GC deleted {} nodes", deleted);
    }
  };
  ```

### RefcountGcStrategy Tuning
- No tuning knobs (GC is integrated into commit path)
- Optimize by reducing version retention (fewer roots = less refcount overhead)
- Consider batching multiple application updates into single commit

---

## FAQ

**Q: Can I switch between strategies?**
A: No, once you start using RefcountGC (with refcounts persisted), you must continue using it or rebuild the database. OnDiskMarkSweep can be used on any database.

**Q: What happens if GC is interrupted?**
- **RefcountGC**: Partial commit is rolled back (atomic with RocksDB WriteBatch)
- **OnDiskMarkSweep**: Safe to re-run (temporary `marks` CF is dropped and recreated)

**Q: How do I know which strategy I'm using?**
Check for the presence of refcount keys (prefixed with `0xF0`) in your `nodes` CF. If present, you're using RefcountGC.

**Q: Can I run both strategies?**
No. Choose one strategy per deployment. Mixing strategies will corrupt refcounts.
