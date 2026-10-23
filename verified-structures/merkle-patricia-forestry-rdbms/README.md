# Merkle Patricia Forestry - RDBMS Backend

SQL storage for [Merkle Patricia Forestry](../merkle-patricia-forestry/) tries, on PostgreSQL, H2 or SQLite.
The trie logic, hashing, roots and proofs are exactly the same as with the RocksDB backend; only where the
nodes are stored changes.

> **RocksDB is the recommended backend for now.** Use the RDBMS backend when you need SQL access to the data or
> already run PostgreSQL. The RDBMS backend has no refcount GC (see [Garbage collection](#garbage-collection)).

## Gradle dependency

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rdbms:0.8.0'

    // Plus the JDBC driver for your database (the PostgreSQL driver is already included).
    // Any recent version works; CCL is tested with these:
    implementation 'com.h2database:h2:2.4.240'          // or
    implementation 'org.xerial:sqlite-jdbc:3.51.2.0'
}
```

## Quick start

The stores never create tables on their own. Create them once with `RdbmsMpfSchema.createIfNotExists`
(idempotent), or apply the bundled DDL with your own migration tool (see [Schema](#schema)).

```java
import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rdbms.RdbmsMpfSchema;
import com.bloxbean.cardano.vds.mpf.rdbms.RdbmsStateTrees;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;

// Pooled (HikariCP) data source; the dialect is detected from the JDBC URL
try (DbConfig config = DbConfig.builder()
        .jdbcUrl("jdbc:postgresql://localhost:5432/mpf", "user", "password")
        .build()) {

    RdbmsMpfSchema.createIfNotExists(config);

    try (RdbmsStateTrees trees = new RdbmsStateTrees(config, StorageMode.MULTI_VERSION)) {
        MpfTrie trie = new MpfTrie(trees.nodeStore());
        trie.put("alice".getBytes(), "100".getBytes());

        long version = trees.putRootWithRefcount(trie.getRootHash()); // records the root, no refcounting

        // Later: reopen that version
        MpfTrie atVersion = new MpfTrie(trees.nodeStore(), trees.rootsIndex().get(version));
        byte[] value = atVersion.get("alice".getBytes());
    }
}
```

Other ways to build a `DbConfig`:

```java
// H2 in memory, non-pooled data source (tests)
DbConfig h2 = DbConfig.builder().simpleJdbcUrl("jdbc:h2:mem:mpf;DB_CLOSE_DELAY=-1").build();

// SQLite file
DbConfig sqlite = DbConfig.builder().simpleJdbcUrl("jdbc:sqlite:data/mpf.db").build();

// Your own DataSource, explicit dialect, and a table prefix
DbConfig custom = DbConfig.builder()
        .dataSource(myDataSource)
        .dialect(new PostgresDialect())
        .tablePrefix("accounts")          // tables accounts_mpt_nodes, accounts_mpt_roots, accounts_mpt_latest
        .build();
```

`DbConfig.close()` shuts down the HikariCP pool it created through `jdbcUrl(...)`. The `simpleJdbcUrl(...)` data
source is unpooled (one JDBC connection per call) and holds nothing to close. A data source you pass with
`dataSource(...)` is never closed by `DbConfig`.

## API

| Class | Purpose |
|---|---|
| `RdbmsNodeStore(DbConfig)` / `RdbmsNodeStore(DbConfig, byte keyPrefix)` | `NodeStore` for `MpfTrie`. `withTransaction(callback)` runs several writes in one JDBC transaction; `putBatch(map)` uses JDBC batching. |
| `RdbmsRootsIndex(DbConfig)` / `RdbmsRootsIndex(DbConfig, byte keyPrefix)` | Version → root index: `put`, `get`, `latest`, `lastVersion`, `nextVersion`, `listAll`, `listRange`. |
| `RdbmsStateTrees(DbConfig)` / `(DbConfig, StorageMode)` / `(DbConfig, StorageMode, byte keyPrefix)` | Node store + roots index for one namespace. |
| `RdbmsMpfSchema.createIfNotExists(DbConfig)` | Creates the tables and indexes for the configured dialect and table prefix. |

Namespaces: the `keyPrefix` byte is stored in the `namespace` column, so several tries can share the same tables.
A table prefix (`DbConfig.tablePrefix`) gives a trie its own tables instead.

### Storage modes

| | RDBMS | RocksDB |
|---|---|---|
| Default mode | `SINGLE_VERSION` | `MULTI_VERSION` |
| Mode persisted and checked on open | No | Yes |

Pass the mode explicitly if you switch backends. Opening the same namespace with a different mode is not detected.

- `SINGLE_VERSION`: `putRootSnapshot(root)` overwrites the root at version 0, `getCurrentRoot()` reads it, and
  `cleanupOrphanedNodes(new RdbmsGcOptions())` mark-sweeps everything not reachable from it.
- `MULTI_VERSION`: `putRootWithRefcount(root)` stores the root under the next version. Despite the name, no
  refcounts are kept in the RDBMS backend.

### Garbage collection

The RDBMS backend only has mark-sweep in `SINGLE_VERSION` mode (`cleanupOrphanedNodes`). In `MULTI_VERSION` mode
nothing is ever deleted. Do not run GC while another writer updates the same namespace.

## Schema

Bundled DDL (only `CREATE ... IF NOT EXISTS`):

- `/ddl/mpf/postgres/schema.sql`
- `/ddl/mpf/h2/schema.sql`
- `/ddl/mpf/sqlite/schema.sql`

```sql
-- PostgreSQL version; types differ per dialect
CREATE TABLE IF NOT EXISTS mpt_nodes (
    namespace  SMALLINT NOT NULL,
    node_hash  BYTEA    NOT NULL,
    node_data  BYTEA    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, node_hash)
);
CREATE INDEX IF NOT EXISTS idx_mpt_nodes_ns ON mpt_nodes(namespace);

CREATE TABLE IF NOT EXISTS mpt_roots (
    namespace  SMALLINT NOT NULL,
    version    BIGINT   NOT NULL,
    root_hash  BYTEA    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, version)
);
CREATE INDEX IF NOT EXISTS idx_mpt_roots_ns ON mpt_roots(namespace);

CREATE TABLE IF NOT EXISTS mpt_latest (
    namespace      SMALLINT NOT NULL PRIMARY KEY,
    latest_version BIGINT   NOT NULL,
    latest_root    BYTEA    NOT NULL,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

With a table prefix `p`, `RdbmsMpfSchema` creates `p_mpt_nodes`, `p_mpt_roots`, `p_mpt_latest`, `p_idx_mpt_nodes_ns`
and `p_idx_mpt_roots_ns`. If you apply the DDL yourself with a prefix, rename the tables and indexes the same way.
`RdbmsMpfSchema.statements(dialect, prefix)` returns the exact statements. A prefix may have at most 46 characters,
so that every generated name fits PostgreSQL's 63-character identifier limit (enforced for all dialects).

With auto-commit off, `createIfNotExists` runs all statements in one transaction and rolls back on failure. With
auto-commit on (the default) each statement commits on its own; after a failure, call it again.

Node keys are commitments (32-byte hashes) and `node_data` is the CBOR-encoded node, byte-identical to what the
RocksDB backend stores.

## Thread safety

- `RdbmsNodeStore` and `RdbmsRootsIndex` can be called from several threads (one connection per call, or one per
  thread inside `withTransaction`).
- `MpfTrie` is **not** thread-safe, and the backend has no locking across a trie update. Use one writer per
  namespace, or synchronise externally.

## Related modules

- [merkle-patricia-forestry](../merkle-patricia-forestry/): trie, proofs and Aiken usage
- [merkle-patricia-forestry-rocksdb](../merkle-patricia-forestry-rocksdb/): RocksDB backend (recommended)
- [rdbms-core](../rdbms-core/): `DbConfig`, dialects, table-name resolution
- [RDBMS design notes](../docs/design-rdbms.md)
