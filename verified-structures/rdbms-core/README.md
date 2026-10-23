# RDBMS Core

Shared SQL building blocks for the verified-structures RDBMS backends: connection configuration, SQL dialects
(PostgreSQL, H2, SQLite) and table-name prefixes.

## When to use

This module comes in automatically with:

- `cardano-client-merkle-patricia-forestry-rdbms`
- `cardano-client-jellyfish-merkle-rdbms`

You use its `DbConfig` to open those backends.

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-rdbms-core:0.8.0'
}
```

## `DbConfig`

```java
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;

// HikariCP pool; the dialect is detected from the URL (jdbc:postgresql:, jdbc:h2:, jdbc:sqlite:)
DbConfig pg = DbConfig.builder()
        .jdbcUrl("jdbc:postgresql://localhost:5432/vds", "user", "password")
        .build();

// Non-pooled data source, for tests and small tools
DbConfig h2 = DbConfig.builder().simpleJdbcUrl("jdbc:h2:mem:vds;DB_CLOSE_DELAY=-1").build();
DbConfig sqlite = DbConfig.builder().simpleJdbcUrl("jdbc:sqlite:data/vds.db").build();

// Your own DataSource and an explicit dialect
DbConfig custom = DbConfig.builder()
        .dataSource(myDataSource)
        .dialect(new PostgresDialect())
        .tablePrefix("accounts")
        .build();
```

- `DbConfig` is `AutoCloseable`. It closes data sources it created (`jdbcUrl`, `simpleJdbcUrl`); a data source passed
  with `dataSource(...)` stays yours to close.
- A builder can be used once.
- `jdbcUrl(...)` uses a pool of at most 10 connections. Pass your own `DataSource` for other pool settings.

## Dialects

`SqlDialect` (`H2Dialect`, `PostgresDialect`, `SqliteDialect`) hides the SQL differences the stores need:
upserts (`upsertLatestSql`, `insertOrIgnoreSql`), column types (`binaryType`, `bigintType`, `smallintType`),
`currentTimestamp`, `supportsReturning` and the `KeyCodec` used to bind binary keys.

## Table prefixes

`TableNameResolver` turns a base name into the real table name: no prefix gives `mpt_nodes`, prefix `accounts`
gives `accounts_mpt_nodes`. A prefix must match `[a-zA-Z_][a-zA-Z0-9_]*`. Set it with `DbConfig.Builder.tablePrefix`.

## Schemas

The stores never create tables. Each backend bundles idempotent DDL per dialect:

- MPF: `RdbmsMpfSchema.createIfNotExists(dbConfig)` (honours the table prefix), or apply
  `/ddl/mpf/{postgres,h2,sqlite}/schema.sql` yourself.
- JMT: apply `/ddl/jmt/{postgres,h2,sqlite}/schema.sql`.

## Thread safety

`DbConfig`, the dialects, `TableNameResolver` and `StandardKeyCodec` are immutable. The pooled data source is
thread-safe.

## Related modules

- [merkle-patricia-forestry-rdbms](../merkle-patricia-forestry-rdbms/): MPF SQL backend
- [jellyfish-merkle-rdbms](../jellyfish-merkle-rdbms/): JMT SQL backend
- [rocksdb-core](../rocksdb-core/): RocksDB utilities (RocksDB is the recommended backend for now)
