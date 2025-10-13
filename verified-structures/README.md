# Verified Data Structures

A modular collection of cryptographically verifiable data structures for blockchain and distributed systems, with pluggable storage backends.

## Overview

This library provides production-ready implementations of Merkle-based authenticated data structures with support for multiple persistence layers (RocksDB, PostgreSQL, H2, SQLite) and both single-version and multi-version storage modes.

## Modules

### Core Modules

- **[verified-structures-core](verified-structures-core/)** - Common APIs, utilities, and interfaces
- **[merkle-patricia-trie](merkle-patricia-trie/)** - MPT and MPF (Merkle Patricia Forestry) implementation
- **[jellyfish-merkle](jellyfish-merkle/)** - JMT (Jellyfish Merkle Tree) implementation following Diem's design

### Backend Utilities

- **[rocksdb-core](rocksdb-core/)** - Shared RocksDB utilities, resource management, and namespacing
- **[rdbms-core](rdbms-core/)** - Shared RDBMS utilities, connection pooling, and SQL dialect abstraction

### Persistence Modules

**Merkle Patricia Trie Backends:**
- **[merkle-patricia-trie-rocksdb](merkle-patricia-trie-rocksdb/)** - MPT with RocksDB persistence and garbage collection
- **[merkle-patricia-trie-rdbms](merkle-patricia-trie-rdbms/)** - MPT with RDBMS persistence (PostgreSQL/H2/SQLite)

**Jellyfish Merkle Tree Backends:**
- **[jellyfish-merkle-rocksdb](jellyfish-merkle-rocksdb/)** - JMT with RocksDB persistence
- **[jellyfish-merkle-rdbms](jellyfish-merkle-rdbms/)** - JMT with RDBMS persistence

## Key Features

### Merkle Patricia Trie (MPT)
- Ethereum-inspired radix tree with path compression
- Space-efficient extension nodes
- Prefix scanning for range queries
- Two modes: Classic MPT and MPF (Merkle Patricia Forestry)
- SecureTrie wrapper for Cardano/Aiken compatibility

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

### Merkle Patricia Trie with RocksDB

```java
// Add dependency
implementation 'com.bloxbean.cardano:merkle-patricia-trie-rocksdb:0.8.0'

// Initialize storage
RocksDbResources resources = RocksDbResources.create(Paths.get("mpt-data"));
RocksDbNodeStore nodeStore = new RocksDbNodeStore(resources.getDb());

// Create trie
HashFunction hashFn = Blake2b256::digest;
MerklePatriciaTrie trie = new MerklePatriciaTrie(nodeStore, hashFn);

// Store data
trie.put("key1".getBytes(), "value1".getBytes());
trie.put("key2".getBytes(), "value2".getBytes());

byte[] rootHash = trie.getRootHash();

// Retrieve data
byte[] value = trie.get("key1".getBytes());

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

### Cardano-Compatible SecureTrie

```java
// For Aiken merkle-patricia-forestry compatibility
import com.bloxbean.cardano.vds.mpt.SecureTrie;

NodeStore store = new RocksDbNodeStore(resources.getDb());
HashFunction blake2b = Blake2b256::digest;

// Keys are automatically hashed before storage (matches Aiken behavior)
SecureTrie trie = new SecureTrie(store, blake2b);

trie.put("account123".getBytes(), accountData);
byte[] rootHash = trie.getRootHash(); // Use in Cardano transactions
```

## Gradle Dependencies

```gradle
dependencies {
    // Core interfaces
    implementation 'com.bloxbean.cardano:verified-structures-core:0.8.0'

    // MPT with RocksDB
    implementation 'com.bloxbean.cardano:merkle-patricia-trie-rocksdb:0.8.0'

    // JMT with RocksDB
    implementation 'com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0'

    // MPT with PostgreSQL
    implementation 'com.bloxbean.cardano:merkle-patricia-trie-rdbms:0.8.0'
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
    ├── merkle-patricia-trie (MPT/MPF core)
    │   ↓
    │   ├── merkle-patricia-trie-rocksdb ← rocksdb-core
    │   └── merkle-patricia-trie-rdbms ← rdbms-core
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

- **MPT**: Optimized for prefix queries and key recovery
- **JMT**: Optimized for versioned state with ~2M ops/sec on commodity hardware
- **RocksDB**: Best for single-node deployments with high throughput
- **RDBMS**: Best for distributed systems requiring SQL queries

## Use Cases

### Merkle Patricia Trie
- Off-chain indexing and databases
- Prefix-based range queries
- Cardano smart contracts (via SecureTrie)
- Ethereum-inspired state trie structure

### Jellyfish Merkle Tree
- Blockchain state storage (Diem-inspired architecture)
- Multi-version concurrency control
- Cryptographic audit trails
- State synchronization between nodes

## Thread Safety

- **MPT/JMT Core**: NOT thread-safe, requires external synchronization
- **RocksDB Stores**: Thread-safe for reads, writes need coordination
- **RDBMS Stores**: Thread-safe via connection pooling

## Related Projects

- [Diem JMT Reference](https://github.com/diem/diem/tree/main/storage/jellyfish-merkle)
- [Cardano MPF (Aiken)](https://github.com/aiken-lang/merkle-patricia-forestry)
- [Ethereum MPT](https://ethereum.org/en/developers/docs/data-structures-and-encoding/patricia-merkle-trie/)
