# Jellyfish Merkle Tree - RocksDB Backend

RocksDB persistence for Jellyfish Merkle Tree with versioned state storage.

## Overview

Production-ready RocksDB backend for JMT, optimized for blockchain state storage with multi-version support, efficient batch writes, and stale node tracking for garbage collection.

## Key Features

- **Versioned Storage** - Full multi-version history with efficient lookups
- **Batch Commits** - Atomic insertion of large update sets
- **Stale Node Tracking** - Metadata for garbage collection
- **High Performance** - Optimized RocksDB configuration for JMT workloads
- **Proof Generation** - Efficient proof creation from stored state
- **Load Testing Tools** - Performance benchmarking utilities

## Quick Start

```java
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// Initialize RocksDB
RocksDbResources resources = RocksDbResources.create(Paths.get("data/jmt"));
RocksDbJmtStore store = new RocksDbJmtStore(resources.getDb());

// Create tree
HashFunction hashFn = Blake2b256::digest;
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

// Batch insert (version 1)
Map<byte[], byte[]> updates = new HashMap<>();
updates.put("alice".getBytes(), "balance:100".getBytes());
updates.put("bob".getBytes(), "balance:200".getBytes());

CommitResult result = tree.put(1L, updates);
byte[] rootHashV1 = result.rootHash();

// Query
Optional<byte[]> value = tree.get("alice".getBytes());

// Generate proof
Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 1L);

// Cleanup
resources.close();
```

## Storage Schema

RocksDB stores JMT data in a versioned schema:

```
Nodes:  (version, nibble_path) → JmtNode (CBOR)
Values: (key_hash, version) → value
Roots:  version → root_hash
Stale:  (version, nibble_path) → deleted_flag
```

### Column Families

```
nodes_cf:  Versioned nodes (Internal + Leaf)
values_cf: Key-value data
roots_cf:  Version → root hash mapping
stale_cf:  Stale node markers for GC
```

## API Overview

### RocksDbJmtStore

```java
// Constructor
RocksDbJmtStore(RocksDB db)
RocksDbJmtStore(RocksDB db, RocksDbConfig config)

// JmtStore interface
CommitBatch beginCommit(long version, CommitConfig config)
Optional<NodeEntry> getNode(long version, NibblePath path)
Optional<byte[]> getValue(byte[] keyHash)
Optional<byte[]> getValueAt(byte[] keyHash, long version)
Optional<byte[]> rootHash(long version)

// Lifecycle
void close()
```

### CommitBatch Operations

```java
CommitBatch batch = store.beginCommit(1L, CommitConfig.defaults());

try {
    // Add nodes
    batch.putNode(nodeKey, node);

    // Mark stale nodes
    batch.markStale(oldNodeKey);

    // Store values
    batch.putValue(keyHash, value);

    // Set root
    batch.setRootHash(rootHash);

    // Atomic commit
    batch.commit();
} finally {
    batch.close();
}
```

## Configuration

### RocksDbConfig

```java
RocksDbConfig config = RocksDbConfig.builder()
    .nodesCfName("jmt_nodes")
    .valuesCfName("jmt_values")
    .rootsCfName("jmt_roots")
    .staleCfName("jmt_stale")
    .enableStaleTracking(true)
    .build();

RocksDbJmtStore store = new RocksDbJmtStore(db, config);
```

### Performance Tuning

```java
// Write-optimized configuration
Options options = new Options()
    .setWriteBufferSize(128 * 1024 * 1024)  // 128 MB
    .setMaxWriteBufferNumber(4)
    .setCompressionType(CompressionType.LZ4_COMPRESSION);

RocksDbResources resources = RocksDbResources.builder()
    .path(path)
    .options(options)
    .build();
```

## Load Testing

Built-in tools for performance testing:

```bash
# Concurrent load test
java -cp ... com.bloxbean.cardano.vds.jmt.rocksdb.tools.JmtConcurrentLoadTester \
  /tmp/jmt 1000000 100 4

# Arguments:
# - /tmp/jmt: Database path
# - 1000000: Total keys
# - 100: Batch size
# - 4: Concurrent threads
```

```bash
# Single-threaded load test
java -cp ... com.bloxbean.cardano.vds.jmt.rocksdb.tools.JmtLoadTester \
  /tmp/jmt 100000 1000
```

## Stale Node Tracking

RocksDB backend automatically tracks stale nodes for garbage collection:

```java
CommitResult result = tree.put(2L, updates);

// Nodes that became stale
List<NodeKey> staleNodes = result.staleNodes();

// Can be deleted to reclaim space
for (NodeKey nodeKey : staleNodes) {
    store.deleteNode(nodeKey);
}
```

## Multi-Version Queries

```java
// Latest version (fastest)
Optional<byte[]> latest = tree.get("alice".getBytes());

// Specific version
Optional<byte[]> historical = tree.get("alice".getBytes(), 1L);

// Root hash at version
Optional<byte[]> rootV1 = store.rootHash(1L);

// Proof at version
Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 1L);
```

## Gradle Dependency

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0'
}
```

## Design Documentation

- [JMT RocksDB Design](docs/design-jmt-rocksdb.md)
- [Core JMT](../jellyfish-merkle/docs/design-jmt.md)

## Thread Safety

- **RocksDbJmtStore**: Thread-safe for reads, coordinate writes
- **CommitBatch**: NOT thread-safe, use per-thread instances

## Related Modules

- [jellyfish-merkle](../jellyfish-merkle/) - JMT core
- [rocksdb-core](../rocksdb-core/) - RocksDB utilities
- [jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/) - SQL backend
