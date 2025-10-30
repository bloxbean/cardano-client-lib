# Garbage Collection Strategies

**Module:** `merkle-patricia-trie-rocksdb`

## Table of Contents

1. [Overview](#overview)
2. [Reference Counting GC](#reference-counting-gc)
3. [Mark-and-Sweep GC](#mark-and-sweep-gc)
4. [Retention Policies](#retention-policies)
5. [Performance Analysis](#performance-analysis)
6. [Design Trade-offs](#design-trade-offs)
7. [GcTool CLI](#gctool-cli)
8. [References](#references)

---

## 1. Overview

### 1.1 Why Garbage Collection?

**Problem:** Copy-on-write creates new nodes on every update, old nodes become unreachable

**Without GC:**
- 1M updates → 20-30M stale nodes (10-20 GB)
- Storage grows indefinitely
- Performance degrades (more data to scan)

**With GC:**
- Reclaim space from old versions
- Maintain bounded storage size
- Keep performance consistent

### 1.2 GC Strategies Comparison

| Strategy | Tracking Overhead | GC Speed | Space Efficiency | Use Case |
|----------|------------------|----------|------------------|----------|
| **Reference Counting** | High (refcount per node) | Fast (incremental) | Good (immediate) | Multi-version, frequent updates |
| **Mark-and-Sweep** | Low (no extra data) | Slow (full scan) | Excellent (reclaim all) | Single-version, batch cleanup |
| **Retention Policy** | None (time-based) | N/A (prune old) | Good (predictable) | Historical queries with time limit |

---

## 2. Reference Counting GC

### 2.1 Algorithm Overview

**Concept:** Track how many tree roots reference each node

**Data Structure:**
```
NodeRefcount: node_hash → refcount
```

**Rules:**
1. New node: refcount = 1 (referenced by new root)
2. Shared node (across versions): refcount++
3. Node removed from tree: refcount--
4. When refcount = 0: Delete node

### 2.2 Implementation

**Schema:**
```sql
CREATE TABLE node_refcounts (
    node_hash BYTEA PRIMARY KEY,
    refcount INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_refcount_zero ON node_refcounts (refcount)
WHERE refcount = 0;
```

**Increment Refcount:**
```java
public void incrementRefcount(byte[] nodeHash) {
    // Atomic increment
    executeUpdate(
        "INSERT INTO node_refcounts (node_hash, refcount) VALUES (?, 1) " +
        "ON CONFLICT (node_hash) DO UPDATE SET refcount = node_refcounts.refcount + 1",
        nodeHash
    );
}
```

**Decrement Refcount:**
```java
public void decrementRefcount(byte[] nodeHash) {
    executeUpdate(
        "UPDATE node_refcounts SET refcount = refcount - 1 WHERE node_hash = ?",
        nodeHash
    );
}
```

**Collect Garbage:**
```java
public int collectGarbage() {
    // Find nodes with refcount = 0
    List<byte[]> deadNodes = query(
        "SELECT node_hash FROM node_refcounts WHERE refcount = 0"
    );

    // Delete nodes and refcount entries
    try (WriteBatch batch = new WriteBatch()) {
        for (byte[] hash : deadNodes) {
            batch.delete(nodesColumnFamily, hash);
        }
        db.write(writeOptions, batch);
    }

    // Clean up refcount table
    executeUpdate("DELETE FROM node_refcounts WHERE refcount = 0");

    return deadNodes.size();
}
```

### 2.3 Refcount Maintenance During Operations

**Insert Operation:**
```
1. Create new nodes (refcount = 1 each)
2. Delete old nodes on path:
   - decrementRefcount(old_node_hash)
   - If refcount = 0, mark for deletion
3. Increment refcount for shared nodes:
   - If branch child already exists: incrementRefcount(child_hash)
```

**Delete Operation:**
```
1. Navigate to leaf, delete it: decrementRefcount(leaf_hash)
2. For each parent on path:
   - decrementRefcount(old_parent_hash)
   - Create new parent (refcount = 1)
3. Compress tree (merge/remove unnecessary nodes)
```

**Root Change:**
```
1. New root: incrementRefcount(new_root_hash)
2. Old root: decrementRefcount(old_root_hash)
3. Run GC if needed
```

### 2.4 Complexity

- **Increment/Decrement:** O(1) (index lookup + update)
- **GC Scan:** O(n) where n = nodes with refcount = 0
- **Space Overhead:** 4 bytes per node (refcount integer)

**For 1M nodes:**
- Refcount storage: 4 MB
- Index overhead: ~1-2 MB
- **Total: ~6 MB** (acceptable)

---

## 3. Mark-and-Sweep GC

### 3.1 Algorithm Overview

**Concept:** Periodically scan reachable nodes, delete unreachable

**Phases:**
1. **Mark:** Starting from active roots, mark all reachable nodes
2. **Sweep:** Delete all unmarked nodes

**Advantage:** No ongoing overhead (no refcount tracking)

**Disadvantage:** Requires full tree traversal (slow for large trees)

### 3.2 Implementation

**Mark Phase:**
```java
public Set<byte[]> markReachable(List<byte[]> rootHashes) {
    Set<byte[]> reachable = new HashSet<>();
    Queue<byte[]> queue = new LinkedList<>(rootHashes);

    while (!queue.isEmpty()) {
        byte[] nodeHash = queue.poll();

        if (reachable.contains(nodeHash)) {
            continue; // Already marked
        }

        reachable.add(nodeHash);

        // Load node and mark children
        byte[] nodeData = nodeStore.get(nodeHash);
        if (nodeData == null) continue;

        Node node = decode(nodeData);
        node.accept(new NodeVisitor<Void>() {
            public Void visitBranch(BranchNode branch) {
                for (byte[] child : branch.getChildren()) {
                    if (child != null && child.length > 0) {
                        queue.offer(child);
                    }
                }
                return null;
            }

            public Void visitExtension(ExtensionNode ext) {
                queue.offer(ext.getChild());
                return null;
            }

            public Void visitLeaf(LeafNode leaf) {
                // Leaf has no children
                return null;
            }
        });
    }

    return reachable;
}
```

**Sweep Phase:**
```java
public int sweep(Set<byte[]> reachable) {
    int deleted = 0;

    try (RocksIterator it = db.newIterator(nodesColumnFamily)) {
        it.seekToFirst();

        while (it.isValid()) {
            byte[] nodeHash = it.key();

            if (!reachable.contains(nodeHash)) {
                nodeStore.delete(nodeHash);
                deleted++;
            }

            it.next();
        }
    }

    return deleted;
}
```

**Complete GC:**
```java
public GcResult runGarbageCollection() {
    long startTime = System.currentTimeMillis();

    // 1. Get active root hashes
    List<byte[]> rootHashes = rootsIndex.getAllRoots();

    // 2. Mark reachable nodes
    Set<byte[]> reachable = markReachable(rootHashes);

    // 3. Sweep unreachable nodes
    int deleted = sweep(reachable);

    long duration = System.currentTimeMillis() - startTime;

    return new GcResult(deleted, reachable.size(), duration);
}
```

### 3.3 Optimizations

**Incremental Mark:**
```java
// Process in chunks to avoid OOM
public Set<byte[]> markReachableIncremental(List<byte[]> rootHashes, int chunkSize) {
    Set<byte[]> reachable = new HashSet<>();
    Queue<byte[]> queue = new LinkedList<>(rootHashes);

    int processed = 0;
    while (!queue.isEmpty()) {
        byte[] nodeHash = queue.poll();

        if (reachable.contains(nodeHash)) continue;

        reachable.add(nodeHash);
        // ... mark children ...

        processed++;
        if (processed % chunkSize == 0) {
            // Yield CPU, allow other operations
            Thread.yield();
        }
    }

    return reachable;
}
```

**Bloom Filter (Space Optimization):**
```java
// Use bloom filter for marked set (reduce memory)
BloomFilter<byte[]> marked = BloomFilter.create(
    Funnels.byteArrayFunnel(),
    estimatedNodes,
    0.01  // 1% false positive rate
);

// In mark phase:
marked.put(nodeHash);

// In sweep phase:
if (!marked.mightContain(nodeHash)) {
    // Definitely not reachable
    delete(nodeHash);
}
```

**Memory Savings:**
- Full HashSet: 32 bytes per node × 1M = 32 MB
- Bloom filter (1% FP): ~1.2 MB
- **96% memory reduction**

### 3.4 Complexity

- **Mark Phase:** O(R) where R = reachable nodes
- **Sweep Phase:** O(N) where N = total nodes
- **Total:** O(N + R) = O(N)
- **Space:** O(R) for marked set (or O(1) with bloom filter)

**For 1M total nodes, 800K reachable:**
- Mark: ~5-10 seconds
- Sweep: ~2-5 seconds
- **Total: ~10-15 seconds**

---

## 4. Retention Policies

### 4.1 Time-Based Retention

**Concept:** Delete versions older than N days

**Schema:**
```sql
CREATE TABLE version_metadata (
    version BIGINT PRIMARY KEY,
    root_hash BYTEA NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_version_timestamp ON version_metadata (timestamp);
```

**Retention Policy:**
```java
public void enforceRetentionPolicy(Duration retentionPeriod) {
    Instant cutoff = Instant.now().minus(retentionPeriod);

    // Find versions older than cutoff
    List<Long> oldVersions = query(
        "SELECT version FROM version_metadata WHERE timestamp < ?",
        Timestamp.from(cutoff)
    );

    // Delete each old version
    for (long version : oldVersions) {
        deleteVersion(version);
    }
}

private void deleteVersion(long version) {
    // 1. Get root for this version
    byte[] rootHash = getRootHash(version);

    // 2. Mark stale (for other GC strategies)
    markVersionAsStale(version);

    // 3. Optionally run GC immediately
    runGarbageCollection();
}
```

**Example Policies:**
```java
// Keep last 30 days
enforceRetentionPolicy(Duration.ofDays(30));

// Keep last 1000 versions
enforceVersionCountLimit(1000);

// Keep last 100 GB
enforceSizeLimit(100L * 1024 * 1024 * 1024);
```

### 4.2 Version Count Retention

**Concept:** Keep only last N versions

```java
public void enforceVersionCountLimit(int maxVersions) {
    List<Long> allVersions = query(
        "SELECT version FROM version_metadata ORDER BY version DESC"
    );

    if (allVersions.size() <= maxVersions) {
        return; // Under limit
    }

    // Delete excess old versions
    List<Long> toDelete = allVersions.subList(maxVersions, allVersions.size());
    for (long version : toDelete) {
        deleteVersion(version);
    }
}
```

### 4.3 Size-Based Retention

**Concept:** Delete old versions when total size exceeds limit

```java
public void enforceSizeLimit(long maxSizeBytes) {
    long currentSize = getCurrentStorageSize();

    if (currentSize <= maxSizeBytes) {
        return; // Under limit
    }

    // Delete oldest versions until under limit
    List<Long> versions = query(
        "SELECT version FROM version_metadata ORDER BY version ASC"
    );

    for (long version : versions) {
        if (getCurrentStorageSize() <= maxSizeBytes) {
            break;
        }
        deleteVersion(version);
    }
}
```

---

## 5. Performance Analysis

### 5.1 Benchmark Results

**Setup:** 1M keys, 100 updates/sec, 10 GB data

| Strategy | GC Overhead | Storage Reclaimed | GC Frequency |
|----------|-------------|-------------------|--------------|
| Refcount | 2-5% (continuous) | 95-99% | Incremental |
| Mark-Sweep | 0% (idle) + 100% (GC) | 100% | Every 1-7 days |
| Retention (30 days) | 0% | 90-95% | Daily |

### 5.2 Space Overhead

| Strategy | Per-Node Overhead | Total (1M nodes) |
|----------|------------------|------------------|
| Refcount | 4 bytes | 4 MB |
| Mark-Sweep | 0 bytes | 0 MB |
| Retention | 0 bytes | 0 MB |

### 5.3 GC Latency

**Refcount (Incremental):**
- Per-operation: +50-100 µs (refcount update)
- Amortized: No visible GC pauses

**Mark-Sweep (Stop-the-World):**
- GC pause: 10-30 seconds (1M nodes)
- Can run during maintenance window
- Requires careful scheduling

**Retention Policy:**
- GC pause: Depends on version count
- 100 versions: ~5-10 seconds
- Can combine with mark-sweep

---

## 6. Design Trade-offs

### 6.1 When to Use Refcount GC

**Advantages:**
- Incremental (no GC pauses)
- Immediate space reclamation
- Good for multi-version workloads

**Disadvantages:**
- Overhead on every operation (2-5%)
- Complex bookkeeping
- Risk of refcount bugs (leaks or premature deletion)

**Best For:**
- Production systems with uptime requirements
- Multi-version databases
- Frequent updates

### 6.2 When to Use Mark-and-Sweep GC

**Advantages:**
- No overhead during normal operations
- Simple implementation
- Guaranteed to find all garbage

**Disadvantages:**
- Stop-the-world pause (10-30 seconds)
- Requires maintenance window
- Higher memory usage during GC

**Best For:**
- Single-version trees
- Batch processing systems
- Maintenance-window deployments

### 6.3 When to Use Retention Policies

**Advantages:**
- Predictable storage usage
- Simple to implement
- No complex tracking

**Disadvantages:**
- Deletes useful data (old versions)
- Not suitable if all history needed
- Requires tuning (retention period)

**Best For:**
- Time-series data (recent is important)
- Regulatory compliance (e.g., 7-year retention)
- Combined with other GC strategies

---

## 7. GcTool CLI

### 7.1 Command-Line Interface

**Run Refcount GC:**
```bash
java -jar gc-tool.jar --mode=refcount --db-path=/data/rocksdb --dry-run=false
```

**Run Mark-and-Sweep GC:**
```bash
java -jar gc-tool.jar --mode=mark-sweep --db-path=/data/rocksdb --roots-cf=roots
```

**Enforce Retention Policy:**
```bash
java -jar gc-tool.jar --mode=retention --db-path=/data/rocksdb \
  --retention-days=30 --dry-run=false
```

**Dry Run (Estimate Savings):**
```bash
java -jar gc-tool.jar --mode=mark-sweep --db-path=/data/rocksdb --dry-run=true
# Output:
# Reachable nodes: 800,000
# Unreachable nodes: 200,000
# Estimated space to reclaim: 15.2 GB
```
