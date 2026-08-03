# Jellyfish-Merkle RocksDB Backend

RocksDB persistence for the repository's custom, versioned radix-16 JMT implementation.

> The first supported persistent format is `classic-radix16-blake2b256-v1`. Stores with missing or
> incompatible format/feature metadata fail closed. Read the
> [JMT security/performance/Cardano audit](../jellyfish-merkle/docs/security-performance-audit.md).

## Usage

```java
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;

import java.util.LinkedHashMap;
import java.util.Map;

RocksDbJmtStore.Options options = RocksDbJmtStore.Options.production();

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
- `disableWalForBatches(true)`: unsafe benchmarking option that permits torn state after a crash;
  it cannot be combined with sync commits and must not be used for durable chain state.
- `rocksDbConfig(...)`: selects/tunes the shared RocksDB configuration.

## Lifecycle operations

```java
// Remove stale nodes and old value history through version 1_000.
int removed = store.pruneUpTo(1_000L);

// Remove roots, nodes, values, and stale markers after version 900.
// Requires enableRollbackIndex(true).
store.truncateAfter(900L);
```

The shared coordinator serializes updates and excludes maintenance for each namespace. A competing
writer does not block: it throws `JmtConcurrentMutationException` before touching storage. Every
wrapper around an externally owned RocksDB handle must be given the same explicit coordinator;
the safe `attach(...)` API requires it.

Pruning removes roots below the retained horizon and persists a prune watermark. Rollback below
that watermark is rejected rather than exposing an incomplete historical tree.

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
