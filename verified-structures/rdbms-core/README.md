# RDBMS Core

Shared SQL database utilities for verified data structures with multi-dialect support.

## Overview

Common RDBMS infrastructure for MPT and JMT persistence, supporting PostgreSQL, H2, and SQLite with connection pooling, schema management, and SQL dialect abstraction.

## Key Features

- **Multi-Dialect Support** - PostgreSQL, H2, SQLite
- **Connection Pooling** - HikariCP integration
- **Schema Management** - Automatic table creation and versioning
- **Key Encoding** - Efficient binary key storage
- **Table Namespacing** - Multiple tries in one database
- **Type Safety** - SQL dialect abstraction layer

## Supported Databases

| Database | Use Case | Performance | Features |
|----------|----------|-------------|----------|
| **PostgreSQL** | Production | High | Full SQL, replication, distributed |
| **H2** | Testing | Very High | In-memory, embedded, SQL compatibility |
| **SQLite** | Embedded | Medium | Single-file, zero-config, portable |

## Quick Start

### PostgreSQL

```java
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.bloxbean.cardano.vds.rdbms.common.SimpleDataSource;
import javax.sql.DataSource;

// Configure PostgreSQL
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:postgresql://localhost:5432/mpt_db")
    .username("postgres")
    .password("password")
    .driverClassName("org.postgresql.Driver")
    .build();

// Create connection pool
DataSource dataSource = SimpleDataSource.create(config);

// Use in MPT or JMT store
RdbmsNodeStore nodeStore = new RdbmsNodeStore(dataSource);
```

### H2 (In-Memory for Testing)

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
    .username("sa")
    .password("")
    .build();

DataSource dataSource = SimpleDataSource.create(config);
```

### SQLite (Embedded)

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:sqlite:data/mpt.db")
    .build();

DataSource dataSource = SimpleDataSource.create(config);
```

## Core Components

### DbConfig

Database connection configuration with HikariCP pooling.

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:postgresql://localhost/db")
    .username("user")
    .password("pass")
    .driverClassName("org.postgresql.Driver")
    // HikariCP settings
    .maximumPoolSize(10)
    .minimumIdle(2)
    .connectionTimeout(30000)  // 30 seconds
    .idleTimeout(600000)        // 10 minutes
    .maxLifetime(1800000)       // 30 minutes
    .build();
```

### SimpleDataSource

Factory for creating HikariCP-backed data sources.

```java
import com.bloxbean.cardano.vds.rdbms.common.SimpleDataSource;

// Create from config
DataSource ds = SimpleDataSource.create(config);

// Use with JDBC
try (Connection conn = ds.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    ResultSet rs = stmt.executeQuery();
}

// Cleanup
if (ds instanceof AutoCloseable) {
    ((AutoCloseable) ds).close();
}
```

### SQL Dialect Abstraction

Support for database-specific SQL variations.

```java
import com.bloxbean.cardano.vds.rdbms.dialect.*;

// Auto-detect from JDBC URL
SqlDialect dialect = SqlDialect.fromJdbcUrl(
    "jdbc:postgresql://localhost/db"
);

// Or create explicitly
SqlDialect postgres = new PostgresDialect();
SqlDialect h2 = new H2Dialect();
SqlDialect sqlite = new SqliteDialect();

// Use dialect for SQL generation
String createTable = dialect.createTableSql(
    "nodes",
    "hash BYTEA PRIMARY KEY, data BYTEA"
);

String upsert = dialect.upsertSql(
    "nodes",
    new String[]{"hash"},
    new String[]{"data"}
);
```

### TableNameResolver

Manages table naming with optional prefixes for namespace isolation.

```java
import com.bloxbean.cardano.vds.rdbms.common.TableNameResolver;

// No prefix (default table names)
TableNameResolver resolver = TableNameResolver.noPrefix();
String nodesTable = resolver.nodes();  // "nodes"

// With prefix (namespace isolation)
TableNameResolver resolver = TableNameResolver.withPrefix("mpt_accounts");
String nodesTable = resolver.nodes();  // "mpt_accounts_nodes"
String rootsTable = resolver.roots();  // "mpt_accounts_roots"
```

### KeyCodec

Encodes/decodes binary keys for SQL storage.

```java
import com.bloxbean.cardano.vds.rdbms.common.StandardKeyCodec;
import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;

KeyCodec codec = new StandardKeyCodec();

// Encode binary key to string
byte[] hash = new byte[]{0x01, 0x02, 0x03};
String encoded = codec.encode(hash);  // Hex or Base64

// Decode back to binary
byte[] decoded = codec.decode(encoded);
```

## SQL Dialect Features

### PostgreSQL Dialect

```java
PostgresDialect postgres = new PostgresDialect();

// UPSERT using ON CONFLICT
String sql = postgres.upsertSql("nodes",
    new String[]{"hash"},
    new String[]{"data"}
);
// INSERT INTO nodes (hash, data) VALUES (?, ?)
// ON CONFLICT (hash) DO UPDATE SET data = EXCLUDED.data

// BYTEA type for binary data
String createTable = postgres.createTableSql("nodes",
    "hash BYTEA PRIMARY KEY, data BYTEA"
);

// Batch insert optimization
boolean supportsBatch = postgres.supportsBatchInsert();  // true
```

### H2 Dialect

```java
H2Dialect h2 = new H2Dialect();

// MERGE statement for upsert
String sql = h2.upsertSql("nodes",
    new String[]{"hash"},
    new String[]{"data"}
);
// MERGE INTO nodes (hash, data) KEY(hash) VALUES (?, ?)

// BINARY type
String createTable = h2.createTableSql("nodes",
    "hash BINARY(32) PRIMARY KEY, data BINARY"
);
```

### SQLite Dialect

```java
SqliteDialect sqlite = new SqliteDialect();

// INSERT OR REPLACE for upsert
String sql = sqlite.upsertSql("nodes",
    new String[]{"hash"},
    new String[]{"data"}
);
// INSERT OR REPLACE INTO nodes (hash, data) VALUES (?, ?)

// BLOB type
String createTable = sqlite.createTableSql("nodes",
    "hash BLOB PRIMARY KEY, data BLOB"
);
```

## Usage Examples

### Multi-Namespace Setup

```java
// Single database, multiple tries
DataSource ds = SimpleDataSource.create(config);

// Different table prefixes for isolation
TableNameResolver accounts = TableNameResolver.withPrefix("accounts");
TableNameResolver contracts = TableNameResolver.withPrefix("contracts");

// Create stores with different namespaces
RdbmsNodeStore accountsStore = new RdbmsNodeStore(ds, accounts);
RdbmsNodeStore contractsStore = new RdbmsNodeStore(ds, contracts);

// Tables: accounts_nodes, accounts_roots, contracts_nodes, contracts_roots
```

### Connection Pooling

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl("jdbc:postgresql://localhost/db")
    .username("user")
    .password("pass")
    // Pool configuration
    .maximumPoolSize(20)      // Max connections
    .minimumIdle(5)           // Min idle connections
    .connectionTimeout(10000) // 10 sec timeout
    .build();

DataSource ds = SimpleDataSource.create(config);

// Pool automatically manages connections
// No need to create/close connections manually
```

### Transaction Management

```java
DataSource ds = SimpleDataSource.create(config);

try (Connection conn = ds.getConnection()) {
    conn.setAutoCommit(false);

    try {
        // Multiple operations
        nodeStore.put(hash1, node1, conn);
        nodeStore.put(hash2, node2, conn);
        rootsIndex.put(version, rootHash, conn);

        // Atomic commit
        conn.commit();
    } catch (SQLException e) {
        conn.rollback();
        throw e;
    }
}
```

### Schema Creation

```java
SqlDialect dialect = SqlDialect.fromJdbcUrl(config.getJdbcUrl());
TableNameResolver tables = TableNameResolver.noPrefix();

String createNodes = String.format(
    "CREATE TABLE IF NOT EXISTS %s (hash BYTEA PRIMARY KEY, data BYTEA)",
    tables.nodes()
);

String createRoots = String.format(
    "CREATE TABLE IF NOT EXISTS %s (version BIGINT PRIMARY KEY, root_hash BYTEA)",
    tables.roots()
);

try (Connection conn = ds.getConnection();
     Statement stmt = conn.createStatement()) {
    stmt.execute(createNodes);
    stmt.execute(createRoots);
}
```

## Performance Tuning

### PostgreSQL

```sql
-- Create indexes
CREATE INDEX idx_nodes_hash ON nodes USING HASH (hash);
CREATE INDEX idx_roots_version ON roots (version);

-- Increase work_mem for sorting
SET work_mem = '256MB';

-- Enable parallel queries
SET max_parallel_workers_per_gather = 4;

-- Optimize autovacuum
ALTER TABLE nodes SET (autovacuum_vacuum_scale_factor = 0.01);
```

### HikariCP Tuning

```java
DbConfig config = DbConfig.builder()
    .jdbcUrl(url)
    // Match pool size to CPU cores
    .maximumPoolSize(Runtime.getRuntime().availableProcessors() * 2)
    // Keep some idle connections
    .minimumIdle(2)
    // Fast connection acquisition
    .connectionTimeout(5000)
    // Detect stale connections
    .maxLifetime(1800000)  // 30 minutes
    .build();
```

## Gradle Dependency

```gradle
dependencies {
    api 'com.bloxbean.cardano:rdbms-core:0.8.0'

    // HikariCP included transitively
    // Add database driver:
    implementation 'org.postgresql:postgresql:42.6.0'
    // or
    implementation 'com.h2database:h2:2.2.224'
    // or
    implementation 'org.xerial:sqlite-jdbc:3.45.0.0'
}
```

## Package Structure

```
com.bloxbean.cardano.vds.rdbms
├── common/
│   ├── DbConfig.java              # Connection configuration
│   ├── SimpleDataSource.java      # DataSource factory
│   ├── TableNameResolver.java     # Table naming
│   ├── KeyCodec.java              # Binary key encoding
│   └── StandardKeyCodec.java      # Default codec
└── dialect/
    ├── SqlDialect.java            # Abstract dialect
    ├── PostgresDialect.java       # PostgreSQL
    ├── H2Dialect.java             # H2
    └── SqliteDialect.java         # SQLite
```

## Best Practices

### 1. Use Connection Pooling

```java
// Good: Connection pool
DataSource ds = SimpleDataSource.create(config);

// Bad: Creating connections manually
Connection conn = DriverManager.getConnection(url, user, pass);
```

### 2. Always Use Transactions

```java
try (Connection conn = ds.getConnection()) {
    conn.setAutoCommit(false);
    // Operations
    conn.commit();
}
```

### 3. Close Resources Properly

```java
try (Connection conn = ds.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    // Use statement
} // Auto-close
```

### 4. Use Batch Operations

```java
try (Connection conn = ds.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    for (Entry e : entries) {
        stmt.setBytes(1, e.hash);
        stmt.setBytes(2, e.data);
        stmt.addBatch();
    }
    stmt.executeBatch();
}
```

## Thread Safety

- **SimpleDataSource**: Thread-safe (HikariCP pooling)
- **SqlDialect**: Immutable, thread-safe
- **TableNameResolver**: Immutable, thread-safe
- **KeyCodec**: Immutable, thread-safe

## Related Modules

- [merkle-patricia-trie-rdbms](../merkle-patricia-trie-rdbms/) - MPT SQL backend
- [jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/) - JMT SQL backend
- [rocksdb-core](../rocksdb-core/) - Alternative: RocksDB utilities
