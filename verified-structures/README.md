# Verified Data Structures

A modular collection of cryptographically verifiable data structures for blockchain and distributed systems, with pluggable storage backends.

## Overview

This library provides experimental Merkle-based authenticated data structures with support for multiple persistence layers (RocksDB, PostgreSQL, H2, SQLite) and both single-version and multi-version storage modes. Production suitability is module-specific; review each module's limitations and migration notes.

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
- Radix-16 trie with path compression, compatible with the Aiken
  [merkle-patricia-forestry](https://github.com/aiken-lang/merkle-patricia-forestry) on-chain library
- Inclusion and exclusion proofs as Plutus data for Aiken validators
- `MpfTrie` primary API for Cardano developers

### Jellyfish Merkle Tree (JMT)
- Diem-inspired sparse Merkle tree
- Optimized for versioned state storage
- Batch insertions with atomic commits
- Cryptographic proofs of inclusion/exclusion
- Efficient stale node tracking

### Storage Backends
- **RocksDB** (recommended for now): high-performance embedded database with garbage collection
- **PostgreSQL**: deployments that need SQL access to the data
- **H2**: in-process SQL database for testing
- **SQLite**: lightweight embedded SQL storage

Notes:
- The RDBMS backends never create their tables. For MPF call `RdbmsMpfSchema.createIfNotExists(dbConfig)` once,
  or apply the bundled DDL under `/ddl/mpf/{postgres,h2,sqlite}/schema.sql`; for JMT apply
  `/ddl/jmt/{postgres,h2,sqlite}/schema.sql`.
- Storage-mode defaults differ: the MPF RocksDB backend defaults to `MULTI_VERSION` (and persists and checks the
  mode), the MPF RDBMS backend defaults to `SINGLE_VERSION` (and does not persist it). Pass the mode explicitly.

## Quick Start

### Merkle Patricia Forestry with RocksDB

```java
// Add dependency
implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:0.8.0'

// Open (or create) the database; MULTI_VERSION is the default storage mode
try (RocksDbStateTrees trees = new RocksDbStateTrees("mpf-data")) {
    // Create trie (keys are hashed with Blake2b-256, as in Aiken)
    MpfTrie trie = new MpfTrie(trees.nodeStore());

    trie.put("account123".getBytes(), accountData);
    trie.put("account456".getBytes(), accountData2);

    byte[] rootHash = trie.getRootHash();       // Use in Cardano transactions
    trees.putRootWithRefcount(rootHash);        // Record the version

    byte[] value = trie.get("account123".getBytes());
}
```

### Jellyfish Merkle Tree with RocksDB

```java
// Add dependency
implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle-rocksdb:0.8.0'

// Initialize a dedicated RocksDB store (the store owns and closes the database)
RocksDbJmtStore store = new RocksDbJmtStore("jmt-data");

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
store.close();
```

## Gradle Dependencies

```gradle
dependencies {
    // Core interfaces
    implementation 'com.bloxbean.cardano:cardano-client-verified-structures-core:0.8.0'

    // MPF with RocksDB
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:0.8.0'

    // JMT with RocksDB
    implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle-rocksdb:0.8.0'

    // MPF with PostgreSQL (the PostgreSQL driver is included)
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rdbms:0.8.0'

    // JMT with PostgreSQL
    implementation 'com.bloxbean.cardano:cardano-client-jellyfish-merkle-rdbms:0.8.0'
    implementation 'org.postgresql:postgresql:42.7.10'
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
- Each module's README contains specific usage examples

## Performance

- **MPF**: Aiken-compatible commitments and proofs; original keys are kept in leaves for introspection
- **JMT**: Designed for batched, versioned off-chain state; benchmark on the target workload and durability settings
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
- Multi-version historical reads with externally serialized writes
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
