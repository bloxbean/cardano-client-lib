# Jellyfish Merkle Tree (JMT) Implementation Guide

A comprehensive guide to the Jellyfish Merkle Tree implementation in the state-trees module, following Diem's reference architecture.

## Table of Contents

- [What is Jellyfish Merkle Tree?](#what-is-jellyfish-merkle-tree)
- [Why Use JMT?](#why-use-jmt)
- [Key Concepts](#key-concepts)
- [Architecture Overview](#architecture-overview)
- [Quick Start](#quick-start)
- [RocksDB Integration](#rocksdb-integration)
- [Advanced Features](#advanced-features)
- [Performance Tuning](#performance-tuning)
- [Production Operations](#production-operations)
- [Code Examples](#code-examples)

## What is Jellyfish Merkle Tree?

**Jellyfish Merkle Tree (JMT)** is a cryptographically authenticated data structure originally developed by Diem (formerly Libra) for blockchain state management. It's an append-only, versioned Merkle tree optimized for:

- **Versioned State**: Every update creates a new version while preserving historical state
- **Efficient Proofs**: Cryptographic proofs of inclusion/non-inclusion for any key at any version
- **Copy-on-Write**: Structural sharing between versions minimizes storage overhead
- **Hexary (16-way) Branching**: Optimal tree depth and proof size

Think of it as a **versioned cryptographic key-value store** where:
- Each commit creates an immutable snapshot with a unique root hash
- Historical states can be queried at any version
- Cryptographic proofs verify data authenticity
- Stale nodes can be pruned to reclaim storage

## Why Use JMT?

### 🔐 **Cryptographic Authentication**
- Every node is cryptographically hashed
- Root hash represents the entire state at a version
- Proofs enable trustless verification of state data

### 📜 **Versioned History**
- Multi-version concurrency control (MVCC) semantics
- Query historical state at any version
- Support for blockchain reorganizations via `truncateAfter`

### 💾 **Storage Efficiency**
- Structural sharing between versions (copy-on-write)
- Hexary branching minimizes tree depth
- Efficient pruning of stale nodes

### ⚡ **High Performance**
- Optimized RocksDB backend for persistence
- Optional caching for hot paths
- Batch operations for throughput
- Streaming commit architecture

### 🔗 **Blockchain Ready**
- Used in production by Diem/Aptos
- Built-in support for state proofs
- Rollback support for chain reorganizations
- Reference-counted garbage collection

## Key Concepts

### 1. Versions

Every commit to the JMT creates a new **version** (monotonically increasing integer):

```
Version 0: Empty tree
Version 1: put("alice" → "100")
Version 2: put("bob" → "200"), put("alice" → "150")
Version 3: delete("alice")
```

Each version has a unique **root hash** representing the complete state.

### 2. NodeKey: Path + Version

Nodes are identified by `NodeKey(NibblePath, Version)`:

```java
// Root node at version 5
NodeKey rootKey = NodeKey.of(NibblePath.EMPTY, 5);

// Internal node at path [6,8] version 5
NodeKey internalKey = NodeKey.of(NibblePath.of(6, 8), 5);
```

### 3. Node Types

JMT has two node types:

**Internal Node (JmtInternalNode)**:
- 16-way branching (one child per nibble 0-F)
- Stores child hashes in compressed bitmap format
- Optional compressed path for single-child chains

**Leaf Node (JmtLeafNode)**:
- Stores key hash and value hash
- Terminal node in the tree
- Represents actual key-value data

### 4. TreeCache: Batch-Local State

`TreeCache` manages node mutations during a commit:
- **Staged nodes**: New/modified nodes in current operation
- **Frozen nodes**: Committed nodes from current batch
- **Storage lookup**: Falls back to store for older versions

### 5. Stale Node Tracking

When a node is updated:
1. Old version is marked as **stale** (deleted)
2. New version is created at current version
3. Stale nodes can be pruned later

This enables efficient garbage collection without affecting active versions.

## Architecture Overview

### Core Components

```
┌─────────────────────────────────────────────────────┐
│           JellyfishMerkleTree (Core Logic)          │
│  - put(version, updates) → CommitResult             │
│  - get(key) / get(key, version)                     │
│  - getProof(key, version) → JmtProof                │
└─────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────┐
│              TreeCache (Batch State)                │
│  - Staged nodes (current operation)                 │
│  - Frozen nodes (committed in batch)                │
│  - Stale tracking (deleted nodes)                   │
└─────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────┐
│               JmtStore (Persistence)                │
│  - getNode(version, path) → NodeEntry               │
│  - getValue(keyHash) / getValueAt(keyHash, version) │
│  - beginCommit() → CommitBatch                      │
│  - pruneUpTo(version), truncateAfter(version)       │
└─────────────────────────────────────────────────────┘
                            │
                            ↓
┌─────────────────────────────────────────────────────┐
│          RocksDbJmtStore (RocksDB Backend)          │
│  - Column Families: nodes, values, roots, stale     │
│  - Atomic batch commits                             │
│  - Rollback indices (optional)                      │
└─────────────────────────────────────────────────────┘
```

### Diem-Compatible Design

Our implementation follows Diem's architecture with these adaptations:

| Aspect | Diem | Our Implementation |
|--------|------|-------------------|
| Storage | TreeReader/TreeWriter traits | JmtStore interface |
| Commitment | Generic hasher | CommitmentScheme interface |
| Delete-then-Create | ✓ | ✓ (via TreeCache) |
| Versioning | NodeKey(path, version) | ✓ Same pattern |
| Proof Format | Inclusion/Non-inclusion | ✓ Compatible |

## Quick Start

### 1. In-Memory Store (Testing/Development)

```java
import com.bloxbean.cardano.statetrees.jmt.*;
import com.bloxbean.cardano.statetrees.jmt.store.*;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import java.util.*;

// Create in-memory store and tree
HashFunction hashFn = Blake2b256::digest;
JmtStore store = new InMemoryJmtStore();
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

// Commit version 1
Map<byte[], byte[]> updates = new LinkedHashMap<>();
updates.put("alice".getBytes(), "100".getBytes());
updates.put("bob".getBytes(), "200".getBytes());

CommitResult result = tree.put(1L, updates);
System.out.println("Root hash: " + bytesToHex(result.rootHash()));
System.out.println("Nodes created: " + result.nodes().size());
System.out.println("Stale nodes: " + result.staleNodes().size());

// Read value
Optional<byte[]> value = tree.get("alice".getBytes());
System.out.println("Alice balance: " + new String(value.get())); // "100"

// Read historical value
Optional<byte[]> historicalValue = tree.get("alice".getBytes(), 1);
```

### 2. RocksDB Store (Production)

```java
import com.bloxbean.cardano.statetrees.rocksdb.jmt.*;

// Open RocksDB store with default options
RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
    .namespace("app1")  // Optional namespace for multi-tenant setups
    .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.SAFE)
    .enableRollbackIndex(true)  // Enable truncateAfter support
    .build();

try (RocksDbJmtStore store = RocksDbJmtStore.open("/var/data/jmt", options)) {
    JellyfishMerkleTree tree = new JellyfishMerkleTree(store, Blake2b256::digest);

    // Use tree...
    Map<byte[], byte[]> updates = Map.of(
        "key1".getBytes(), "value1".getBytes(),
        "key2".getBytes(), "value2".getBytes()
    );
    tree.put(1L, updates);
}
```

## RocksDB Integration

### Store Configuration

#### Basic Options

```java
RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
    .namespace("myapp")              // Optional: namespace for column families
    .prunePolicy(ValuePrunePolicy.SAFE)  // SAFE or AGGRESSIVE
    .enableRollbackIndex(true)       // Enable truncateAfter support
    .syncOnCommit(true)              // fsync on commit (durability)
    .syncOnPrune(true)               // fsync on prune (durability)
    .syncOnTruncate(true)            // fsync on truncate (durability)
    .build();
```

#### RocksDB Performance Tuning

```java
import com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbConfig;

// Balanced profile (default)
RocksDbConfig balanced = RocksDbConfig.balanced();

// Write-optimized profile
RocksDbConfig writeHeavy = RocksDbConfig.writeOptimized();

// Read-optimized profile
RocksDbConfig readHeavy = RocksDbConfig.readOptimized();

// Custom configuration
RocksDbConfig custom = RocksDbConfig.builder()
    .writeBufferSize(128 * 1024 * 1024)  // 128MB
    .maxWriteBufferNumber(4)
    .targetFileSizeBase(128 * 1024 * 1024)
    .maxBackgroundJobs(6)
    .compressionType(org.rocksdb.CompressionType.LZ4_COMPRESSION)
    .build();

RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
    .rocksDbConfig(custom)
    .build();
```

### Column Families

RocksDbJmtStore uses four column families:

1. **nodes**: Stores JMT nodes (NodeKey → CBOR-encoded node)
2. **values**: Stores key-value data (keyHash → value, versioned)
3. **roots**: Stores root hashes (version → rootHash)
4. **stale**: Tracks stale nodes for pruning (version+path → NodeKey)

```java
// Get column family names for a namespace
RocksDbJmtStore.ColumnFamilies cf = RocksDbJmtStore.columnFamilies("myapp");
System.out.println("Nodes CF: " + cf.nodes());     // "myapp:jmt:nodes"
System.out.println("Values CF: " + cf.values());   // "myapp:jmt:values"
System.out.println("Roots CF: " + cf.roots());     // "myapp:jmt:roots"
System.out.println("Stale CF: " + cf.stale());     // "myapp:jmt:stale"
```

### Opening Shared RocksDB Instance

```java
// Attach to existing RocksDB instance
RocksDB existingDb = ...; // Your existing database
Map<String, ColumnFamilyHandle> existingHandles = ...; // Your CF handles

RocksDbJmtStore.Options options = RocksDbJmtStore.Options.defaults();
RocksDbJmtStore store = RocksDbJmtStore.attach(existingDb, options, existingHandles);
```

## Advanced Features

### 1. Cryptographic Proofs

JMT provides efficient cryptographic proofs of inclusion/non-inclusion:

```java
// Get proof for a key at specific version
Optional<JmtProof> proofOpt = tree.getProof("alice".getBytes(), 1L);

if (proofOpt.isPresent()) {
    JmtProof proof = proofOpt.get();

    // Extract value from proof
    byte[] value = proof.value();
    System.out.println("Value in proof: " + new String(value));

    // Verify proof against root hash
    byte[] rootHash = store.rootHash(1L).orElseThrow();
    HashFunction hashFn = Blake2b256::digest;
    CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);

    boolean valid = JmtProofVerifier.verify(
        rootHash,
        "alice".getBytes(),
        value,
        proof,
        hashFn,
        commitments
    );

    System.out.println("Proof valid: " + valid);
}
```

#### Proof Types

JMT supports three types of proofs:

1. **Inclusion Proof**: Key exists in the tree
   - Contains value, branch steps, and leaf data

2. **Non-Inclusion (Empty)**: Path doesn't exist
   - Contains branch steps showing path termination

3. **Non-Inclusion (Different Leaf)**: Path leads to different key
   - Contains branch steps and conflicting leaf data

### 2. Versioned Reads

```java
// Read latest value (fastest)
Optional<byte[]> latest = tree.get("alice".getBytes());

// Read value at specific version
Optional<byte[]> atVersion1 = tree.get("alice".getBytes(), 1L);
Optional<byte[]> atVersion5 = tree.get("alice".getBytes(), 5L);

// Performance comparison:
// get(key) - Direct storage lookup, no tree traversal (~50,000 ops/sec)
// getProof(key, version) - Tree traversal + proof generation (~2,000 ops/sec)
```

**Performance Note**: Use `get()` for trusted internal operations. Use `getProof()` when cryptographic verification is required.

### 3. Pruning Stale Nodes

Remove old nodes to reclaim storage:

```java
// List stale nodes up to version 100
List<NodeKey> staleNodes = store.staleNodesUpTo(100L);
System.out.println("Stale nodes to prune: " + staleNodes.size());

// Prune stale nodes
int prunedCount = store.pruneUpTo(100L);
System.out.println("Pruned nodes: " + prunedCount);
```

#### Prune Policies

**SAFE Policy** (Default):
- Keeps most recent value ≤ prune target for each key
- Historical queries at retained versions still work
- Recommended for production

**AGGRESSIVE Policy**:
- Removes all value payloads at or below prune target
- Minimizes disk usage
- Historical reads may return empty
- Tombstones preserved for correctness

```java
RocksDbJmtStore.Options safeOptions = RocksDbJmtStore.Options.builder()
    .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.SAFE)
    .build();

RocksDbJmtStore.Options aggressiveOptions = RocksDbJmtStore.Options.builder()
    .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE)
    .build();
```

### 4. Rollback and Truncation

Truncate state to handle blockchain reorganizations:

```java
// Enable rollback indices (required for truncateAfter)
RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
    .enableRollbackIndex(true)  // Must be enabled
    .build();

try (RocksDbJmtStore store = RocksDbJmtStore.open("/var/data/jmt", options)) {
    JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

    // Commit several versions
    tree.put(1L, Map.of("alice".getBytes(), "100".getBytes()));
    tree.put(2L, Map.of("alice".getBytes(), "200".getBytes()));
    tree.put(3L, Map.of("alice".getBytes(), "300".getBytes()));

    // Rollback to version 1 (removes versions 2 and 3)
    store.truncateAfter(1L);

    // Latest version is now 1
    Optional<byte[]> latest = tree.get("alice".getBytes());
    System.out.println("After rollback: " + new String(latest.get())); // "100"
}
```

**Important**: `truncateAfter(version)` removes:
- All nodes with `version > specified version`
- All stale markers for those versions
- All root hashes for those versions
- All values created after that version

### 5. Metrics and Monitoring

Enable metrics for observability:

```java
import com.bloxbean.cardano.statetrees.jmt.JmtMetrics;
import com.bloxbean.cardano.statetrees.jmt.metrics.JmtMicrometerAdapter;
import io.micrometer.core.instrument.Metrics;

// Create Micrometer adapter
JmtMetrics metrics = JmtMicrometerAdapter.create(
    Metrics.globalRegistry,
    "jmt"  // metric prefix
);

// Create tree with metrics
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn, metrics);

// Metrics are automatically recorded:
// - jmt.commit.count, jmt.commit.latency
// - jmt.commit.puts, jmt.commit.deletes
// - jmt.read.latency
// - jmt.proof.latency
```

## Performance Tuning

### Durability vs Throughput

#### Safe Defaults (Production)

```java
RocksDbJmtStore.Options safeOptions = RocksDbJmtStore.Options.builder()
    .syncOnCommit(true)      // fsync on each commit
    .syncOnPrune(true)       // fsync on prune
    .syncOnTruncate(true)    // fsync on truncate
    .disableWalForBatches(false)  // WAL enabled
    .build();
```

**Expected**: Durable commits, steady throughput

#### Fast Mode (Benchmarking Only - UNSAFE)

```java
RocksDbJmtStore.Options fastOptions = RocksDbJmtStore.Options.builder()
    .syncOnCommit(false)     // No fsync (UNSAFE)
    .disableWalForBatches(true)  // WAL disabled (UNSAFE)
    .build();
```

**Warning**: Risk of data loss on crash. Use only for disposable benchmark data.

### Load Testing

Use the built-in load tester for performance measurement:

```bash
# Time-bound mixed workload with periodic pruning
./gradlew :state-trees-rocksdb:com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester.main \
  --args="--records=0 --duration=7200 --batch=1000 --value-size=128 --rocksdb=/var/jmt \
          --node-cache=4096 --value-cache=8192 --mix=60:30:10:0 \
          --prune-interval=10000 --prune-to=window:100000 \
          --stats-csv=/tmp/jmt-stats.csv --stats-period=10 --sync-commit=true"
```

**Key Flags**:
- `--duration=SEC`: Run for specified seconds (use with `--records=0`)
- `--batch=N`: Batch size for commits
- `--mix=P:U:D:R`: Put:Update:Delete:Proof ratios
- `--prune-interval=N`: Prune every N operations
- `--prune-to=window:W`: Keep last W versions
- `--stats-csv=PATH`: Record metrics to CSV
- `--sync-commit=true/false`: Control fsync behavior

### Visualize Performance

```bash
# Requires matplotlib
pip install matplotlib

# Plot throughput, heap, DB size over time
python3 state-trees-rocksdb/tools/jmt_stats_plot.py /tmp/jmt-stats.csv
```

## Production Operations

### Best Practices

#### 1. Single-Writer Policy
- Use one writer thread/process for commits
- Multiple readers are safe
- Avoids lock contention

#### 2. Batch Operations
```java
// Group updates in a single commit for better throughput
Map<byte[], byte[]> batch = new LinkedHashMap<>();
for (int i = 0; i < 1000; i++) {
    batch.put(("key" + i).getBytes(), ("value" + i).getBytes());
}
tree.put(version, batch);
```

#### 3. Periodic Pruning
```java
// Example: Keep last 100k versions, prune older ones
long currentVersion = store.latestRoot().map(r -> r.version()).orElse(0L);
long pruneTarget = Math.max(0, currentVersion - 100_000);

if (pruneTarget > 0) {
    int pruned = store.pruneUpTo(pruneTarget);
    System.out.println("Pruned " + pruned + " stale nodes");
}
```

#### 4. Monitor Compaction
```java
// Check RocksDB properties
RocksDbJmtStore.DbProperties props = store.getDbProperties();
System.out.println("Pending compaction: " + props.pendingCompactionBytes() / 1024 / 1024 + " MB");
System.out.println("Running compactions: " + props.runningCompactions());
System.out.println("Memtable size: " + props.curSizeAllMemTables() / 1024 / 1024 + " MB");
```

### Error Handling

```java
try {
    tree.put(version, updates);
} catch (Exception e) {
    // Handle commit failures
    logger.error("Commit failed at version " + version, e);

    // Consider rollback if partial state is problematic
    if (store.latestRoot().map(r -> r.version()).orElse(-1L) >= version) {
        store.truncateAfter(version - 1);
    }
}
```

## Code Examples

### Complete Application Example

```java
import com.bloxbean.cardano.statetrees.jmt.*;
import com.bloxbean.cardano.statetrees.jmt.store.*;
import com.bloxbean.cardano.statetrees.rocksdb.jmt.*;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import java.util.*;

public class JmtExample {
    public static void main(String[] args) {
        // Configure RocksDB store
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
            .namespace("ledger")
            .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.SAFE)
            .enableRollbackIndex(true)
            .rocksDbConfig(RocksDbConfig.balanced())
            .build();

        try (RocksDbJmtStore store = RocksDbJmtStore.open("/var/data/ledger", options)) {
            HashFunction hashFn = Blake2b256::digest;
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

            // Process transactions at version 1
            Map<byte[], byte[]> txn1 = new LinkedHashMap<>();
            txn1.put("alice".getBytes(), "1000".getBytes());
            txn1.put("bob".getBytes(), "500".getBytes());

            CommitResult result1 = tree.put(1L, txn1);
            System.out.println("Version 1 root: " + bytesToHex(result1.rootHash()));

            // Process transactions at version 2
            Map<byte[], byte[]> txn2 = new LinkedHashMap<>();
            txn2.put("alice".getBytes(), "900".getBytes());  // Transfer to bob
            txn2.put("bob".getBytes(), "600".getBytes());

            CommitResult result2 = tree.put(2L, txn2);
            System.out.println("Version 2 root: " + bytesToHex(result2.rootHash()));

            // Query current state
            Optional<byte[]> aliceBalance = tree.get("alice".getBytes());
            System.out.println("Alice current: " + new String(aliceBalance.get())); // "900"

            // Query historical state
            Optional<byte[]> aliceHistorical = tree.get("alice".getBytes(), 1L);
            System.out.println("Alice at v1: " + new String(aliceHistorical.get())); // "1000"

            // Generate and verify proof
            Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 2L);
            if (proof.isPresent()) {
                byte[] rootHash = result2.rootHash();
                CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
                boolean valid = JmtProofVerifier.verify(
                    rootHash,
                    "alice".getBytes(),
                    "900".getBytes(),
                    proof.get(),
                    hashFn,
                    commitments
                );
                System.out.println("Proof valid: " + valid);
            }

            // Prune old versions (keep last 1)
            int pruned = store.pruneUpTo(1L);
            System.out.println("Pruned nodes: " + pruned);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
```

### Blockchain State Example

```java
public class BlockchainState {
    private final RocksDbJmtStore store;
    private final JellyfishMerkleTree tree;
    private final HashFunction hashFn;

    public BlockchainState(String dbPath) throws Exception {
        RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
            .namespace("blockchain")
            .enableRollbackIndex(true)
            .build();

        this.store = RocksDbJmtStore.open(dbPath, options);
        this.hashFn = Blake2b256::digest;
        this.tree = new JellyfishMerkleTree(store, hashFn);
    }

    // Apply block
    public byte[] applyBlock(long blockHeight, Map<byte[], byte[]> stateUpdates) {
        CommitResult result = tree.put(blockHeight, stateUpdates);
        return result.rootHash();
    }

    // Handle reorganization
    public void handleReorg(long validBlockHeight) {
        store.truncateAfter(validBlockHeight);
        System.out.println("Rolled back to block " + validBlockHeight);
    }

    // Query account state
    public Optional<byte[]> getAccountState(byte[] accountKey, long blockHeight) {
        return tree.get(accountKey, blockHeight);
    }

    // Generate state proof for light clients
    public Optional<JmtProof> generateStateProof(byte[] key, long blockHeight) {
        return tree.getProof(key, blockHeight);
    }

    // Periodic maintenance
    public void pruneOldState(long keepLastNBlocks) {
        long currentBlock = store.latestRoot().map(r -> r.version()).orElse(0L);
        long pruneTarget = Math.max(0, currentBlock - keepLastNBlocks);

        if (pruneTarget > 0) {
            int pruned = store.pruneUpTo(pruneTarget);
            System.out.println("Pruned " + pruned + " nodes up to block " + pruneTarget);
        }
    }

    public void close() throws Exception {
        store.close();
    }
}
```

## Troubleshooting

### Common Issues

**Q: Getting null for historical queries**
```java
// Ensure version exists and hasn't been pruned
Optional<JmtStore.VersionedRoot> root = store.rootHash(version);
if (root.isEmpty()) {
    System.out.println("Version " + version + " not found or pruned");
}
```

**Q: Performance degradation over time**
```java
// Check compaction pressure
RocksDbJmtStore.DbProperties props = store.getDbProperties();
if (props.pendingCompactionBytes() > 1_000_000_000) {  // 1GB
    System.out.println("High compaction pressure, consider tuning");
}

// Prune stale nodes
int pruned = store.pruneUpTo(currentVersion - 10000);
System.out.println("Pruned " + pruned + " nodes");
```

**Q: High memory usage**
```java
// Reduce RocksDB buffer sizes
RocksDbConfig config = RocksDbConfig.builder()
    .writeBufferSize(32 * 1024 * 1024)  // 32MB instead of default
    .maxWriteBufferNumber(2)
    .build();
```

## Contributing

When working with JMT:

1. **Run tests**: `./gradlew :state-trees:test :state-trees-rocksdb:test`
2. **Check performance**: Use the load tester for benchmarking
3. **Follow patterns**: Use TreeCache for all node mutations
4. **Update docs**: Keep documentation current with changes
