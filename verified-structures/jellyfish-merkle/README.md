# Jellyfish Merkle Tree

Java authenticated radix-16 tree with JMT-style versioned storage, inspired by Diem's Jellyfish
Merkle Tree. The qualified production scope is serialized, off-chain state with one logical writer
per namespace; see [ADR-002](adr/002-production-readiness-gates.md).

## Overview

This implementation combines copy-on-write versioned storage with a custom radix-16 commitment and proof format. It is not commitment-compatible or wire-compatible with Diem/Aptos JMT.

> **Format boundary:** persistent stores carry a mandatory
> `classic-radix16-blake2b256-v1` descriptor and fail closed on missing or incompatible metadata.
> Pre-release prototype data, if any exists outside this project, must be rebuilt. See the
> [security, performance, and Cardano audit](docs/security-performance-audit.md).

## Key Features

- **Versioned State** - Historical roots and values (single writer required)
- **Batch Commits** - Atomic insertion of multiple key-value pairs
- **Cryptographic Proofs** - Inclusion/exclusion proofs for trustless verification
- **Sparse Design** - Efficient storage for sparse key spaces (256-bit keys)
- **Stale Node Tracking** - Automatic garbage collection metadata
- **Diem-Inspired** - Copy-on-write nodes, versioned keys, and stale-node tracking

## When to Use

### Use Jellyfish Merkle Tree When:
- Building off-chain, versioned authenticated state
- Need versioned state with backend-supported rollback
- Batch insertions are common
- Roots are authenticated independently by the application
- State synchronization between nodes

### Don't Use JMT When:
- Need prefix queries or native key deletion
- Need Cardano on-chain proof verification → use the repository's MPF implementation
- Single-version only → MPT may be simpler
- Keys are not uniformly distributed

## Quick Start

### Basic Usage

```java
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProfile;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;

// Lightweight storage for tests/examples; use RocksDbJmtStore or RdbmsJmtStore in production
JmtStore store = new InMemoryJmtStore();

// Create tree with the stable classic radix-16 Blake2b-256 v1 profile.
// The same constructor works for every store; persistent stores record the profile and reject others.
JellyfishMerkleTree tree = new JellyfishMerkleTree(store);   // = new JellyfishMerkleTree(store, JmtProfile.classicBlake2b256V1())

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

> The `JellyfishMerkleTree(store, hashFn)` constructors are deprecated: they install the `unversioned`
> format, which RocksDB and RDBMS stores reject with `JmtFormatMismatchException`. With `Blake2b256::digest`
> they compute exactly the same roots and proofs as the default profile.

### Proof Generation

```java
// Generate proof for a key at specific version
Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 2L);

if (proof.isPresent()) {
    JmtProof p = proof.get();

    // Proof contains the value (null for an absence proof)
    byte[] value = p.value();

    // Verify proof independently
    JmtProfile profile = JmtProfile.classicBlake2b256V1();
    boolean valid = JmtProofVerifier.verify(
        rootHashV2,
        "alice".getBytes(),
        value,                       // expected value, or null to verify that the key is absent
        p,
        profile.hashFunction(),
        profile.commitmentScheme()
    );

    assert valid;
}
```

`verify(root, key, value, proof, ...)` is true only when the proof shows exactly that statement: an inclusion
proof never proves absence, and an absence proof (`NON_INCLUSION_EMPTY` or `NON_INCLUSION_DIFFERENT_LEAF`) never
proves a non-null value.

### Wire Format Proofs (For Network Transmission)

```java
// Generate CBOR-encoded proof
Optional<byte[]> wireProof = tree.getProofWire("alice".getBytes(), 2L);

// Verify the wire proof (an instance method: it uses the tree's hash function, commitments and codec)
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
import java.util.concurrent.TimeUnit;

// Setup metrics (the second argument is the meter-name prefix)
MeterRegistry registry = new SimpleMeterRegistry();
JmtMetrics metrics = new MicrometerJmtMetrics(registry, "jmt");

// Create tree with metrics
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, JmtProfile.classicBlake2b256V1(), metrics);

// Commits, proofs, reads and tree.pruneUpTo(..) are tracked
tree.put(1L, updates);

// Query metrics
double commitLatency = registry.timer("jmt.commit.duration").mean(TimeUnit.MILLISECONDS);
double nodesWritten = registry.counter("jmt.commit.nodes_written").count();
```

## Architecture

### Diem-Inspired Design

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
- No path compression: the tree never creates extension nodes, and proofs that contain them are rejected

```java
JmtInternalNode {
    int bitmap;           // Which children exist (16 bits)
    byte[][] childHashes; // Compressed array of child hashes
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

Batching normally reduces storage and path-rewrite overhead. Measure the actual backend with the
same durability, key distribution, history, and batch sizes used in production.

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

`get(key, version)` answers *as of* `version`: a version between two commits reads the earlier commit's
state. A version newer than the latest commit, or below the prune watermark (see below), throws
`IllegalArgumentException` instead of returning the latest or a partial state. `getProof(key, version)`
needs a root committed at exactly `version` and returns empty otherwise.

### Pruning

```java
int removed = tree.pruneUpTo(10L);         // records jmt.prune.* metrics; store.pruneUpTo(10L) does the same without them
OptionalLong watermark = store.pruneWatermark(); // 10 once something was removed
```

State at the horizon and later stays fully readable and provable. Reads below the watermark are rejected, and
`truncateAfter` below it, or to a version where no root survives pruning, throws `IllegalStateException`.

### Version Constraints

- New versions must be monotonically increasing and non-negative.
- Gaps are supported. Only the latest committed version may be replayed for recovery; storage
  treats that replay as an immutable whole-batch no-op.
- Creating a new historical version below the latest version is rejected.
- A replay that reproduces the committed root returns a `CommitResult` with that root and no nodes, stale
  markers or value operations. After pruning up to that version, a replay is rejected: the state it was
  built on is gone.
- Writes, pruning, and rollback use fail-fast namespace coordination. RDBMS commits also
  compare-and-set the latest root transactionally across independent store instances.

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
    ProofType type();                 // INCLUSION, NON_INCLUSION_EMPTY, NON_INCLUSION_DIFFERENT_LEAF
    List<BranchStep> steps();         // Path from root to leaf
    byte[] value();                   // Value (inclusion only, else null)
    byte[] valueHash();               // Hash of value (inclusion only)
    NibblePath suffix();              // Remaining nibbles at the leaf (inclusion only)
    byte[] leafKeyHash();             // Key hash at the leaf (inclusion only)
    byte[] conflictingKeyHash();      // NON_INCLUSION_DIFFERENT_LEAF: the other leaf's key hash
    byte[] conflictingValueHash();    // NON_INCLUSION_DIFFERENT_LEAF: the other leaf's value hash
    NibblePath conflictingSuffix();   // NON_INCLUSION_DIFFERENT_LEAF: the other leaf's remaining nibbles
}
```

### Verification Process

```java
// 1. Generate proof
Optional<JmtProof> proof = tree.getProof(key, version);

// 2. Extract components
byte[] rootHash = store.rootHash(version).orElseThrow();
byte[] value = proof.get().value();

// 3. Verify cryptographically (value == null checks an absence proof)
JmtProfile profile = JmtProfile.classicBlake2b256V1();
boolean valid = JmtProofVerifier.verify(rootHash, key, value, proof.get(),
        profile.hashFunction(), profile.commitmentScheme());

// 4. Use verified data
if (valid && value != null) {
    // key -> value is proven to be in the tree
    processVerifiedValue(value);
} else if (valid) {
    // key is proven to be absent
}
```

## Performance Characteristics

### Time Complexity
- Insert (batch): proportional to the changed radix paths, with copy-on-write persistence
- Get (latest/versioned): one backend value-history lookup, without tree traversal
- Get proof: one node lookup per traversed level plus current neighbor metadata reads
- Proof verification: one fixed 515-byte branch hash per proof level for Blake2b-256

### Space Complexity
- Per version: O(k) where k is number of changes
- Total: O(v × k̄) where v is versions, k̄ is average changes per version
- Pruning (`pruneUpTo`) removes stale nodes and superseded values up to the horizon

## Gradle Dependency

```gradle
dependencies {
    // JMT core only
    implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle:<version>'

    // For production, add a storage backend:
    // RocksDB backend (recommended)
    implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle-rocksdb:<version>'

    // Or PostgreSQL backend
    implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle-rdbms:<version>'
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
// Constructors (every store)
JellyfishMerkleTree(JmtStore store)                                   // classic Blake2b-256 v1 profile
JellyfishMerkleTree(JmtStore store, JmtProfile profile)
JellyfishMerkleTree(JmtStore store, JmtProfile profile, JmtMetrics metrics)

// In-memory only (unversioned format; persistent stores reject it)
JellyfishMerkleTree(JmtStore store, CommitmentScheme commitments, HashFunction hashFn)
@Deprecated JellyfishMerkleTree(JmtStore store, HashFunction hashFn)
@Deprecated JellyfishMerkleTree(JmtStore store, HashFunction hashFn, JmtMetrics metrics)

// Batch operations
CommitResult put(long version, Map<byte[], byte[]> updates)

// Queries
Optional<byte[]> get(byte[] key)                    // Latest version
Optional<byte[]> get(byte[] key, long version)      // As of a version (rejects future and pruned versions)

// Maintenance
int pruneUpTo(long versionInclusive)                // Delegates to the store, records prune metrics

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

When using `MicrometerJmtMetrics` with prefix `jmt` (the names below use the prefix you pass):

**Commit Metrics** (not recorded for a replay that changes nothing):
- `jmt.commit.duration` - Timer for commit latency
- `jmt.commit.batch_size` - Distribution of key-value pairs per commit
- `jmt.commit.nodes_written` - Counter for nodes written
- `jmt.commit.nodes_stale` - Counter for nodes marked stale

**Read and Proof Metrics:**
- `jmt.read.duration` - Timer for `get` latency
- `jmt.proof.duration` - Timer for proof generation
- `jmt.proof.size` - Distribution of proof sizes (sibling nodes)
- `jmt.cache.hits` / `jmt.cache.misses` - Counters; the tree does not track cache hits yet, so every read
  counts as a miss
- `jmt.cache.size` - Gauge for nodes staged by the last commit

**Prune Metrics** (recorded by `tree.pruneUpTo`, not by a direct `store.pruneUpTo`):
- `jmt.prune.duration` - Timer
- `jmt.prune.nodes` - Counter for records removed (the store reports nodes, values and roots as one total)
- `jmt.prune.values` - Counter (0 while the store reports one total)

**Storage Metrics:**
- `jmt.storage.version` - Gauge for latest version
- `jmt.rocksdb.pending_compaction_bytes`, `jmt.rocksdb.running_compactions`,
  `jmt.rocksdb.memtable_size_bytes`, `jmt.rocksdb.immutable_memtables` - Gauges, published when you call
  `RocksDbJmtStore.recordStats(metrics)` (for example from a scheduler)

## Design Documentation

See [docs/design-jmt.md](docs/design-jmt.md) for detailed architecture, algorithms, and design decisions.

## Thread Safety

- **JellyfishMerkleTree**: one update lease per namespace; competing writes fail immediately with
  `JmtConcurrentMutationException`
- **Reads**: committed-version proofs may overlap a copy-on-write update; `get` takes no lease, so a prune
  running at the same time can make a `get` below the new horizon see partial history
- **Maintenance**: prune/rollback is exclusive and fails fast if a read or update is active
- **TreeCache**: Not thread-safe (batch-local)
- **JmtStore implementations**: every tree/store wrapper for a namespace must share its coordinator;
  PostgreSQL also enforces the rule across processes

## Comparison: JMT vs MPT

| Feature | JMT | MPT |
|---------|-----|-----|
| **Primary use case** | Versioned state | Key-value + prefix queries |
| **Batch operations** | Optimized | Not optimized |
| **Prefix queries** | No | Yes |
| **Version support** | Native | External |
| **Cardano on-chain verifier** | Not provided | Provided by the MPF/Aiken package |
| **Deletion** | Not implemented | Supported by MPF |
| **Historical versions** | Native in JMT stores | Backend/application dependent |
| **Direct value lookup** | Separate value index | API-dependent |

**Recommendation**: use the qualified RocksDB or PostgreSQL profile for serialized, versioned
off-chain state after satisfying the deployment checklist in ADR-002. Prefer MPF when Cardano
on-chain proofs, insert/update/delete transitions, or an existing Aiken verifier are required.

## Related Modules

- [verified-structures-core](../verified-structures-core/) - Core interfaces
- [jellyfish-merkle-rocksdb](../jellyfish-merkle-rocksdb/) - RocksDB persistence
- [jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/) - SQL persistence
- [merkle-patricia-forestry](../merkle-patricia-forestry/) - Alternative: MPF, compatible with Aiken on-chain proofs

## References

- [Diem JMT Reference](https://github.com/diem/diem/tree/main/storage/jellyfish-merkle) - Original Diem implementation
- [Jellyfish Merkle Tree Paper](https://developers.diem.com/papers/jellyfish-merkle-tree/2021-01-14.pdf) - Academic paper
