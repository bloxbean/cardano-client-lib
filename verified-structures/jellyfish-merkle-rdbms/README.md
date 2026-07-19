# Jellyfish-Merkle RDBMS Backend (Experimental)

Transactional persistence for the repository's custom, versioned radix-16 JMT implementation.
PostgreSQL, H2, and SQLite dialects and DDL scripts are included.

> The key-binding security fix changes every non-empty root. Rebuild old trees into new tables or a
> new table prefix. Read the
> [JMT security/performance/Cardano audit](../jellyfish-merkle/docs/security-performance-audit.md).

## Schema setup

The store does not create or migrate tables. Apply the matching resource before opening it:

```text
/ddl/jmt/postgres/schema.sql
/ddl/jmt/h2/schema.sql
/ddl/jmt/sqlite/schema.sql
```

Use a migration tool for production. If `DbConfig.tablePrefix(...)` is configured, provision tables
with that same validated prefix.

## Usage

```java
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.rdbms.RdbmsJmtStore;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;

import java.util.LinkedHashMap;
import java.util.Map;

try (DbConfig config = DbConfig.builder()
        .jdbcUrl("jdbc:postgresql://localhost:5432/jmt", "jmt_user", "secret")
        .build()) {
    // Apply /ddl/jmt/postgres/schema.sql before constructing/using the store.
    try (RdbmsJmtStore store = new RdbmsJmtStore(config)) {
        HashFunction hashFn = Blake2b256::digest;
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put("alice".getBytes(), "balance:100".getBytes());
        byte[] root = tree.put(1L, updates).rootHash();

        byte[] wire = tree.getProofWire("alice".getBytes(), 1L).orElseThrow();
        boolean valid = tree.verifyProofWire(
                root, "alice".getBytes(), "balance:100".getBytes(), true, wire);
    }
}
```

`DbConfig` closes a pool/data source that it created with `jdbcUrl` or `simpleJdbcUrl`. A data source
passed with `dataSource(...)` remains caller-owned. `RdbmsJmtStore.close()` does not close it.

## Namespaces and table prefixes

- The optional `byte keyPrefix` constructor argument selects the numeric namespace column.
- `DbConfig.tablePrefix("account")` resolves tables such as `account_jmt_nodes`.
- Use separate namespaces or table prefixes for independent trees and for rebuilding an old
  commitment format.

## Lifecycle operations

```java
int removed = store.pruneUpTo(1_000L);
store.truncateAfter(900L);
```

Both are transactional. Safe value pruning keeps the latest value at/below the boundary for reads
at newer retained versions; older historical value reads are intentionally discarded. Rollback
deletes future nodes, values, roots, and stale markers, then repoints latest metadata to the
greatest surviving root.

## Concurrency

Prepared statements are parameterized and each backend operation obtains its own connection, but
tree writes are **single-writer**. Serialize `put`, prune, and rollback per namespace. For multiple
processes, add a database lock or compare-and-set protocol around the expected latest version; a
connection pool alone is not sufficient.

## Indexes and performance

The supplied schema indexes node path/version, value key/version, root version, and stale version.
Commits batch nodes, values, and stale markers in one transaction. Proof generation still performs
multiple point queries and is sensitive to network/database latency. Benchmark with the production
database, history size, durability, and batch distribution; this repository makes no fixed
throughput guarantee.

## Gradle

```gradle
implementation "com.bloxbean.cardano:jellyfish-merkle-rdbms:0.8.0"
runtimeOnly "org.postgresql:postgresql:<approved-version>"
```
