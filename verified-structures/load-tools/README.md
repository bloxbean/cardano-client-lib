# Load Testing Tools

This module provides comprehensive load testing and benchmarking tools for all verifiable data structures (JMT, MPT, MPF) across all storage backends (RocksDB, H2, SQLite, PostgreSQL, in-memory).

## Quick Start

### Option 1: Build Fat JAR with Tools Entry Point (Recommended)

Build a single JAR with unified entry point and all dependencies bundled:

```bash
# Build the fat JAR
./gradlew :verified-structures:load-tools:shadowJar

# Run any tool (simplified syntax)
java -jar verified-structures/load-tools/build/libs/cardano-client-vds-load-tools-0.7.0-beta4-all.jar jmt \
    --records=10000 --batch=1000 --rocksdb=/tmp/jmt-test

# Run RDBMS tool (example: JMT with PostgreSQL)
java -jar verified-structures/load-tools/build/libs/cardano-client-vds-load-tools-0.7.0-beta4-all.jar jmt-rdbms \
    --records=10000 --batch=1000 --db=postgresql \
    --db-host=localhost --db-name=testdb \
    --db-user=postgres --db-password=secret

# Show help
java -jar verified-structures/load-tools/build/libs/cardano-client-vds-load-tools-0.7.0-beta4-all.jar help
```

**Available Tools:**
- `jmt` - Jellyfish Merkle Tree with RocksDB backend
- `jmt-rdbms` - Jellyfish Merkle Tree with H2/SQLite/PostgreSQL backend
- `jmt-concurrent` - JMT concurrent load testing with multiple threads
- `mpt` - Merkle Patricia Trie with RocksDB backend
- `mpt-rdbms` - Merkle Patricia Trie with H2/SQLite/PostgreSQL backend
- `gc` - Garbage collection tool for MPT
- `help` - Show detailed help

### Option 2: Run Specific Tool Class Directly

```bash
# Run directly via class name (alternative)
java -cp verified-structures/load-tools/build/libs/cardano-client-vds-load-tools-0.7.0-beta4-all.jar \
    com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester \
    --records=10000 --batch=1000 --rocksdb=/tmp/jmt-test
```

### Option 3: Run via Gradle

```bash
# Run via Gradle (using Tools entry point)
./gradlew :verified-structures:load-tools:run --args="jmt --records=10000 --batch=1000 --memory"

# Run specific tool class via Gradle (alternative)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=10000 --batch=1000 --memory"
```

## Features

- ✅ **All Data Structures**: JMT (Jellyfish Merkle Tree), MPT (Merkle Patricia Trie), MPF (Merkle Patricia Forestry)
- ✅ **All Storage Backends**: RocksDB, H2, SQLite, PostgreSQL, In-Memory
- ✅ **Complete Dependencies**: All database drivers included - no need to modify `build.gradle`
- ✅ **Standalone Fat JAR**: Single executable with all dependencies bundled
- ✅ **Performance Metrics**: Throughput, latency, storage statistics, RocksDB health monitoring
- ✅ **Advanced Features**: Pruning, proof generation, concurrent testing, GC tools
- ✅ **Database Credentials**: Support for --db-user, --db-password, --db-host, --db-port, --db-name

## Available Tools

### JMT (Jellyfish Merkle Tree)

#### 1. JmtLoadTester - RocksDB Performance Testing

Tests JMT with RocksDB backend for sustained write performance, proof generation, and pruning.

```bash
# Basic load test - 1M operations
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=1000000 --batch=1000 --rocksdb=/tmp/jmt-load"

# With pruning enabled
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=1000000 --batch=1000 --prune-every=100 --keep-latest=1000 --rocksdb=/tmp/jmt-load"

# High throughput test (large batches, no WAL)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=10000000 --batch=5000 --no-wal --rocksdb=/tmp/jmt-fast"

# In-memory baseline
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=100000 --batch=1000 --memory"
```

**Key Options:**
- `--records=N` - Total operations (default: 1,000,000)
- `--batch=N` - Updates per batch (default: 1000)
- `--value-size=N` - Value size in bytes (default: 128)
- `--update-ratio=F` - Fraction of updates vs inserts (default: 0.2)
- `--rocksdb=PATH` - RocksDB directory (default: ./jmt-load-db)
- `--prune-every=N` - Run pruning every N batches
- `--keep-latest=N` - Keep N latest versions when pruning (default: 1000)
- `--progress=N` - Progress reporting interval (default: 100,000)
- `--memory` - Use in-memory store instead of RocksDB

#### 2. JmtConcurrentLoadTester - Concurrent Write Testing

Tests JMT under concurrent write load with multiple threads.

```bash
# Concurrent test with 4 threads
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtConcurrentLoadTester --threads=4 --records=1000000 --batch=1000 --rocksdb=/tmp/jmt-concurrent"

# High concurrency test
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtConcurrentLoadTester --threads=16 --records=10000000 --batch=5000 --rocksdb=/tmp/jmt-concurrent-high"
```

**Key Options:**
- `--threads=N` - Number of concurrent writer threads (default: 4)
- All options from `JmtLoadTester` are supported

#### 3. RdbmsJmtLoadTester - RDBMS Backend Testing

Tests JMT with H2, SQLite, or PostgreSQL backends.

```bash
# H2 database (default)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/jmt-h2"

# SQLite database
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/jmt.db"

# PostgreSQL (requires running PostgreSQL instance)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=postgresql --db-host=localhost --db-name=testdb --db-user=postgres --db-password=secret"

# In-memory baseline
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --memory"
```

**Key Options:**
- `--db=TYPE` - Database type: h2, sqlite, postgresql (default: h2)
- `--path=PATH` - Database file path (default: ./jmt-load-db)
- `--jdbc-url=URL` - Custom JDBC URL (overrides --db and --path)
- `--prune-every=N` - Run pruning every N batches (RDBMS-specific)
- `--memory` - Use in-memory JMT store (no RDBMS)

### MPT (Merkle Patricia Trie)

#### 4. MptLoadTester - RocksDB Performance Testing

Tests MPT with RocksDB backend, supports both plain MPT and SecureTrie (MPF) modes.

```bash
# SecureTrie/MPF mode (default)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.MptLoadTester --records=1000000 --batch=1000 --secure --rocksdb=/tmp/mpt-load"

# Plain MPT mode
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.MptLoadTester --records=1000000 --batch=1000 --plain --rocksdb=/tmp/mpt-load"

# With deletes (10% delete ratio)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.MptLoadTester --records=1000000 --batch=1000 --delete-ratio=0.1 --rocksdb=/tmp/mpt-load"

# With garbage collection enabled (refcount mode)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.MptLoadTester --records=1000000 --batch=1000 --gc=refcount --rocksdb=/tmp/mpt-load"
```

**Key Options:**
- `--secure` - Use SecureTrie/MPF mode (default)
- `--plain` - Use plain MerklePatriciaTrie mode
- `--delete-ratio=F` - Fraction of deletes per batch (default: 0.0)
- `--gc=MODE` - Garbage collection mode: none, refcount, mark-sweep (default: none)
- `--proof-every=N` - Generate proof every N batches

#### 5. RdbmsMptLoadTester - RDBMS Backend Testing

Tests MPT with H2, SQLite, or PostgreSQL backends.

```bash
# H2 database with SecureTrie/MPF mode
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.RdbmsMptLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/mpt-h2 --secure"

# SQLite with deletes
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.RdbmsMptLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/mpt.db --delete-ratio=0.1"

# PostgreSQL
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.RdbmsMptLoadTester --records=100000 --batch=1000 --db=postgresql --db-host=localhost --db-name=testdb --db-user=postgres --db-password=secret"
```

**Key Options:**
- `--secure` - Use SecureTrie/MPF mode (default)
- `--plain` - Use plain MerklePatriciaTrie mode
- `--delete-ratio=F` - Fraction of deletes per batch (default: 0.0)
- `--db=TYPE` - Database type: h2, sqlite, postgresql (default: h2)

#### 6. GcTool - Garbage Collection Utility

Runs garbage collection on RocksDB-based MPT storage to reclaim space from stale nodes.

```bash
# Refcount GC up to version 10000
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.GcTool --db=/tmp/mpt-rocksdb --gc-type=refcount --version=10000"

# Mark-sweep GC (full scan)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.GcTool --db=/tmp/mpt-rocksdb --gc-type=mark-sweep --version=10000"

# Dry-run mode (estimate space savings without deleting)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.mpt.GcTool --db=/tmp/mpt-rocksdb --gc-type=refcount --version=10000 --dry-run"
```

**Key Options:**
- `--db=PATH` - RocksDB directory
- `--gc-type=MODE` - Garbage collection mode: refcount, mark-sweep
- `--version=N` - Target version for GC (prune versions older than this)
- `--dry-run` - Estimate space savings without actually deleting

## Backend Comparison Guide

### Testing Same Workload Across Backends

Compare JMT performance across all backends:

```bash
# 1. RocksDB
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=100000 --batch=1000 --rocksdb=/tmp/jmt-rocks"

# 2. H2
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=h2 --path=/tmp/jmt-h2"

# 3. SQLite
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=sqlite --path=/tmp/jmt.db"

# 4. PostgreSQL
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.RdbmsJmtLoadTester --records=100000 --batch=1000 --db=postgresql --db-host=localhost --db-name=testdb --db-user=postgres --db-password=secret"

# 5. In-Memory (baseline)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --records=100000 --batch=1000 --memory"
```

## Performance Metrics

All tools report:

- **Throughput**: Operations per second (sustained)
- **Commit Latency**: Average time per batch commit
- **Proof Generation**: Average time to generate cryptographic proofs
- **Pruning Performance**: Time and entries pruned (if enabled)
- **Memory Usage**: JVM heap usage
- **Storage Statistics**: Database-specific metrics (RocksDB: pending compaction, memtable size; RDBMS: connection pool stats)

## Expected Performance (ADR-0015 Targets)

### JMT with RocksDB (Optimized)
- Throughput: 10-13k ops/s sustained (vs 2.6k baseline)
- Write amplification: ~15x (vs 50x baseline)
- Write stalls: <5% (vs 40-60% baseline)

### MPT with RocksDB
- Throughput: 8-10k ops/s sustained
- Proof generation: <10ms avg
- GC efficiency: 50-70% space reclaimed

### RDBMS Backends
- H2: 3-5k ops/s (fast but in-memory)
- SQLite: 2-4k ops/s (good for single-process)
- PostgreSQL: 4-6k ops/s (best for distributed deployments)

## Dependencies

This module includes **all** database drivers:

```gradle
dependencies {
    implementation libs.rocksdbjni          // RocksDB
    implementation libs.h2database          // H2
    implementation libs.sqlite.jdbc         // SQLite
    implementation libs.postgresql          // PostgreSQL
    implementation libs.hikaricp            // Connection pooling
    implementation libs.micrometer.core     // Metrics (optional)
}
```

No need to add dependencies manually - just run the tools!

## Why This Module?

Production modules (e.g., `jellyfish-merkle-rdbms`) keep dependencies minimal:
- Only `testImplementation` for H2/SQLite
- Only `implementation` for PostgreSQL (if needed)
- No RocksDB dependency

**This module** consolidates all testing tools with **all** backends, allowing:
- ✅ Easy execution without modifying `build.gradle`
- ✅ Cross-backend comparisons
- ✅ Standalone runnable JARs
- ✅ Comprehensive benchmarking suite

## Building and Running

```bash
# Build the module
./gradlew :verified-structures:load-tools:build

# Run a specific tool (use fully qualified class name)
./gradlew :verified-structures:load-tools:run --args="com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --help"

# Create a fat JAR for distribution
./gradlew :verified-structures:load-tools:shadowJar
java -jar verified-structures/load-tools/build/libs/load-tools-all.jar com.bloxbean.cardano.vds.tools.jmt.JmtLoadTester --help
```

## Notes

- JMT does not support delete operations (Diem-inspired design)
- MPT/MPF support delete operations with optional garbage collection
- RocksDB backends support advanced features: pruning, rollback indices, GC
- RDBMS backends support transactional batch commits
- All tools accept `--help` for detailed usage information
