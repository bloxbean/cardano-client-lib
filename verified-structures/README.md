# Verified Data Structures

A modular collection of cryptographically verifiable data structures for blockchain and distributed systems, with pluggable storage backends.

## Overview

This library provides production-ready implementations of Merkle-based authenticated data structures with support for multiple persistence layers (RocksDB, PostgreSQL, H2, SQLite) and both single-version and multi-version storage modes.

## Modules

### Core Modules

- **[verified-structures-core](verified-structures-core/)** - Common APIs, utilities, and interfaces
- **[merkle-patricia-forestry](merkle-patricia-forestry/)** - MPF (Merkle Patricia Forestry) implementation with Cardano/Aiken compatibility
- **[jellyfish-merkle](jellyfish-merkle/)** - JMT (Jellyfish Merkle Tree) implementation following Diem's design

### Backend Utilities

- **[rocksdb-core](rocksdb-core/)** - Shared RocksDB utilities, resource management, and namespacing
- **[rdbms-core](rdbms-core/)** - Shared RDBMS utilities, connection pooling, and SQL dialect abstraction

### Persistence Modules

**Merkle Patricia Forestry Backends:**
- **[merkle-patricia-forestry-rocksdb](merkle-patricia-forestry-rocksdb/)** - MPF with RocksDB persistence and garbage collection
- **[merkle-patricia-forestry-rdbms](merkle-patricia-forestry-rdbms/)** - MPF with RDBMS persistence (PostgreSQL/H2/SQLite)

**Jellyfish Merkle Tree Backends:**
- **[jellyfish-merkle-rocksdb](jellyfish-merkle-rocksdb/)** - JMT with RocksDB persistence
- **[jellyfish-merkle-rdbms](jellyfish-merkle-rdbms/)** - JMT with RDBMS persistence

## Key Features

### Merkle Patricia Forestry (MPF)
- Cardano-optimized radix tree with path compression
- Space-efficient extension nodes
- Prefix scanning for range queries
- Two modes: Classic (off-chain) and MPF (Cardano/Aiken compatible)
- `MpfTrie` primary API for Cardano developers

### Jellyfish Merkle Tree (JMT)
- Diem-inspired sparse Merkle tree
- Optimized for versioned state storage
- Batch insertions with atomic commits
- Cryptographic proofs of inclusion/exclusion
- Efficient stale node tracking

### Storage Backends
- **RocksDB**: High-performance embedded database with garbage collection
- **PostgreSQL**: Distributed deployments with SQL query support
- **H2**: In-process SQL database for testing
- **SQLite**: Lightweight embedded SQL storage

## Quick Start

### Merkle Patricia Forestry with RocksDB

```java
// Add dependency
implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rocksdb:0.8.0'

// Initialize storage
RocksDbResources resources = RocksDbResources.create(Paths.get("mpf-data"));
RocksDbNodeStore nodeStore = new RocksDbNodeStore(resources.getDb());

// Create trie (MpfTrie for Cardano compatibility)
MpfTrie trie = new MpfTrie(nodeStore);

// Store data (keys automatically hashed with Blake2b-256)
trie.put("account123".getBytes(), accountData);
trie.put("account456".getBytes(), accountData2);

byte[] rootHash = trie.getRootHash();  // Use in Cardano transactions

// Retrieve data
byte[] value = trie.get("account123".getBytes());

// Cleanup
resources.close();
```

### Jellyfish Merkle Tree with RocksDB

```java
// Add dependency
implementation 'com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0'

// Initialize storage
RocksDbResources resources = RocksDbResources.create(Paths.get("jmt-data"));
RocksDbJmtStore store = new RocksDbJmtStore(resources.getDb());

// Create tree
HashFunction hashFn = Blake2b256::digest;
JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

// Batch insert
Map<byte[], byte[]> updates = new HashMap<>();
updates.put("alice".getBytes(), "balance:100".getBytes());
updates.put("bob".getBytes(), "balance:200".getBytes());

CommitResult result = tree.put(1L, updates);
byte[] rootHash = result.rootHash();

// Generate proof
Optional<JmtProof> proof = tree.getProof("alice".getBytes(), 1L);

// Cleanup
resources.close();
```

### Advanced: Low-Level MerklePatriciaTrie

```java
// For off-chain indexing with prefix queries on original keys
import com.bloxbean.cardano.vds.mpt.MerklePatriciaTrie;

NodeStore store = new RocksDbNodeStore(resources.getDb());
HashFunction hashFn = Blake2b256::digest;

// Raw MPT - keys NOT hashed (use MpfTrie for Cardano instead!)
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);

trie.put("user:alice".getBytes(), userData);
trie.put("user:bob".getBytes(), userData2);

// Prefix queries work on raw keys
List<Entry> users = trie.scanByPrefix("user:".getBytes(), 100);
```

## Gradle Dependencies

```gradle
dependencies {
    // Core interfaces
    implementation 'com.bloxbean.cardano:verified-structures-core:0.8.0'

    // MPF with RocksDB
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rocksdb:0.8.0'

    // JMT with RocksDB
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0'

    // MPF with PostgreSQL
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rdbms:0.8.0'
    implementation 'org.postgresql:postgresql:42.6.0'

    // JMT with PostgreSQL
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rdbms:0.8.0'
    implementation 'org.postgresql:postgresql:42.6.0'
}
```

## Architecture

```
verified-structures-core (interfaces & utilities)
    ↓
    ├── merkle-patricia-forestry (MPF core)
    │   ↓
    │   ├── merkle-patricia-forestry-rocksdb ← rocksdb-core
    │   └── merkle-patricia-forestry-rdbms ← rdbms-core
    │
    └── jellyfish-merkle (JMT core)
        ↓
        ├── jellyfish-merkle-rocksdb ← rocksdb-core
        └── jellyfish-merkle-rdbms ← rdbms-core
```

## Documentation

- [Design Documentation](docs/) - Architecture and design decisions
- [ADR-0016](../ADR-0016-verified-structures-modularization.md) - Modularization rationale
- Each module's README contains specific usage examples

## Performance

- **MPF**: Optimized for prefix queries and key recovery
- **JMT**: Optimized for versioned state with ~2M ops/sec on commodity hardware
- **RocksDB**: Best for single-node deployments with high throughput
- **RDBMS**: Best for distributed systems requiring SQL queries

## Use Cases

### Merkle Patricia Forestry
- Cardano smart contracts (via MpfTrie)
- Off-chain indexing and databases
- Prefix-based range queries
- Aiken on-chain proof verification

### Jellyfish Merkle Tree
- Blockchain state storage (Diem-inspired architecture)
- Multi-version concurrency control
- Cryptographic audit trails
- State synchronization between nodes

## Thread Safety

- **MPF/JMT Core**: NOT thread-safe, requires external synchronization
- **RocksDB Stores**: Thread-safe for reads, writes need coordination
- **RDBMS Stores**: Thread-safe via connection pooling

## Related Projects

- [Diem JMT Reference](https://github.com/diem/diem/tree/main/storage/jellyfish-merkle)
- [Cardano MPF (Aiken)](https://github.com/aiken-lang/merkle-patricia-forestry)
- [Ethereum MPT](https://ethereum.org/en/developers/docs/data-structures-and-encoding/patricia-merkle-trie/)
