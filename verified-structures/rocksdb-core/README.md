# RocksDB Core

Shared RocksDB utilities, resource management, and namespacing for verified data structures.

## Overview

This module provides common RocksDB infrastructure used by both MPT and JMT RocksDB persistence modules. It handles database lifecycle, connection pooling, namespace isolation, and error handling.

## Key Features

- **Resource Management** - Automatic cleanup of RocksDB instances and native memory
- **Namespace Isolation** - Multiple logical databases in single RocksDB instance
- **Key Prefixing** - Type-safe key prefixing for column families
- **Configuration Helpers** - Optimized RocksDB settings for various workloads
- **Exception Handling** - Structured exception hierarchy for RocksDB errors

## When to Use

This module is automatically included when using:
- `merkle-patricia-trie-rocksdb`
- `jellyfish-merkle-rocksdb`

**Don't use directly** unless building custom RocksDB-backed data structures.

## Core Components

### RocksDbResources

Manages RocksDB database lifecycle with automatic resource cleanup.

```java
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources;
import org.rocksdb.RocksDB;
import java.nio.file.Paths;

// Create with default options
RocksDbResources resources = RocksDbResources.create(Paths.get("data/rocksdb"));

// Access database
RocksDB db = resources.getDb();

// Resources automatically closed when done
resources.close(); // Closes DB and frees native memory
```

**Auto-closeable:**
```java
try (RocksDbResources resources = RocksDbResources.create(path)) {
    RocksDB db = resources.getDb();
    // Use database
} // Automatically closed
```

### RocksDbInitializer

Factory for creating RocksDB instances with optimized settings.

```java
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbInitializer;
import org.rocksdb.Options;

// Create options for write-heavy workload
Options options = RocksDbInitializer.createOptions(true); // true = create if missing

// Custom options
Options customOptions = new Options()
    .setCreateIfMissing(true)
    .setMaxOpenFiles(1000)
    .setWriteBufferSize(64 * 1024 * 1024);

RocksDB db = RocksDbInitializer.open(Paths.get("data"), customOptions);
```

**Default optimizations:**
- Write buffer: 64 MB
- Block size: 16 KB
- Bloom filter: 10 bits per key
- Compression: LZ4
- Max open files: 1000

### KeyPrefixer

Type-safe key prefixing for namespace isolation.

```java
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;

// Create prefixer for a namespace
KeyPrefixer prefixer = new KeyPrefixer("mpt_nodes");

// Prefix keys
byte[] key = "nodeHash123".getBytes();
byte[] prefixedKey = prefixer.prefix(key);
// Result: "mpt_nodes:" + key

// Remove prefix when reading
byte[] originalKey = prefixer.removePrefix(prefixedKey);
```

**Use case**: Store multiple tries in one RocksDB instance:

```java
KeyPrefixer mptNodes = new KeyPrefixer("mpt_nodes");
KeyPrefixer jmtNodes = new KeyPrefixer("jmt_nodes");
KeyPrefixer mptRoots = new KeyPrefixer("mpt_roots");

// Keys don't collide
db.put(mptNodes.prefix(hash), mptNode);
db.put(jmtNodes.prefix(hash), jmtNode);
```

### NamespaceOptions

Configuration for namespace behavior.

```java
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;

NamespaceOptions options = NamespaceOptions.builder()
    .prefix("mpt_tree_1")
    .separator(":")
    .validateKeys(true)  // Check keys match namespace
    .build();

KeyPrefixer prefixer = options.createPrefixer();
```

## Exception Handling

Structured exception hierarchy for better error handling:

```java
import com.bloxbean.cardano.vds.rocksdb.exceptions.*;

try {
    db.put(key, value);
} catch (RocksDbStorageException e) {
    // Storage I/O errors
    logger.error("Storage error", e);
    handleStorageFailure();
} catch (RocksDbConfigurationException e) {
    // Invalid configuration
    logger.error("Configuration error", e);
    System.exit(1);
} catch (RocksDbBatchException e) {
    // Batch operation failed
    logger.error("Batch failed", e);
    rollback();
} catch (RocksDbOperationException e) {
    // General operation error
    logger.error("Operation failed", e);
}
```

### Exception Types

- **RocksDbStorageException** - I/O and storage errors
- **RocksDbConfigurationException** - Invalid configuration
- **RocksDbBatchException** - Batch operation failures
- **RocksDbOperationException** - General operation errors

## Usage Examples

### Multi-Namespace Setup

```java
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;

// Single RocksDB instance
RocksDbResources resources = RocksDbResources.create(Paths.get("data"));
RocksDB db = resources.getDb();

// Multiple logical namespaces
KeyPrefixer accountsTrie = new KeyPrefixer("accounts");
KeyPrefixer contractsTrie = new KeyPrefixer("contracts");
KeyPrefixer metadataTrie = new KeyPrefixer("metadata");

// Store in different namespaces
db.put(accountsTrie.prefix(hash1), accountNode);
db.put(contractsTrie.prefix(hash2), contractNode);
db.put(metadataTrie.prefix(hash3), metadataNode);

// Iterate single namespace
RocksIterator iter = db.newIterator();
byte[] prefix = accountsTrie.prefix(new byte[0]);
iter.seek(prefix);

while (iter.isValid() && hasPrefix(iter.key(), prefix)) {
    byte[] key = accountsTrie.removePrefix(iter.key());
    byte[] value = iter.value();
    // Process account node
    iter.next();
}
```

### Custom RocksDB Configuration

```java
import org.rocksdb.*;

// High-throughput configuration
Options options = new Options()
    .setCreateIfMissing(true)
    .setMaxBackgroundJobs(4)
    .setIncreaseParallelism(4)
    .setMaxOpenFiles(-1)  // Unlimited
    .setCompressionType(CompressionType.LZ4_COMPRESSION)
    .setWriteBufferSize(128 * 1024 * 1024)  // 128 MB
    .setMaxWriteBufferNumber(4)
    .setMinWriteBufferNumberToMerge(2)
    .setLevelCompactionDynamicLevelBytes(true);

// Create resources with custom options
RocksDbResources resources = RocksDbResources.builder()
    .path(Paths.get("data"))
    .options(options)
    .build();
```

### Batch Operations

```java
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

try (WriteBatch batch = new WriteBatch();
     WriteOptions writeOpts = new WriteOptions()) {

    KeyPrefixer prefixer = new KeyPrefixer("nodes");

    // Add multiple operations
    batch.put(prefixer.prefix(key1), value1);
    batch.put(prefixer.prefix(key2), value2);
    batch.delete(prefixer.prefix(oldKey));

    // Atomic write
    db.write(writeOpts, batch);

} catch (RocksDBException e) {
    throw new RocksDbBatchException("Batch write failed", e);
}
```

### Resource Cleanup

```java
// Manual cleanup
RocksDbResources resources = RocksDbResources.create(path);
try {
    RocksDB db = resources.getDb();
    // Use database
} finally {
    resources.close();  // Critical: prevents native memory leak
}

// Or use try-with-resources (recommended)
try (RocksDbResources resources = RocksDbResources.create(path)) {
    RocksDB db = resources.getDb();
    // Automatic cleanup
}
```

## Configuration Guidelines

### Write-Heavy Workload

```java
Options options = new Options()
    .setWriteBufferSize(128 * 1024 * 1024)  // Large write buffer
    .setMaxWriteBufferNumber(4)
    .setMinWriteBufferNumberToMerge(2)
    .setCompressionType(CompressionType.LZ4_COMPRESSION); // Fast compression
```

### Read-Heavy Workload

```java
Options options = new Options()
    .setMaxOpenFiles(-1)  // Cache all files
    .setTableFormatConfig(
        new BlockBasedTableConfig()
            .setBlockSize(64 * 1024)  // Large blocks
            .setCacheIndexAndFilterBlocks(true)
            .setFilterPolicy(new BloomFilter(10, false))
    );
```

### Space-Constrained

```java
Options options = new Options()
    .setCompressionType(CompressionType.ZSTD_COMPRESSION)  // High compression
    .setMaxOpenFiles(100)  // Limit open files
    .setWriteBufferSize(16 * 1024 * 1024);  // Small buffer
```

## Gradle Dependency

```gradle
dependencies {
    api 'com.bloxbean.cardano:rocksdb-core:0.8.0'
    implementation 'org.rocksdb:rocksdbjni:8.9.1'
}
```

**Note**: Typically not used directly. Included automatically by:
- `merkle-patricia-trie-rocksdb`
- `jellyfish-merkle-rocksdb`

## Package Structure

```
com.bloxbean.cardano.vds.rocksdb
├── resources/
│   ├── RocksDbResources.java      # Lifecycle management
│   └── RocksDbInitializer.java    # Factory methods
├── namespace/
│   ├── KeyPrefixer.java           # Key prefixing
│   └── NamespaceOptions.java      # Namespace config
└── exceptions/
    ├── RocksDbStorageException.java
    ├── RocksDbConfigurationException.java
    ├── RocksDbBatchException.java
    └── RocksDbOperationException.java
```

## Best Practices

### 1. Always Close Resources

```java
// Bad: Memory leak
RocksDbResources resources = RocksDbResources.create(path);
RocksDB db = resources.getDb();
// Forgot to close!

// Good: Automatic cleanup
try (RocksDbResources resources = RocksDbResources.create(path)) {
    RocksDB db = resources.getDb();
}
```

### 2. Use Namespaces for Isolation

```java
// Bad: Manual prefixing (error-prone)
db.put(("mpt_" + hash).getBytes(), node);

// Good: Type-safe prefixing
KeyPrefixer prefixer = new KeyPrefixer("mpt");
db.put(prefixer.prefix(hash), node);
```

### 3. Batch Writes When Possible

```java
// Bad: Individual writes (slow)
for (Entry e : entries) {
    db.put(e.key, e.value);
}

// Good: Batch write (fast + atomic)
try (WriteBatch batch = new WriteBatch()) {
    for (Entry e : entries) {
        batch.put(e.key, e.value);
    }
    db.write(new WriteOptions(), batch);
}
```

### 4. Handle Exceptions Appropriately

```java
try {
    db.put(key, value);
} catch (RocksDbStorageException e) {
    // Recoverable: retry or failover
    logger.error("Storage error", e);
    switchToBackupStorage();
} catch (RocksDbConfigurationException e) {
    // Fatal: exit
    logger.error("Fatal configuration error", e);
    System.exit(1);
}
```

## Performance Tuning

### Memory Settings

```java
// Limit total memory usage
long memBudget = 512 * 1024 * 1024; // 512 MB

Options options = new Options()
    .setWriteBufferSize(memBudget / 4)  // 25% for write buffer
    .setMaxWriteBufferNumber(3)
    .setTableFormatConfig(
        new BlockBasedTableConfig()
            .setBlockCache(new LRUCache(memBudget / 2))  // 50% for block cache
    );
```

### Compaction Tuning

```java
Options options = new Options()
    .setLevelCompactionDynamicLevelBytes(true)
    .setMaxBackgroundJobs(4)
    .setMaxBytesForLevelBase(256 * 1024 * 1024)
    .setTargetFileSizeBase(64 * 1024 * 1024);
```

## Thread Safety

- **RocksDB**: Thread-safe for reads, writes need coordination
- **RocksDbResources**: Thread-safe
- **KeyPrefixer**: Immutable, thread-safe
- **WriteBatch**: NOT thread-safe, use separate instances per thread

## Related Modules

- [merkle-patricia-trie-rocksdb](../merkle-patricia-trie-rocksdb/) - MPT RocksDB backend
- [jellyfish-merkle-rocksdb](../jellyfish-merkle-rocksdb/) - JMT RocksDB backend
- [rdbms-core](../rdbms-core/) - Alternative: SQL database utilities

