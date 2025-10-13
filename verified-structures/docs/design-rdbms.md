# RDBMS Integration Architecture

**Module:** `rdbms-core`

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [SQL Dialect Abstraction](#sql-dialect-abstraction)
3. [Connection Pooling](#connection-pooling)
4. [Transaction Management](#transaction-management)
5. [Schema Design](#schema-design)
6. [Performance Tuning](#performance-tuning)
7. [Design Decisions](#design-decisions)
8. [References](#references)

---

## 1. Architecture Overview

### 1.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────┐
│            Application (MPT/JMT)                         │
└─────────────────────┬────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────┐
│           rdbms-core Integration Layer                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐ │
│  │  Dialect   │  │  Pool      │  │  Transaction       │ │
│  │  Resolver  │  │  Manager   │  │  Coordinator       │ │
│  └────────────┘  └────────────┘  └────────────────────┘ │
│                                                           │
│  Supported Dialects:                                     │
│  • PostgreSQL (production-recommended)                   │
│  • H2 (testing, embedded)                                │
│  • SQLite (single-user, embedded)                        │
└─────────────────────┬────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────┐
│              JDBC / Database Driver                      │
└─────────────────────┬────────────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────────────┐
│              Relational Database                         │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Tables: nodes, values, roots, metadata           │  │
│  │  Indexes: Primary keys, hash indexes, version IX  │  │
│  │  Constraints: Foreign keys, unique constraints    │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 1.2 Component Responsibilities

**SqlDialect:**
- Provides database-specific SQL syntax
- Handles data type mapping (BYTEA vs BLOB vs BINARY)
- Manages upsert operations (INSERT ... ON CONFLICT vs MERGE)
- Optimizes queries for specific DB engines

**ConnectionPool (HikariCP):**
- Connection lifecycle management
- Connection validation and health checks
- Automatic failover and retry
- Metrics and monitoring

**TransactionManager:**
- ACID transaction coordination
- Savepoint support for nested transactions
- Isolation level management
- Deadlock detection and retry

---

## 2. SQL Dialect Abstraction

### 2.1 Dialect Interface

```java
public interface SqlDialect {
    // DDL
    String createNodeTable(String tableName);
    String createValueTable(String tableName);
    String createRootTable(String tableName);

    // DML
    String upsertNode(String tableName);
    String selectNode(String tableName);
    String deleteNode(String tableName);

    // Type mapping
    String binaryType(int maxLength);
    String bigintType();
    String timestampType();

    // Features
    boolean supportsReturning();
    boolean supportsUpsert();
    boolean supportsArrays();
}
```

### 2.2 PostgreSQL Dialect

**Features:**
- BYTEA for binary data (efficient, no encoding)
- ON CONFLICT for upserts (atomic, performant)
- RETURNING clause for insert feedback
- Rich indexing (B-tree, Hash, GIN, GiST)
- MVCC for high concurrency

**Example SQL:**

```sql
-- Node table with hash index
CREATE TABLE nodes (
    tree_name VARCHAR(255) NOT NULL,
    node_hash BYTEA NOT NULL,
    node_data BYTEA NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (tree_name, node_hash)
);

CREATE INDEX idx_nodes_hash ON nodes USING HASH (node_hash);

-- Upsert with RETURNING
INSERT INTO nodes (tree_name, node_hash, node_data)
VALUES (?, ?, ?)
ON CONFLICT (tree_name, node_hash) DO UPDATE
    SET node_data = EXCLUDED.node_data
RETURNING node_hash;

-- Value table with version support
CREATE TABLE values (
    tree_name VARCHAR(255) NOT NULL,
    key_hash BYTEA NOT NULL,
    version BIGINT NOT NULL,
    value_data BYTEA NOT NULL,
    PRIMARY KEY (tree_name, key_hash, version)
);

CREATE INDEX idx_values_version ON values (tree_name, version);
```

**Performance Optimizations:**
- Use HASH index for exact-match lookups on node_hash
- Use B-tree index for range queries on version
- Use partial indexes for active data: `WHERE version >= (SELECT MAX(version) - 100)`

### 2.3 H2 Dialect

**Features:**
- BINARY for binary data (compatible with PostgreSQL BYTEA)
- MERGE for upserts (standard SQL)
- In-memory mode for testing (fast, ephemeral)
- Compatible mode for PostgreSQL migration

**Example SQL:**

```sql
-- Node table (H2)
CREATE TABLE nodes (
    tree_name VARCHAR(255) NOT NULL,
    node_hash BINARY(32) NOT NULL,
    node_data BINARY NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (tree_name, node_hash)
);

-- Upsert using MERGE
MERGE INTO nodes (tree_name, node_hash, node_data)
KEY (tree_name, node_hash)
VALUES (?, ?, ?);
```

**Testing Usage:**

```java
// In-memory H2 for unit tests
String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
HikariConfig config = new HikariConfig();
config.setJdbcUrl(url);
config.setUsername("sa");
config.setPassword("");
HikariDataSource ds = new HikariDataSource(config);
```

### 2.4 SQLite Dialect

**Features:**
- BLOB for binary data
- INSERT OR REPLACE for upserts
- Single-writer (WAL mode helps)
- Good for embedded, single-user scenarios

**Limitations:**
- Poor concurrent write performance
- No true ALTER TABLE support
- Limited data types
- Not recommended for production multi-user

**Example SQL:**

```sql
-- Node table (SQLite)
CREATE TABLE nodes (
    tree_name TEXT NOT NULL,
    node_hash BLOB NOT NULL,
    node_data BLOB NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s', 'now')),
    PRIMARY KEY (tree_name, node_hash)
);

-- Upsert
INSERT OR REPLACE INTO nodes (tree_name, node_hash, node_data)
VALUES (?, ?, ?);
```

---

## 3. Connection Pooling

### 3.1 HikariCP Configuration

**Why HikariCP:**
- Fastest connection pool (0.1-1 µs overhead)
- Battle-tested (production-proven)
- Excellent monitoring and metrics
- Active development and support

**Production Configuration:**

```java
HikariConfig config = new HikariConfig();

// Connection
config.setJdbcUrl("jdbc:postgresql://localhost:5432/merkle_trees");
config.setUsername("app_user");
config.setPassword(System.getenv("DB_PASSWORD"));

// Pool sizing (Rule: connections = cores × 2 + effective_spindle_count)
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);

// Connection lifetime (prevent stale connections)
config.setMaxLifetime(1800000); // 30 minutes
config.setIdleTimeout(600000);  // 10 minutes

// Connection validation
config.setConnectionTestQuery("SELECT 1");
config.setConnectionTimeout(5000); // 5 seconds

// Performance
config.setAutoCommit(false); // Explicit transaction control
config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

// Monitoring
config.setRegisterMbeans(true);
config.setLeakDetectionThreshold(60000); // 60 seconds

HikariDataSource dataSource = new HikariDataSource(config);
```

### 3.2 Pool Sizing Strategy

**Formula:** `connections = ((core_count × 2) + effective_spindle_count)`

**Example (4-core CPU, 1 SSD):**
- connections = (4 × 2) + 1 = 9
- Round up to 10-12 for safety margin
- Maximum pool size: 20 (allows burst traffic)

**Reasoning:**
- Each core can handle 2 active threads (hyperthreading)
- Disk I/O adds 1 concurrent operation (SSD)
- More connections doesn't improve throughput (context switching overhead)

**Testing Configuration:**

```java
// Aggressive for testing (avoid timeouts)
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setConnectionTimeout(30000); // 30 seconds
config.setIdleTimeout(60000);       // 1 minute
```

### 3.3 Connection Lifecycle

```
┌─────────────────────────────────────────────┐
│  Request Connection                         │
│      │                                       │
│      ├─ Check pool for idle connection      │ ← Fast path (<1 µs)
│      ├─ If available: return immediately    │
│      ├─ If not: wait up to connectionTimeout│ ← Slow path
│      └─ If timeout: throw SQLException      │
├─────────────────────────────────────────────┤
│  Use Connection                             │
│      ├─ Execute queries                     │
│      ├─ Transaction management              │
│      └─ Error handling                      │
├─────────────────────────────────────────────┤
│  Return Connection                          │
│      ├─ Reset state (rollback if needed)    │
│      ├─ Validate health (optional)          │
│      └─ Return to pool                      │
└─────────────────────────────────────────────┘
```

---

## 4. Transaction Management

### 4.1 Transaction Patterns

**Read-Only Transaction:**
```java
try (Connection conn = dataSource.getConnection()) {
    conn.setReadOnly(true);
    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

    try (PreparedStatement ps = conn.prepareStatement(SELECT_QUERY)) {
        ps.setBytes(1, keyHash);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBytes("value_data");
            }
        }
    }
} // Auto-commit (no explicit commit needed)
```

**Write Transaction:**
```java
Connection conn = null;
try {
    conn = dataSource.getConnection();
    conn.setAutoCommit(false);

    // Multiple writes
    try (PreparedStatement ps = conn.prepareStatement(INSERT_NODE)) {
        for (Node node : nodes) {
            ps.setString(1, treeName);
            ps.setBytes(2, node.hash());
            ps.setBytes(3, node.data());
            ps.addBatch();
        }
        ps.executeBatch();
    }

    conn.commit(); // Atomic commit

} catch (SQLException e) {
    if (conn != null) {
        try {
            conn.rollback(); // Rollback on error
        } catch (SQLException ex) {
            // Log rollback failure
        }
    }
    throw new RdbmsStorageException("Failed to commit transaction", e);
} finally {
    if (conn != null) {
        try {
            conn.close(); // Return to pool
        } catch (SQLException e) {
            // Log close failure
        }
    }
}
```

### 4.2 Isolation Levels

**READ COMMITTED (Recommended):**
- Prevents dirty reads
- Allows non-repeatable reads (acceptable for most workloads)
- Good concurrency
- PostgreSQL default

**REPEATABLE READ:**
- Prevents dirty reads and non-repeatable reads
- Can cause serialization errors (retry needed)
- Lower concurrency
- Use for critical consistency requirements

**SERIALIZABLE:**
- Prevents all anomalies
- Very low concurrency (many retries)
- Use only when absolutely necessary

**Comparison:**

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Concurrency |
|-------|------------|---------------------|--------------|-------------|
| READ COMMITTED | No | Yes | Yes | High |
| REPEATABLE READ | No | No | Yes | Medium |
| SERIALIZABLE | No | No | No | Low |

### 4.3 Deadlock Handling

**Detection:**
```java
catch (SQLException e) {
    if (e.getSQLState().equals("40P01")) { // PostgreSQL deadlock code
        // Retry with exponential backoff
        return retryWithBackoff(() -> performTransaction(), 3);
    }
    throw e;
}

private <T> T retryWithBackoff(Supplier<T> operation, int maxRetries) {
    int retries = 0;
    while (retries < maxRetries) {
        try {
            return operation.get();
        } catch (DeadlockException e) {
            retries++;
            long backoff = (long) (100 * Math.pow(2, retries)); // Exponential
            Thread.sleep(backoff);
        }
    }
    throw new RuntimeException("Max retries exceeded");
}
```

**Prevention:**
1. Always acquire locks in same order (e.g., sort keys before update)
2. Keep transactions short (minimize lock hold time)
3. Use optimistic locking where possible
4. Consider lock timeout: `SET lock_timeout = '5s';`

---

## 5. Schema Design

### 5.1 Core Tables

**Nodes Table:**
```sql
CREATE TABLE nodes (
    tree_name VARCHAR(255) NOT NULL,
    node_hash BYTEA NOT NULL,
    node_data BYTEA NOT NULL,
    node_type SMALLINT NOT NULL,  -- 0=Branch, 1=Extension, 2=Leaf
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tree_name, node_hash)
);

CREATE INDEX idx_nodes_type ON nodes (tree_name, node_type);
CREATE INDEX idx_nodes_created ON nodes (created_at); -- For GC
```

**Values Table (Versioned):**
```sql
CREATE TABLE values (
    tree_name VARCHAR(255) NOT NULL,
    key_hash BYTEA NOT NULL,
    version BIGINT NOT NULL,
    value_data BYTEA NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL, -- Soft delete
    PRIMARY KEY (tree_name, key_hash, version)
);

CREATE INDEX idx_values_version ON values (tree_name, version);
CREATE INDEX idx_values_active ON values (tree_name, key_hash)
    WHERE deleted_at IS NULL;
```

**Roots Table:**
```sql
CREATE TABLE roots (
    tree_name VARCHAR(255) PRIMARY KEY,
    root_hash BYTEA NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Metadata Table:**
```sql
CREATE TABLE metadata (
    tree_name VARCHAR(255) NOT NULL,
    key VARCHAR(255) NOT NULL,
    value TEXT,
    PRIMARY KEY (tree_name, key)
);
```

### 5.2 Indexing Strategy

**Primary Keys:**
- Composite keys for multi-tenancy (tree_name + entity_id)
- Ensures uniqueness across trees
- Clustered index (PostgreSQL B-tree)

**Hash Indexes (PostgreSQL):**
```sql
CREATE INDEX idx_nodes_hash_lookup ON nodes USING HASH (node_hash);
```
- O(1) for exact-match lookups
- 30-50% faster than B-tree for hash lookups
- Cannot be used for range queries

**Partial Indexes (Hot Data):**
```sql
-- Only index recent versions (last 1000)
CREATE INDEX idx_values_hot ON values (tree_name, key_hash, version)
    WHERE version > (SELECT MAX(version) - 1000 FROM values);
```
- Smaller index size (faster, less memory)
- Covers 99% of queries (recent data)
- Falls back to full scan for historical queries

**Expression Indexes:**
```sql
-- Index on substring for prefix queries
CREATE INDEX idx_nodes_prefix ON nodes
    ((substring(node_data, 1, 8)));
```

---

## 6. Performance Tuning

### 6.1 Batch Operations

**Batch Insert (JDBC):**
```java
try (PreparedStatement ps = conn.prepareStatement(INSERT_QUERY)) {
    for (int i = 0; i < 10000; i++) {
        ps.setBytes(1, keys[i]);
        ps.setBytes(2, values[i]);
        ps.addBatch();

        if (i % 1000 == 0) {
            ps.executeBatch(); // Batch every 1000
        }
    }
    ps.executeBatch(); // Final batch
}
```

**Performance:**
- Single inserts: ~1ms each = 10 seconds for 10K
- Batched inserts: ~100ms total = 100x faster

**PostgreSQL COPY (Fastest):**
```java
CopyManager copyManager = new CopyManager((BaseConnection) conn);
StringReader reader = new StringReader(csvData);
copyManager.copyIn("COPY nodes FROM STDIN WITH CSV", reader);
```
- 10-100x faster than batched inserts
- Best for bulk loading (millions of rows)

### 6.2 Query Optimization

**Use Prepared Statements:**
```java
// Good: Compiled once, executed many times
PreparedStatement ps = conn.prepareStatement("SELECT * FROM nodes WHERE node_hash = ?");
ps.setBytes(1, hash);
ResultSet rs = ps.executeQuery();

// Bad: Compiled every time
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM nodes WHERE node_hash = '" + hash + "'");
```

**Benefits:**
- 10-20% faster execution
- Prevents SQL injection
- Plan caching (server-side)

**Use Connection Pooling:**
- Creating connection: 50-100ms
- Getting from pool: <1ms
- 50-100x faster for short queries

**Projection (Select Only Needed Columns):**
```java
// Good: Only fetch node_data
SELECT node_data FROM nodes WHERE node_hash = ?

// Bad: Fetch all columns
SELECT * FROM nodes WHERE node_hash = ?
```
- 30-50% faster for large tables (fewer bytes transferred)

### 6.3 PostgreSQL-Specific Tuning

**shared_buffers (25% of RAM):**
```sql
-- postgresql.conf
shared_buffers = 4GB  -- For 16GB RAM server
```

**effective_cache_size (50-75% of RAM):**
```sql
effective_cache_size = 12GB  -- Helps query planner
```

**work_mem (Per-operation memory):**
```sql
work_mem = 64MB  -- For sorting/hashing
```

**maintenance_work_mem (For VACUUM, CREATE INDEX):**
```sql
maintenance_work_mem = 1GB
```

**Checkpoint Configuration:**
```sql
checkpoint_completion_target = 0.9  -- Spread writes
wal_buffers = 16MB                  -- WAL buffer
```
