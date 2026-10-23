# RocksDB Core

Shared RocksDB building blocks for the verified-structures RocksDB backends: namespaces, tuning profiles,
initialisation and resource cleanup.

## When to use

This module comes in automatically with:

- `cardano-client-merkle-patricia-forestry-rocksdb`
- `cardano-client-jellyfish-merkle-rocksdb`

Use it directly only when you build your own RocksDB-backed structure, or when you pass a `NamespaceOptions` or
`RocksDbConfig` to one of the backends.

```gradle
dependencies {
    implementation 'com.bloxbean.cardano:cardano-client-rocksdb-core:0.8.0'
    // org.rocksdb:rocksdbjni is an api dependency and comes transitively
}
```

## Namespaces

A RocksDB database can hold several trees. `NamespaceOptions` has two independent dimensions:

| Dimension | Effect | Factory |
|---|---|---|
| Column-family prefix | The tree gets its own column families (e.g. `account_nodes` instead of `nodes`) | `NamespaceOptions.columnFamily("account")` |
| Key prefix (1 byte) | Every key in the shared column families starts with this byte (default `0x00`, `0xFF` is reserved) | `NamespaceOptions.keyPrefix((byte) 0x01)` |

```java
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;

NamespaceOptions defaults = NamespaceOptions.defaults();                         // default CFs, key prefix 0x00
NamespaceOptions accounts = NamespaceOptions.columnFamily("account");            // own CFs
NamespaceOptions shard1   = NamespaceOptions.keyPrefix((byte) 0x01);             // shared CFs, prefix 0x01
NamespaceOptions both     = NamespaceOptions.both("account", (byte) 0x02);

// e.g. MPF
RocksDbStateTrees trees = new RocksDbStateTrees("data/mpf", shard1);
```

`KeyPrefixer` applies the key prefix:

```java
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;

KeyPrefixer prefixer = new KeyPrefixer((byte) 0x01);
byte[] stored = prefixer.prefix(nodeHash);         // 0x01 || nodeHash
byte[] hash   = prefixer.unprefix(stored);         // nodeHash
boolean mine  = prefixer.hasCorrectPrefix(stored); // true
```

### Scanning one namespace

Seek to the namespace prefix with a total-order iterator and stop at the first key with another prefix. Do not
combine `seekToFirst()` with `ReadOptions.setPrefixSameAsStart(true)` (`createPrefixReadOptions()`): the iterator then
binds to the *lowest* prefix group in the column family, which is another namespace's data whenever several
key-prefix namespaces share it.

```java
try (ReadOptions ro = new ReadOptions().setTotalOrderSeek(true);
     RocksIterator it = db.newIterator(cf, ro)) {
    for (it.seek(prefixer.prefix(new byte[0])); it.isValid(); it.next()) {
        if (!prefixer.hasCorrectPrefix(it.key())) break;
        byte[] key = prefixer.unprefix(it.key());
        // ...
    }
}
```

`createPrefixReadOptions()` is fine for point-like lookups that `seek` a fully prefixed key, on column families
opened with `useFixedLengthPrefixExtractor(1)`.

## Tuning profiles: `RocksDbConfig`

```java
import com.bloxbean.cardano.vds.rocksdb.RocksDbConfig;

RocksDbConfig config = RocksDbConfig.balanced();          // also highThroughput(), lowMemory(), defaults()
RocksDbConfig custom = RocksDbConfig.builder()
        .profile(RocksDbConfig.Profile.HIGH_THROUGHPUT)
        .blockCacheSize(512L * 1024 * 1024)
        .bloomFilterBits(10)
        .build();

// e.g. MPF
RocksDbStateTrees trees = new RocksDbStateTrees("data/mpf", NamespaceOptions.defaults(),
        StorageMode.MULTI_VERSION, custom);
```

Custom code can call `applyToDbOptions(DBOptions)`, `applyToCfOptions(ColumnFamilyOptions, Cache)` and
`createBlockCache()` directly.

## Initialisation and cleanup

`RocksDbInitializer` opens a database and creates missing column families:

```java
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbInitializer;

try (RocksDbInitializer.Result result = RocksDbInitializer.builder("/path/to/db")
        .withRequiredColumnFamily("nodes")
        .withRequiredColumnFamily("roots")
        .withDbOptionsModifier(opts -> opts.setMaxBackgroundJobs(4))
        .initialize()) {
    RocksDB db = result.getDatabase();
    ColumnFamilyHandle nodes = result.getColumnFamily("nodes");
    // ...
}
```

`RocksDbResources` closes registered native resources in reverse order and aggregates failures:

```java
import com.bloxbean.cardano.vds.rocksdb.resources.RocksDbResources;

try (RocksDbResources resources = new RocksDbResources()) {
    DBOptions dbOptions = resources.register("dbOptions", new DBOptions().setCreateIfMissing(true));
    RocksDB db = resources.register("db", RocksDB.open(dbOptions, path, descriptors, handles));
    // ...
}
```

## Exceptions

`com.bloxbean.cardano.vds.rocksdb.exceptions` holds a checked hierarchy rooted at `RocksDbStorageException`:
`RocksDbOperationException`, `RocksDbConfigurationException` and `RocksDbBatchException`, each with a
`getDiagnosticInfo()`.

## Thread safety

- `KeyPrefixer`, `NamespaceOptions` and `RocksDbConfig` are immutable.
- `RocksDbResources` registration and close are thread-safe.
- RocksDB itself is thread-safe, but the tree classes built on it are not; see each backend's README.

## Related modules

- [merkle-patricia-forestry-rocksdb](../merkle-patricia-forestry-rocksdb/): MPF RocksDB backend
- [jellyfish-merkle-rocksdb](../jellyfish-merkle-rocksdb/): JMT RocksDB backend
- [rdbms-core](../rdbms-core/): SQL alternative
