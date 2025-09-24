# state-trees-rocksdb

High-performance RocksDB storage backend for Merkle Patricia Trie state trees in the Cardano ecosystem.

## Overview

The `state-trees-rocksdb` module provides persistent, production-ready storage for Merkle Patricia Tries using RocksDB as the underlying storage engine. It implements both the `NodeStore` and `RootsIndex` interfaces with advanced features like batch operations, garbage collection, and versioned state management.

### Key Features

- **High Performance**: Built on RocksDB for optimal read/write performance
- **ACID Transactions**: Atomic batch operations with read-your-writes consistency  
- **Versioned Storage**: Built-in support for historical state management
- **Garbage Collection**: Advanced GC framework to reclaim unused storage
- **Thread Safety**: ThreadLocal operation contexts for concurrent access
- **Auto-Management**: Automatic column family creation and lifecycle management

### Architecture

The module uses a multi-column-family RocksDB design:

- **`default`**: RocksDB system metadata
- **`nodes`**: MPT node storage (hash → CBOR-encoded node)
- **`roots`**: Versioned root hash storage (version → root hash)

## Quick Start

### Add Dependency

Add the following to your `build.gradle`:

```gradle
dependencies {
    implementation project(':state-trees-rocksdb')
    implementation 'org.rocksdb:rocksdbjni:8.8.1' // or compatible version
}
```

### Basic Usage

#### Option 1: Unified Manager (Recommended)

```java
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbStateTrees;

// Create unified database manager
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("/path/to/database")) {
    // Get storage components
    var nodeStore = stateTrees.nodeStore();
    var rootsIndex = stateTrees.rootsIndex();
    
    // Create MPT instance
    var trie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest);
    
    // Perform operations
    trie.put("user123".getBytes(), "userData".getBytes());
    trie.put("balance".getBytes(), "1000".getBytes());
    
    // Store versioned root (e.g., by block height)
    byte[] rootHash = trie.getRootHash();
    rootsIndex.put(blockHeight, rootHash);
    
    // Query historical roots
    byte[] historicalRoot = rootsIndex.get(blockHeight - 10);
    var historicalTrie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest, historicalRoot);
}
```

#### Option 2: Separate Instances

```java
// Create separate storage instances
try (var nodeStore = new RocksDbNodeStore("/path/to/nodes");
     var rootsIndex = new RocksDbRootsIndex("/path/to/roots")) {
     
    var trie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest);
    
    // Use trie...
    trie.put("key".getBytes(), "value".getBytes());
}
```

## Advanced Features

### Batch Operations

For optimal performance when performing multiple operations, use batch contexts:

```java
try (var stateTrees = new RocksDbStateTrees("/path/to/db")) {
    var nodeStore = stateTrees.nodeStore();
    var rootsIndex = stateTrees.rootsIndex();
    var trie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest);
    
    try (var batch = new org.rocksdb.WriteBatch()) {
        // Execute multiple operations atomically
        byte[] newRoot = nodeStore.withBatch(batch, () -> {
            trie.put("key1".getBytes(), "value1".getBytes());
            trie.put("key2".getBytes(), "value2".getBytes());
            trie.put("key3".getBytes(), "value3".getBytes());
            return trie.getRootHash();
        });
        
        // Store the new root in the same batch
        rootsIndex.withBatch(batch, () -> {
            rootsIndex.put(nextVersion, newRoot);
            return null;
        });
        
        // Commit all operations atomically
        stateTrees.db().write(new org.rocksdb.WriteOptions(), batch);
    }
}
```

### Historical State Access

```java
try (var stateTrees = new RocksDbStateTrees("/path/to/db")) {
    var nodeStore = stateTrees.nodeStore();
    var rootsIndex = stateTrees.rootsIndex();
    
    // Get all historical versions
    var allRoots = rootsIndex.listAll();
    System.out.println("Total versions: " + allRoots.size());
    
    // Get roots for a specific range (e.g., blocks 1000-2000)
    var rangeRoots = rootsIndex.listRange(1000, 2000);
    
    // Access state at specific version
    byte[] rootAtBlock1500 = rootsIndex.get(1500);
    if (rootAtBlock1500 != null) {
        var historicalTrie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest, rootAtBlock1500);
        byte[] historicalValue = historicalTrie.get("someKey".getBytes());
    }
    
    // Get the latest root quickly
    byte[] latestRoot = rootsIndex.latest();
}
```

### Version Management

```java
try (var stateTrees = new RocksDbStateTrees("/path/to/db")) {
    var rootsIndex = stateTrees.rootsIndex();
    
    // Get next version automatically
    long nextVersion = rootsIndex.nextVersion();
    
    // Store new root with auto-incremented version
    rootsIndex.put(nextVersion, newRootHash);
    
    // Check what's the latest version
    long latestVersion = rootsIndex.lastVersion();
    System.out.println("Latest version: " + latestVersion);
}
```

## Garbage Collection

The module includes a comprehensive garbage collection framework to reclaim storage from unreferenced nodes:

### Basic Garbage Collection

```java
import com.bloxbean.cardano.statetrees.rocksdb.gc.*;

try (var stateTrees = new RocksDbStateTrees("/path/to/db")) {
    var gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
    
    // Define retention policy (keep last 100 versions)
    var retentionPolicy = RetentionPolicy.keepLastVersions(100);
    
    // Configure GC options
    var gcOptions = GcOptions.builder()
        .setBatchSize(1000)
        .setReportProgress(true)
        .build();
    
    // Choose GC strategy
    var gcStrategy = new RefcountGcStrategy();
    
    // Run synchronous GC
    GcReport report = gcManager.runSync(gcStrategy, retentionPolicy, gcOptions);
    System.out.println("GC Results:");
    System.out.println("- Deleted nodes: " + report.getDeletedNodeCount());
    System.out.println("- Reclaimed bytes: " + report.getReclaimedBytes());
    System.out.println("- Duration: " + report.getDurationMs() + "ms");
}
```

### Asynchronous Garbage Collection

```java
// Run GC in background
gcManager.runAsync(gcStrategy, retentionPolicy, gcOptions, report -> {
    System.out.println("Background GC completed:");
    System.out.println("Deleted " + report.getDeletedNodeCount() + " nodes");
    System.out.println("Reclaimed " + report.getReclaimedBytes() + " bytes");
});
```

### Available GC Strategies

1. **RefcountGcStrategy**: Uses reference counting for fast, incremental GC
2. **OnDiskMarkSweepStrategy**: Traditional mark-and-sweep for comprehensive cleanup
3. **Custom strategies**: Implement `GcStrategy` interface for specialized needs

### Retention Policies

```java
// Keep last N versions
RetentionPolicy.keepLastVersions(100);

// Keep versions newer than timestamp
RetentionPolicy.keepNewerThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30));

// Keep specific versions
RetentionPolicy.keepVersions(Set.of(1000L, 2000L, 3000L));

// Custom policy
RetentionPolicy.custom(version -> version % 1000 == 0); // Keep every 1000th version
```

## Configuration and Tuning

### RocksDB Configuration

You can customize RocksDB options for your workload:

```java
// For write-heavy workloads
var options = new DBOptions()
    .setCreateIfMissing(true)
    .setCreateMissingColumnFamilies(true)
    .setMaxBackgroundJobs(4)
    .setWriteBufferSize(64 * 1024 * 1024); // 64MB

var cfOptions = new ColumnFamilyOptions()
    .setWriteBufferSize(64 * 1024 * 1024)
    .setMaxWriteBufferNumber(3)
    .setTargetFileSizeBase(64 * 1024 * 1024)
    .setCompactionStyle(CompactionStyle.LEVEL);

// Apply custom options during database creation
// (requires custom initialization - see advanced documentation)
```

### Performance Tips

1. **Use Batch Operations**: Group multiple operations for better throughput
2. **Tune Buffer Sizes**: Increase write buffer sizes for write-heavy workloads
3. **Monitor Compaction**: Watch for compaction pressure in high-write scenarios
4. **Regular GC**: Schedule periodic garbage collection to maintain performance
5. **SSD Storage**: Use SSDs for better random I/O performance

## Monitoring and Observability

### Database Statistics

```java
try (var stateTrees = new RocksDbStateTrees("/path/to/db")) {
    var db = stateTrees.db();
    
    // Get database statistics
    String stats = db.getProperty("rocksdb.stats");
    System.out.println("RocksDB Stats:\n" + stats);
    
    // Get column family specific stats
    String nodeStats = db.getProperty(stateTrees.nodeStore().nodesHandle(), 
                                     "rocksdb.estimate-num-keys");
    System.out.println("Estimated node count: " + nodeStats);
    
    // Memory usage
    String memUsage = db.getProperty("rocksdb.estimate-table-readers-mem");
    System.out.println("Table readers memory: " + memUsage);
}
```

### Health Checks

```java
public class TrieStorageHealthCheck {
    private final RocksDbStateTrees stateTrees;
    
    public boolean isHealthy() {
        try {
            // Test basic operations
            var nodeStore = stateTrees.nodeStore();
            var rootsIndex = stateTrees.rootsIndex();
            
            // Test node store
            byte[] testHash = Blake2b256.digest("test".getBytes());
            nodeStore.put(testHash, "test-node".getBytes());
            byte[] retrieved = nodeStore.get(testHash);
            nodeStore.delete(testHash);
            
            // Test roots index
            byte[] latestRoot = rootsIndex.latest();
            
            return retrieved != null && Arrays.equals(retrieved, "test-node".getBytes());
        } catch (Exception e) {
            return false;
        }
    }
}
```

## Error Handling and Recovery

### Common Issues and Solutions

1. **RocksDBException**: Usually indicates I/O issues or corruption
   ```java
   try {
       // RocksDB operations
   } catch (org.rocksdb.RocksDBException e) {
       logger.error("RocksDB operation failed", e);
       // Handle appropriately - may need database repair
   }
   ```

2. **Resource Leaks**: Always use try-with-resources
   ```java
   // Good
   try (var stateTrees = new RocksDbStateTrees("/path")) {
       // operations
   }
   
   // Bad - may leak resources
   var stateTrees = new RocksDbStateTrees("/path");
   // operations
   // stateTrees.close(); // may be forgotten
   ```

3. **Corruption Recovery**: RocksDB provides repair utilities
   ```java
   // Attempt database repair (use with caution)
   org.rocksdb.RepairDB.repairDB("/path/to/db", new Options());
   ```

## Migration and Upgrades

When upgrading between versions, consider:

1. **Backup First**: Always backup your data before upgrading
2. **Compatibility**: Check RocksDB version compatibility
3. **Schema Changes**: Review any column family or key format changes
4. **Test Migration**: Test upgrade path with non-production data

## Troubleshooting

### Common Problems

| Issue | Symptoms | Solution |
|-------|----------|----------|
| Poor write performance | High latency on put operations | Increase write buffer sizes, use batch operations |
| High memory usage | OOM errors, slow performance | Tune block cache, reduce write buffer count |
| Compaction pressure | High CPU usage, slow reads | Adjust compaction threads, file size targets |
| Storage bloat | Disk usage grows indefinitely | Implement regular garbage collection |
| Startup failures | Database won't open | Check file permissions, disk space, corruption |

### Debug Logging

Enable detailed logging for troubleshooting:

```java
// Enable RocksDB logging
System.setProperty("rocksdb.logging.level", "INFO");

// Monitor batch operations
logger.debug("Batch operation completed: {} nodes written", batchSize);
```

## Examples and Integration

See the `src/test/java` directory for comprehensive examples:

- `RocksDbMptParityTest`: Basic MPT operations
- `RocksDbSharedDbTest`: Shared database scenarios  
- `RefcountGcIntegrationTest`: Garbage collection workflows

## Dependencies

- **RocksDB**: `org.rocksdb:rocksdbjni:8.8.1+`
- **state-trees**: Core MPT implementation
- **CBOR**: Node serialization (via state-trees)

## License

This module is part of the cardano-client-lib project and follows the same licensing terms.

## Contributing

Contributions are welcome! Please:

1. Follow existing code style and patterns
2. Add comprehensive tests for new features
3. Update documentation for public APIs
4. Consider performance implications of changes

For questions or support, please refer to the main project documentation or open an issue in the project repository.

### MPT Batched Writes (Session Helper)

For a JMT-like ergonomics (single batch + write options in one place), use
`com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbMptSession`:

```
import static java.nio.charset.StandardCharsets.UTF_8;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbMptSession;

try (RocksDbNodeStore nodeStore = new RocksDbNodeStore("/path/mpt-db")) {
  MerklePatriciaTrie trie = new MerklePatriciaTrie(nodeStore, Blake2b256::digest);

  // Optional: disable WAL for throughput benchmarking (unsafe for durability)
  RocksDbMptSession.Options opts = RocksDbMptSession.Options.builder()
      .disableWal(true)
      .build();

  try (RocksDbMptSession session = RocksDbMptSession.of(nodeStore, opts)) {
    session.write(() -> {
      trie.put("alice".getBytes(UTF_8), "100".getBytes(UTF_8));
      trie.put("bob".getBytes(UTF_8),   "200".getBytes(UTF_8));
      trie.delete("carol".getBytes(UTF_8));
      return null;
    });
  }
}
```

This keeps RocksDB-specific knobs (like WAL) in the RocksDB adapter, while the MPT API stays
platform-agnostic — similar to how `RocksDbJmtStore.Options` configures JMT batches.

