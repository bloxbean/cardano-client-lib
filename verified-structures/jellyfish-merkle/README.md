# Jellyfish Merkle Tree

High-performance Java implementation of the Jellyfish Merkle Tree (JMT), following Diem's architecture.

## Overview

The Jellyfish Merkle Tree is a space-efficient sparse Merkle tree optimized for versioned state storage in blockchain systems. Originally developed for Diem/Aptos, this implementation provides identical semantics with Java-native performance optimizations.

## Key Features

- **Versioned State** - Multi-version concurrency control with full history
- **Batch Commits** - Atomic insertion of multiple key-value pairs
- **Cryptographic Proofs** - Inclusion/exclusion proofs for trustless verification
- **Sparse Design** - Efficient storage for sparse key spaces (256-bit keys)
- **Stale Node Tracking** - Automatic garbage collection metadata
- **Diem-Compatible** - Matches reference implementation semantics
- **High Performance** - ~2M ops/sec on commodity hardware

## When to Use

### Use Jellyfish Merkle Tree When:
- Building blockchain state storage (like Diem, Aptos)
- Need versioned state with rollback capability
- Batch insertions are common
- Cryptographic audit trails required
- State synchronization between nodes

### Don't Use JMT When:
- Need prefix queries → use [MerklePatriciaTrie](../merkle-patricia-trie/)
- Single-version only → MPT may be simpler
- Keys are not uniformly distributed

## Quick Start

### Basic Usage

```java
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// Setup storage (use RocksDB for production)
JmtStore store = new InMemoryJmtStore();
HashFunction hashFn = Blake2b256::digest;

// Create tree
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

// Batch insert (version 1)
Map<byte[], byte[]> updates = new HashMap<>();
updates.put("alice".getBytes(), "balance:100".getBytes());
updates.put("bob".getBytes(), "balance:200".getBytes());
updates.put("charlie".getBytes(), "balance:300".getBytes());

CommitResult result = tree.put(1L, updates);
byte[] rootHashV1 = result.rootHash();

// Version 2: Update Alice's balance
Map<byte[], byte[]> updates2 = new HashMap<>();
updates2.put("alice".getBytes(), "balance:150".getBytes());

CommitResult result2 = tree.put(2L, updates2);
byte[] rootHashV2 = result2.rootHash();

// Query latest version (fast)
Optional<byte[]> value = tree.get("alice".getBytes());

// Query historical version
Optional<byte[]> historicalValue = tree.get("alice".getBytes(), 1L);
// Returns "balance:100"
```

### Proof Generation

```java
// Generate proof for a key at specific version
Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 2L);

if (proof.isPresent()) {
    JmtProof p = proof.get();

    // Proof contains the value
    byte[] value = p.value();

    // Verify proof independently
    boolean valid = JmtProofVerifier.verify(
        rootHashV2,
        "alice".getBytes(),
        value,
        p,
        hashFn,
        commitments
    );

    assert valid;
}
```

### Wire Format Proofs (For Network Transmission)

```java
// Generate CBOR-encoded proof
Optional<byte[]> wireProof = tree.getProofWire("alice".getBytes(), 2L);

// Verify wire proof (can be done without tree instance)
boolean valid = tree.verifyProofWire(
    rootHashV2,
    "alice".getBytes(),
    "balance:150".getBytes(),
    true,  // inclusion proof
    wireProof.get()
);
```

### With Metrics

```java
import com.bloxbean.cardano.vds.jmt.metrics.MicrometerJmtMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// Setup metrics
MeterRegistry registry = new SimpleMeterRegistry();
JmtMetrics metrics = new MicrometerJmtMetrics(registry, "blockchain");

// Create tree with metrics
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn, metrics);

// Operations are automatically tracked
tree.put(1L, updates);

// Query metrics
double commitLatency = registry.timer("jmt.commit.duration").mean();
long nodesCreated = registry.counter("jmt.commit.nodes.created").count();
```

## Architecture

### Diem-Compatible Design

This implementation follows Diem's three-tier architecture:

```
JellyfishMerkleTree (API)
    ↓
TreeCache (batch-local state)
    ↓
JmtStore (persistent storage)
```

**Key Patterns:**
- **Delete-then-create**: All node updates follow this pattern for versioning
- **Three-tier lookup**: Staged → Frozen → Storage
- **Copy-on-write**: New version for each modified node

### Node Types

#### Internal Node
- 16 children (bitmap-compressed)
- Stores child hashes only (not versions)
- Optional compressed path for optimization

```java
JmtInternalNode {
    int bitmap;           // Which children exist (16 bits)
    byte[][] childHashes; // Compressed array of child hashes
    byte[] compressedPath; // Optional path compression (extension nodes)
}
```

#### Leaf Node
- Stores key hash and value hash
- Terminal node in the tree

```java
JmtLeafNode {
    byte[] keyHash;   // 32-byte hash of key
    byte[] valueHash; // 32-byte hash of value
}
```

### Storage Schema

JMT uses a **versioned storage** model:

```
Nodes: (version, nibble_path) → JmtNode
Values: (key_hash, version) → value
Roots: version → root_hash
Stale: (version, nibble_path) → deleted flag
```

## Batch Commits

JMT is optimized for batch operations:

```java
// Efficient: Single batch with 1000 updates
Map<byte[], byte[]> updates = new HashMap<>();
for (int i = 0; i < 1000; i++) {
    updates.put(("key" + i).getBytes(), ("value" + i).getBytes());
}
tree.put(1L, updates);

// Inefficient: 1000 separate batches
// (Don't do this)
for (int i = 0; i < 1000; i++) {
    Map<byte[], byte[]> single = new HashMap<>();
    single.put(("key" + i).getBytes(), ("value" + i).getBytes());
    tree.put(i, single);
}
```

**Performance**: Batch of 1000 updates is ~100x faster than 1000 individual commits.

## Versioning Model

### Multi-Version Storage

```java
// Version 1: Initial state
tree.put(1L, Map.of("alice".getBytes(), "100".getBytes()));

// Version 2: Update
tree.put(2L, Map.of("alice".getBytes(), "150".getBytes()));

// Version 3: Add new key
tree.put(3L, Map.of("bob".getBytes(), "200".getBytes()));

// Query any version
tree.get("alice".getBytes(), 1L); // Returns "100"
tree.get("alice".getBytes(), 2L); // Returns "150"
tree.get("alice".getBytes(), 3L); // Returns "150" (unchanged)

tree.get("bob".getBytes(), 1L);   // Returns empty (doesn't exist yet)
tree.get("bob".getBytes(), 3L);   // Returns "200"
```

### Version Constraints

- Versions must be monotonically increasing: v1 < v2 < v3 < ...
- Versions must be positive: version ≥ 0
- No gaps allowed (implementation-dependent)

### Stale Node Tracking

JMT tracks which nodes become stale (unreferenced) after each commit:

```java
CommitResult result = tree.put(2L, updates);

// Nodes created in this version
Map<NodeKey, JmtNode> newNodes = result.nodes();

// Nodes that became stale (can be garbage collected)
List<NodeKey> staleNodes = result.staleNodes();
```

**Use Case**: Implement garbage collection to reclaim space from old versions.

## Proof System

### Proof Types

1. **Inclusion Proof**: Proves a key exists with a specific value
2. **Non-inclusion (Empty)**: Proves a key doesn't exist (path ends)
3. **Non-inclusion (Different Leaf)**: Proves a key doesn't exist (different leaf at path)

### Proof Structure

```java
JmtProof {
    ProofType type;              // INCLUSION, NON_INCLUSION_EMPTY, NON_INCLUSION_DIFFERENT_LEAF
    List<BranchStep> steps;      // Path from root to leaf
    byte[] value;                // Value (for inclusion)
    byte[] valueHash;            // Hash of value
    NibblePath leafSuffix;       // Remaining nibbles at leaf
    byte[] leafKeyHash;          // Key hash at leaf
}
```

### Verification Process

```java
// 1. Generate proof
Optional<JmtProof> proof = tree.getProof(key, version);

// 2. Extract components
byte[] rootHash = store.rootHash(version).orElseThrow();
byte[] value = proof.get().value();

// 3. Verify cryptographically
CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
boolean valid = JmtProofVerifier.verify(rootHash, key, value, proof.get(), hashFn, commitments);

// 4. Use verified data
if (valid) {
    // value is cryptographically proven to be in the tree
    processVerifiedValue(value);
}
```

## Performance Characteristics

### Time Complexity
- Insert (batch): O(k × log₁₆ n) amortized, where k is batch size, n is tree size
- Get (latest): O(1) - direct value lookup
- Get (versioned): O(1) - direct value lookup with version
- Get proof: O(log₁₆ n) - tree traversal with neighbor loading

### Space Complexity
- Per version: O(k) where k is number of changes
- Total: O(v × k̄) where v is versions, k̄ is average changes per version
- With GC: O(k) for single version mode

## Gradle Dependency

```gradle
dependencies {
    // JMT core only
    implementation 'com.bloxbean.cardano:jellyfish-merkle:0.8.0'

    // For production, add a storage backend:
    // RocksDB backend (recommended)
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0'

    // Or PostgreSQL backend
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rdbms:0.8.0'
    implementation 'org.postgresql:postgresql:42.6.0'

    // Optional: Metrics
    compileOnly 'io.micrometer:micrometer-core:1.11.0'
}
```

## Storage Backends

This module provides the core JMT algorithm. For persistence, use:

- **[jellyfish-merkle-rocksdb](../jellyfish-merkle-rocksdb/)** - Embedded RocksDB (recommended)
- **[jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/)** - PostgreSQL/H2/SQLite

## API Overview

### JellyfishMerkleTree

```java
// Constructors
JellyfishMerkleTree(JmtStore store, HashFunction hashFn)
JellyfishMerkleTree(JmtStore store, HashFunction hashFn, JmtMetrics metrics)
JellyfishMerkleTree(JmtStore store, CommitmentScheme commitments, HashFunction hashFn)

// Batch operations
CommitResult put(long version, Map<byte[], byte[]> updates)

// Queries
Optional<byte[]> get(byte[] key)                    // Latest version
Optional<byte[]> get(byte[] key, long version)      // Specific version

// Proofs
Optional<JmtProof> getProof(byte[] key, long version)
Optional<byte[]> getProofWire(byte[] key, long version)  // CBOR encoded
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean including, byte[] wire)
```

### CommitResult

```java
CommitResult {
    long version();                          // Version committed
    byte[] rootHash();                       // New root hash
    Map<NodeKey, JmtNode> nodes();          // Nodes created
    List<NodeKey> staleNodes();             // Nodes to GC
    List<ValueOperation> valueOperations(); // Value puts/deletes
}
```

## Metrics

### Available Metrics

When using `MicrometerJmtMetrics`:

**Commit Metrics:**
- `jmt.commit.duration` - Timer for commit latency
- `jmt.commit.nodes.created` - Counter for nodes created
- `jmt.commit.nodes.stale` - Counter for stale nodes
- `jmt.commit.keys.updated` - Counter for keys updated

**Read Metrics:**
- `jmt.read.duration` - Timer for read latency
- `jmt.read.hit` - Counter for cache/storage hits
- `jmt.read.miss` - Counter for misses

**Proof Metrics:**
- `jmt.proof.generation.duration` - Timer for proof generation
- `jmt.proof.steps` - Distribution of proof path lengths
- `jmt.proof.inclusion` - Counter for inclusion proofs
- `jmt.proof.exclusion` - Counter for exclusion proofs

**Storage Metrics:**
- `jmt.storage.version` - Gauge for latest version
- `jmt.storage.root.size` - Gauge for root hash size
- `jmt.storage.nodes.size` - Gauge for total nodes size

## Design Documentation

See [docs/design-jmt.md](docs/design-jmt.md) for detailed architecture, algorithms, and design decisions.

## Thread Safety

- **JellyfishMerkleTree**: NOT thread-safe for writes, requires external synchronization
- **TreeCache**: Not thread-safe (batch-local)
- **JmtStore implementations**: Check specific backend documentation

## Comparison: JMT vs MPT

| Feature | JMT | MPT |
|---------|-----|-----|
| **Primary use case** | Versioned state | Key-value + prefix queries |
| **Batch operations** | Optimized | Not optimized |
| **Prefix queries** | No | Yes |
| **Version support** | Native | External |
| **Proof size** | Smaller | Larger |
| **Insert performance** | Faster (batched) | Slower |
| **Query performance** | Faster (direct lookup) | Slower (tree traversal) |
| **Space efficiency** | Better (sparse) | Worse (dense) |

**Recommendation**: Use JMT for blockchain state storage, MPT for indexing/queries.

## Related Modules

- [verified-structures-core](../verified-structures-core/) - Core interfaces
- [jellyfish-merkle-rocksdb](../jellyfish-merkle-rocksdb/) - RocksDB persistence
- [jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/) - SQL persistence
- [merkle-patricia-trie](../merkle-patricia-trie/) - Alternative: MPT for prefix queries

## References

- [Diem JMT Reference](https://github.com/diem/diem/tree/main/storage/jellyfish-merkle) - Original implementation
- [Jellyfish Merkle Tree Paper](https://developers.diem.com/papers/jellyfish-merkle-tree/2021-01-14.pdf) - Academic paper
