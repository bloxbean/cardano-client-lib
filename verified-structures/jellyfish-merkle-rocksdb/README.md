# Jellyfish-Merkle RocksDB Backend (Experimental)

RocksDB persistence for the repository's custom, versioned radix-16 JMT implementation.

> The key-binding security fix changes every non-empty root. Rebuild old trees into a new database
> or column-family namespace; do not continue an existing pre-fix tree. Read the
> [JMT security/performance/Cardano audit](../jellyfish-merkle/docs/security-performance-audit.md).

## Usage

```java
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;

import java.util.LinkedHashMap;
import java.util.Map;

RocksDbJmtStore.Options options = RocksDbJmtStore.Options.builder()
        // Required if the application must call truncateAfter(), such as a Cardano chain follower.
        // Enable this when the database is first created; it does not backfill old commits.
        .enableRollbackIndex(true)
        .build();

try (RocksDbJmtStore store = RocksDbJmtStore.open("data/jmt", options)) {
    HashFunction hashFn = Blake2b256::digest;
    JellyfishMerkleTree tree = new JellyfishMerkleTree(store, hashFn);

    Map<byte[], byte[]> updates = new LinkedHashMap<>();
    updates.put("alice".getBytes(), "balance:100".getBytes());
    byte[] root = tree.put(1L, updates).rootHash();

    byte[] wire = tree.getProofWire("alice".getBytes(), 1L).orElseThrow();
    boolean valid = tree.verifyProofWire(
            root, "alice".getBytes(), "balance:100".getBytes(), true, wire);
}
```

The string-path constructors and `open` factories own the RocksDB instance and close its column
families/resources from `close()`. `attach(...)` binds to a caller-owned `RocksDB` instance; the
caller remains responsible for the database and for any supplied column-family handles.

## Options

- `namespace(String)`: prefixes the JMT column-family names. The `NamespaceOptions` constructor
  currently uses only its column-family prefix; custom key-prefix support is not implemented.
- `enableRollbackIndex(boolean)`: creates and maintains node/value indexes by version. Default is
  `false`; `truncateAfter` throws when disabled.
- `prunePolicy(SAFE|AGGRESSIVE)`: safe mode keeps the latest value sentinel at/below the prune
  boundary; aggressive mode removes all such value history.
- `syncOnCommit`, `syncOnPrune`, `syncOnTruncate`: default to `true` for durability.
- `disableWalForBatches(true)`: unsafe benchmarking option; do not use for durable chain state.
- `rocksDbConfig(...)`: selects/tunes the shared RocksDB configuration.

## Lifecycle operations

```java
// Remove stale nodes and old value history through version 1_000.
int removed = store.pruneUpTo(1_000L);

// Remove roots, nodes, values, and stale markers after version 900.
// Requires enableRollbackIndex(true).
store.truncateAfter(900L);
```

Serialize `put`, `pruneUpTo`, and `truncateAfter` for each namespace. RocksDB's thread-safe API does
not make the tree's read/compute/commit protocol safe for concurrent writers.

## Storage layout

- `nodes_jmt`: `namespace || NodeKey -> encoded node`
- `values_jmt`: `namespace || keyHash || version -> value/tombstone`
- `roots_jmt`: per-version roots plus latest-root metadata
- `stale_jmt`: `staleSince || NodeKey`
- `nodes_by_ver_jmt`, `values_by_ver_jmt`: optional rollback indexes

Values use 32-byte key hashes. Commits use one RocksDB `WriteBatch` for nodes, values, stale
markers, roots, latest metadata, and enabled rollback indexes.

## Gradle

```gradle
implementation "com.bloxbean.cardano:jellyfish-merkle-rocksdb:0.8.0"
```
