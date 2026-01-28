# Merkle Patricia Forestry - RDBMS Backend

SQL database persistence for Merkle Patricia Forestry (MPF) with PostgreSQL, H2, and SQLite support.

## Overview

Production-ready SQL backend for MPF, enabling distributed deployments, SQL queries, and easy integration with existing database infrastructure.

## Key Features

- **Multi-Database Support** - PostgreSQL, H2, SQLite
- **SQL Queries** - Direct database access for analytics
- **Distributed Deployment** - PostgreSQL replication and clustering
- **Transaction Support** - ACID guarantees
- **Easy Migration** - Standard SQL schema

## Quick Start

### PostgreSQL

```java
import com.bloxbean.cardano.vds.mpt.rdbms.RdbmsNodeStore;
import com.bloxbean.cardano.vds.mpt.MpfTrie;
import com.bloxbean.cardano.vds.rdbms.common.*;

// Configure PostgreSQL
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:postgresql://localhost:5432/mpf_db")
    .username("postgres")
    .password("password")
    .maximumPoolSize(10)
    .build();

DataSource ds = SimpleDataSource.create(config);

// Create RDBMS node store
RdbmsNodeStore nodeStore = new RdbmsNodeStore(ds);

// Initialize schema
nodeStore.initializeSchema();

// Create trie (Blake2b-256 hashing, MPF mode - Cardano/Aiken compatible)
MpfTrie trie = new MpfTrie(nodeStore);

// Store data (keys are automatically hashed)
trie.put("key".getBytes(), "value".getBytes());
byte[] rootHash = trie.getRootHash();
```

### H2 (In-Memory)

Perfect for testing:

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
    .username("sa")
    .password("")
    .build();

DataSource ds = SimpleDataSource.create(config);
RdbmsNodeStore nodeStore = new RdbmsNodeStore(ds);
nodeStore.initializeSchema();

MpfTrie trie = new MpfTrie(nodeStore);
```

### SQLite (File-Based)

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:sqlite:data/mpt.db")
    .build();

DataSource ds = SimpleDataSource.create(config);
RdbmsNodeStore nodeStore = new RdbmsNodeStore(ds);
nodeStore.initializeSchema();
```

## Schema

### Tables

**nodes table:**
```sql
CREATE TABLE nodes (
    hash BYTEA PRIMARY KEY,      -- Node hash (32 bytes)
    data BYTEA NOT NULL          -- CBOR-encoded node
);

CREATE INDEX idx_nodes_hash ON nodes USING HASH (hash);
```

**roots table:**
```sql
CREATE TABLE roots (
    tree_name VARCHAR(255),
    version BIGINT,
    root_hash BYTEA,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tree_name, version)
);

CREATE INDEX idx_roots_latest ON roots (tree_name, version DESC);
```

## API Overview

### MpfTrie (Recommended)

The primary API for Cardano developers with automatic key hashing and Aiken compatibility.

```java
// Constructors
MpfTrie(NodeStore store)                           // Blake2b-256, MPF mode
MpfTrie(NodeStore store, byte[] rootHash)          // Load existing trie

// Core operations
void put(byte[] key, byte[] value)                 // Keys automatically hashed
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()

// Proof generation (Aiken-compatible)
Optional<ListPlutusData> getProofPlutusData(byte[] key)
Optional<byte[]> getProofWire(byte[] key)
```

### RdbmsNodeStore

```java
// Constructors
RdbmsNodeStore(DataSource dataSource)
RdbmsNodeStore(DataSource dataSource, TableNameResolver tables)

// NodeStore interface
byte[] get(byte[] hash)
void put(byte[] hash, byte[] nodeBytes)
void delete(byte[] hash)

// Schema management
void initializeSchema()
void dropSchema()

// Batch operations
void withTransaction(Connection conn, Runnable operation)

// Statistics
long countNodes()
long sizeBytes()
```

### RdbmsStateTrees

Multi-tree management with SQL backend and versioning support.

```java
// Constructor
RdbmsStateTrees(DataSource dataSource)
RdbmsStateTrees(DataSource dataSource, StorageMode mode)

// Version management
void commit(String name)
byte[] getRootHash(String name)
List<Long> listVersions(String name)

// Cleanup
void close()
```

## Usage Examples

### Multi-Tree Management

```java
DataSource ds = SimpleDataSource.create(config);
RdbmsNodeStore nodeStore = new RdbmsNodeStore(ds);
nodeStore.initializeSchema();

// Create separate tries for different domains
MpfTrie accounts = new MpfTrie(nodeStore);
MpfTrie contracts = new MpfTrie(nodeStore);

// Store data (keys automatically hashed)
accounts.put("alice".getBytes(), "100".getBytes());
contracts.put("0x123".getBytes(), contractData);

// Get root hashes for each tree
byte[] accountsRoot = accounts.getRootHash();
byte[] contractsRoot = contracts.getRootHash();
```

### Transaction Management

```java
RdbmsNodeStore nodeStore = new RdbmsNodeStore(ds);
MpfTrie trie = new MpfTrie(nodeStore);

try (Connection conn = ds.getConnection()) {
    conn.setAutoCommit(false);

    try {
        // Atomic operations
        trie.put("key1".getBytes(), "value1".getBytes());
        trie.put("key2".getBytes(), "value2".getBytes());
        trie.put("key3".getBytes(), "value3".getBytes());

        conn.commit();
    } catch (Exception e) {
        conn.rollback();
        throw e;
    }
}
```

### SQL Analytics

Direct SQL queries for analytics:

```sql
-- Count nodes
SELECT COUNT(*) FROM nodes;

-- Find largest nodes
SELECT hash, LENGTH(data) as size
FROM nodes
ORDER BY size DESC
LIMIT 10;

-- Version history
SELECT tree_name, version, created_at, root_hash
FROM roots
WHERE tree_name = 'accounts'
ORDER BY version DESC;

-- Latest root per tree
SELECT DISTINCT ON (tree_name)
    tree_name, version, root_hash
FROM roots
ORDER BY tree_name, version DESC;
```

### Backup and Restore

```sql
-- PostgreSQL backup
pg_dump -t nodes -t roots mpt_db > backup.sql

-- Restore
psql mpt_db < backup.sql
```

```bash
# SQLite backup
sqlite3 mpt.db ".backup backup.db"

# Restore
cp backup.db mpt.db
```

## Performance

### Benchmarks

PostgreSQL on commodity hardware (Intel i7, local PostgreSQL):

| Operation | Throughput | Latency (p50) |
|-----------|-----------|---------------|
| Insert (single) | 5K ops/sec | 200μs |
| Insert (batch 100) | 50K ops/sec | 2ms/batch |
| Lookup | 20K ops/sec | 50μs |
| Prefix scan (100) | 10K scans/sec | 100μs |

**Note**: RocksDB is ~10x faster for writes. Use RDBMS for distributed systems or SQL query requirements.

### Optimization Tips

**PostgreSQL:**
```sql
-- Batch inserts
COPY nodes (hash, data) FROM STDIN BINARY;

-- Prepared statements
PREPARE insert_node AS
INSERT INTO nodes (hash, data) VALUES ($1, $2)
ON CONFLICT (hash) DO UPDATE SET data = EXCLUDED.data;

EXECUTE insert_node('\x01020304', '\xdeadbeef');
```

**HikariCP tuning:**
```java
DbConfig config = DbConfig.builder()
    .jdbcUrl(url)
    .maximumPoolSize(20)           // Match concurrent threads
    .minimumIdle(5)
    .connectionTimeout(5000)
    .prepStmtCacheSize(250)        // Statement cache
    .prepStmtCacheSqlLimit(2048)
    .build();
```

## Distributed Deployment

### PostgreSQL Replication

```yaml
# Primary server
wal_level = replica
max_wal_senders = 5
wal_keep_size = 1GB

# Standby servers
primary_conninfo = 'host=primary port=5432'
```

### High Availability

```java
// Connection string with failover
String jdbcUrl = "jdbc:postgresql://primary:5432,standby:5432/mpt_db" +
    "?targetServerType=primary";

DbConfig config = DbConfig.builder()
    .jdbcUrl(jdbcUrl)
    .username("user")
    .password("pass")
    .build();
```

### Read Replicas

```java
// Write to primary
DataSource primaryDs = SimpleDataSource.create(primaryConfig);
RdbmsNodeStore writeStore = new RdbmsNodeStore(primaryDs);

// Read from replica
DataSource replicaDs = SimpleDataSource.create(replicaConfig);
RdbmsNodeStore readStore = new RdbmsNodeStore(replicaDs);

// Write operations
MpfTrie trie = new MpfTrie(writeStore);
trie.put(key, value);
byte[] rootHash = trie.getRootHash();

// Read operations (from replica)
MpfTrie readTrie = new MpfTrie(readStore, rootHash);
byte[] value = readTrie.get(key);
```

## Migration from RocksDB

### Export from RocksDB

```java
RocksDbResources rocksResources = RocksDbResources.create(Paths.get("rocksdb"));
RocksDbNodeStore rocksStore = new RocksDbNodeStore(rocksResources.getDb());

DataSource pgDs = SimpleDataSource.create(pgConfig);
RdbmsNodeStore rdbmsStore = new RdbmsNodeStore(pgDs);
rdbmsStore.initializeSchema();

// Iterate and copy
try (RocksIterator iter = rocksResources.getDb().newIterator()) {
    iter.seekToFirst();
    while (iter.isValid()) {
        byte[] hash = iter.key();
        byte[] data = iter.value();
        rdbmsStore.put(hash, data);
        iter.next();
    }
}
```

## Gradle Dependency

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rdbms:0.8.0'

    // Database driver
    implementation 'org.postgresql:postgresql:42.6.0'
    // or
    implementation 'com.h2database:h2:2.2.224'
    // or
    implementation 'org.xerial:sqlite-jdbc:3.45.0.0'
}
```

## Comparison: RDBMS vs RocksDB

| Feature | RDBMS | RocksDB |
|---------|-------|---------|
| **Write Performance** | Medium (5K-50K ops/sec) | Very High (50K-500K ops/sec) |
| **Read Performance** | Medium (20K ops/sec) | High (100K ops/sec) |
| **SQL Queries** | Yes | No |
| **Distributed** | Yes (PostgreSQL) | No (embedded) |
| **Transactions** | ACID | Limited |
| **Operational Complexity** | Higher | Lower |
| **Storage Efficiency** | Medium | High |

**Use RDBMS when:**
- Need SQL analytics
- Distributed deployment required
- Existing PostgreSQL infrastructure
- ACID transactions critical

**Use RocksDB when:**
- Maximum performance needed
- Single-node deployment
- Embedded database preferred

## Thread Safety

- **RdbmsNodeStore**: Thread-safe (connection pooling)
- **RdbmsStateTrees**: Thread-safe
- **MpfTrie**: NOT thread-safe, use separate instances per thread

## Design Documentation

- [Core MPF Design](../merkle-patricia-forestry/docs/design.md) - MPF algorithm

## Related Modules

- [merkle-patricia-forestry](../merkle-patricia-forestry/) - MPT core
- [rdbms-core](../rdbms-core/) - RDBMS utilities
- [merkle-patricia-forestry-rocksdb](../merkle-patricia-forestry-rocksdb/) - Alternative: RocksDB backend

## References

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [H2 Database](https://www.h2database.com/)
- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry) - Cardano MPF reference implementation
