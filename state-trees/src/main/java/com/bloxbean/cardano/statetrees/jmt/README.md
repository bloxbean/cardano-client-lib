# Jellyfish Merkle Tree (JMT)

This package hosts the ADR-0004 implementation of the Jellyfish Merkle Tree used by
`cardano-client-lib`. It provides an immutable, versioned hexary Merkle structure with
the default commitment and proof semantics from the Diem reference implementation, and
exposes both in-memory and store-backed facades.

## Wire Proof APIs

JMT exposes wire-first proof helpers aligned with ADR-0008. Proofs are encoded as CBOR
arrays of node payloads; verification mirrors the reference implementation.

```
HashFunction hash = Blake2b256::digest;

// In-memory facade
JellyfishMerkleTree tree = new JellyfishMerkleTree(hash);
Optional<byte[]> wire = tree.getProofWire("alice".getBytes(UTF_8), 1L);
boolean ok = tree.verifyProofWire(tree.rootHash(1L), "alice".getBytes(UTF_8),
                                  "100".getBytes(UTF_8), true, wire.orElseThrow());

// Store-backed facade
JellyfishMerkleTreeStore storeTree = new JellyfishMerkleTreeStore(
        backendStore,
        null,
        hash,
        JellyfishMerkleTreeStore.EngineMode.STREAMING,
        JellyfishMerkleTreeStoreConfig.defaults());
Optional<byte[]> w = storeTree.getProofWire("alice".getBytes(UTF_8), 1L);
boolean ok2 = storeTree.verifyProofWire(storeTree.rootHash(1L), "alice".getBytes(UTF_8),
                                       "100".getBytes(UTF_8), true, w.orElseThrow());
```

## Core Features

- **Store-backed streaming façade** `JellyfishMerkleTreeStore` streams commits directly into a
  `JmtStore` implementation (e.g. RocksDB) without rebuilding full snapshots in memory. This is the
  default engine mode; the in-memory reference engine remains available for parity testing. It exposes
  configuration knobs for adaptive node/value caching and for limiting commit result verbosity.
- **Versioned tree state** The reference `JellyfishMerkleTree` rebuilds a copy-on-write tree for every
  commit. It remains useful for deterministic testing and parity checks.
- **Classic commitments** The `commitment` package contains the `ClassicJmtCommitmentScheme`
  mirroring the hashing rules from the Diem implementation.
- **Proofs** `JmtProof` captures branch steps, optional fork neighbors, and alternative leaf data.
  `JmtProofVerifier` and `JellyfishMerkleTree#getProof` cover inclusion/non-inclusion cases, and the
  built-in codec emits the classic CBOR node-list format.
- **Adaptive caching and pruning** The streaming façade maintains optional LRU caches for nodes and
  values, plus a bounded negative-lookup cache that prevents repeated trips to the store for paths
  known to be absent. A high-level `prune(version)` API triggers stale-node deletion in the backing
  store and evicts any cached entries that refer to pruned versions.
- **Version-aware values** RocksDB-backed stores retain value history under composite keys
  `(keyHash || version)` so `get(key, t)` returns the last write ≤ `t`. Tombstones record deletions,
  matching MVCC semantics and keeping proofs correct for historical snapshots.

## RocksDB Store Configuration

`RocksDbJmtStore` can either open its own database directory or attach to an existing `RocksDB`
instance. Use the `Options` builder to tune pruning behaviour, namespacing, and rollback support:

```java
RocksDbJmtStore.Options storeOptions = RocksDbJmtStore.Options.builder()
        .namespace("app1")                 // optional column-family prefix
        .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.SAFE)
        .enableRollbackIndex(true)          // enable truncateAfter and rollback indices
        .build();

try(
RocksDbJmtStore store = RocksDbJmtStore.open("/var/jmt-db", storeOptions)){
        // use store with JellyfishMerkleTreeStore
        }

// For shared RocksDB instances:
RocksDbJmtStore shared = RocksDbJmtStore.attach(existingDb, storeOptions, existingHandles);
```

The default prune policy (`SAFE`) keeps the newest value ≤ prune target for every key; historical
queries at retained versions continue to work. `AGGRESSIVE` removes all value payloads at or below the
cut-off to minimise disk usage. Tombstones are preserved so reads at versions where the key was deleted
return empty until it is reinserted.

### Durability vs throughput

For benchmarking, you can disable WAL on commit batches:

```java
RocksDbJmtStore.Options fastIngest = RocksDbJmtStore.Options.builder()
        .disableWalForBatches(true)   // UNSAFE: do not use in production
        .syncOnCommit(false)          // skip fsync for commit batches
        .build();
```

In production, prefer the safer defaults (`syncOnCommit=true`, WAL enabled). The load tester supports
`--no-wal` and `--sync-commit=BOOL` flags to experiment with these trade-offs.

## Rollback & Truncation

Enable rollback indices in the store options when you need to truncate history (for example, to mirror
blockchain rollbacks). With `enableRollbackIndex(true)` the store maintains per-version indexes so
`JellyfishMerkleTreeStore.truncateAfter(version)` can drop nodes, values, stale markers, and roots above
the target snapshot:

```java
tree.truncateAfter(rollbackVersion); // drops versions > rollbackVersion and resets caches
```

Applications that do not need rollback semantics can leave the feature disabled to avoid the extra
index writes.

## Versioned Reads & Pruning Policies

- `get(key, version)` first checks that the requested version is ≤ the latest persisted root and then
  returns the most recent write at or before that version. Reads beyond a truncation point (for example
  after `truncateAfter`) return `null`.
- Delete operations record a tombstone at the commit version. Historical reads at the delete snapshot
  (and until the key is written again) return empty, mirroring Diem/Aptos JMT behaviour.
- `JellyfishMerkleTreeStore.prune(version)` propagates to the store and evicts cached entries that refer
  to pruned versions. With safe pruning the most recent retained value per key is preserved; aggressive
  pruning removes all payloads up to the cut-off so future historical reads may return empty even if the
  leaf node remains.

## Typical Usage

```java
HashFunction hash = Blake2b256::digest;
CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hash);

RocksDbJmtStore.Options storeOptions = RocksDbJmtStore.Options.builder()
        .prunePolicy(RocksDbJmtStore.ValuePrunePolicy.SAFE)
        .enableRollbackIndex(true)
        .build();
JmtStore backend = RocksDbJmtStore.open("/var/jmt-db", storeOptions);

JellyfishMerkleTreeStoreConfig config = JellyfishMerkleTreeStoreConfig.builder()
        .enableNodeCache(true).nodeCacheSize(8_192)
        .enableValueCache(true).valueCacheSize(8_192)
        .resultNodeLimit(0) // minimise per-commit memory footprint
        .build();

JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, commitments, hash,
        JellyfishMerkleTreeStore.EngineMode.STREAMING, config);

Map<byte[], byte[]> updates = new LinkedHashMap<>();
updates.

put("alice".getBytes(StandardCharsets.UTF_8), "100".

getBytes(StandardCharsets.UTF_8));
JellyfishMerkleTree.CommitResult commit = tree.commit(1L, updates);

Optional<byte[]> proofWire = tree.getProofWire("alice".getBytes(StandardCharsets.UTF_8), 1);
boolean ok = proofWire.map(cbor -> tree.verifyProofWire(
        commit.rootHash(),
        "alice".getBytes(StandardCharsets.UTF_8),
        "100".getBytes(StandardCharsets.UTF_8),
        true,
        cbor)).orElse(false);

JellyfishMerkleTreeStore.PruneReport report = tree.prune(10); // prune stale nodes up to version 10
System.out.

printf("Pruned %,d nodes (cache evictions: %,d)%n",report.nodesPruned(),report.

cacheEntriesEvicted());

        tree.

truncateAfter(42); // requires enableRollbackIndex(true)
```

## Where to integrate

- **State persistence** Use the classes in `state-trees-rocksdb/jmt` to store nodes, values, roots and
  stale metadata. The streaming façade already emits the correct writes through `JmtStore.CommitBatch`
  and exposes `prune(version)` for orchestrating stale clean-up.
- **Proof services** Expose `getProofWire` over RPC/APIs (classic CBOR node list). Consumers can use
  `JmtProofVerifier` to verify proofs without touching internal tree structures.
- **Offline tooling** `JmtProofVerifier` can be used wherever inclusion/non-inclusion checks are required.

## Advantages

- **MVCC-friendly** Each commit is versioned via ADR-0004 NodeKeys, enabling efficient reads for
  historical versions and controlled pruning through the stale index.
- **Hexary compression** The tree compresses single-child paths and avoids binary depth explosion,
  matching the Diem layout for efficient proof size and performance.
- **Interoperability** Commitments follow the Diem JMT specification, enabling reuse of its verifier
  implementations and tooling.
- **Persistence isolation** The RocksDB store keeps nodes, values, roots, and stale metadata in
  dedicated column families, making pruner operations straightforward while isolating hot data.
- **Adaptive memory footprint** Enable the node/value caches for hot-path acceleration or disable them
  entirely for the smallest possible heap usage. Commit result limits let you trade observability for
  lower allocation when ingesting very large batches.

## Load-testing Harness

`com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester` is a thin CLI that drives millions of random
updates through the streaming façade. Example:

```
./gradlew :state-trees-rocksdb:com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester.main \
  --args="--records=5_000_000 --batch=1000 --value-size=128 --rocksdb=/Volumes/data/jmt-load \
          --node-cache=4096 --value-cache=8192 --delete-ratio=0.2 --proof-every=1000"
```

Useful flags:

- `--node-cache` / `--value-cache` size the streaming caches (0 disables each cache).
- `--delete-ratio` randomly deletes a fraction of each batch while reusing live keys to stress tombstone handling.
- `--proof-every` periodically performs value+proof fetches to measure proof-generation overhead.
- `--version-log=path.csv` records version/root pairs for later analysis.
- `--memory` switches to the in-memory store, making it easy to compare heap pressure vs the RocksDB-backed mode.

It reports throughput, optional proof latency, and heap usage so you can size caches and prune policies before
shipping to production.

## Further Reading

- ADR-0004 (`state-trees/docs/adr/ADR-0004-jmt-mode.md`) for full design context.
- `state-trees-rocksdb/src/test/.../RocksDbJmtStoreTest` for persistence examples.
- `state-trees/src/test/java/com/bloxbean/cardano/statetrees/jmt/JmtClassicProofWireTest` for proof generation and verification patterns.
## Production Operations

- Single-writer policy: use one writer thread/process for commits; multiple readers are safe. This avoids lock contention and simplifies ordering guarantees (see concurrency tests).
- Durability vs throughput:
  - Safe defaults: WAL on, `syncOnCommit=true`, `syncOnPrune=true`, `syncOnTruncate=true`.
  - Benchmarking only: `disableWalForBatches(true)` and `syncOnCommit(false)` to maximise ingest. Risk of data loss on crash.
- Pruning strategy:
  - Periodic prune on a moving window (e.g., keep last 100k versions). Use the load tester’s `--prune-interval` and `--prune-to=window:W` to model policies.
  - Value pruning policy in RocksDbJmtStore: SAFE keeps the most recent ≤ target per key; AGGRESSIVE removes all payloads to minimise disk.
- RocksDB tuning guidelines (starting points):
  - Increase write buffers/memtables for sustained ingest; ensure enough background compaction threads.
  - Use LZ4 compression for a balance of speed and space in most environments.
  - Monitor compaction pressure using the load tester CSV: pending_compaction_mb, running_compactions, running_flushes, and memtable sizes.

### Metrics (Micrometer Adapter)

Integrate metrics via a no-dependency adapter that reflects into Micrometer at runtime:

```java
import com.bloxbean.cardano.statetrees.jmt.metrics.JmtMicrometerAdapter;
import com.bloxbean.cardano.statetrees.jmt.JmtMetrics;

Object registry = io.micrometer.core.instrument.Metrics.globalRegistry; // or your MeterRegistry
JmtMetrics metrics = JmtMicrometerAdapter.create(registry, "jmt");
JellyfishMerkleTreeStoreConfig cfg = JellyfishMerkleTreeStoreConfig.builder()
        .metrics(metrics)
        .enableNodeCache(true).nodeCacheSize(8192)
        .enableValueCache(true).valueCacheSize(8192)
        .build();
```

Emitted metric names (prefix configurable, default `jmt`):
- Counters: `jmt.node.cache.hit`, `jmt.node.cache.miss`, `jmt.value.cache.hit`, `jmt.value.cache.miss`,
  `jmt.commit.count`, `jmt.commit.puts`, `jmt.commit.deletes`, `jmt.prune.count`, `jmt.prune.nodes`, `jmt.prune.cacheEvicted`
- Timers: `jmt.commit.latency`, `jmt.prune.latency`

## Soak Recipes

Long-haul ingest and measurement using the RocksDB-backed tester.

- Time-bound single process (2h), mixed workload, rolling prune window, safe durability:

```
./gradlew :state-trees-rocksdb:com.bloxbean.cardano.statetrees.rocksdb.tools.JmtLoadTester.main \
  --args="--records=0 --duration=7200 --batch=1000 --value-size=128 --rocksdb=/var/jmt \
          --node-cache=4096 --value-cache=8192 --mix=60:30:10:0 --prune-interval=10000 \
          --prune-to=window:100000 --stats-csv=/tmp/jmt-stats.csv --stats-period=10 \
          --sync-commit=true"
```

- Segmented soak (6x 1h) with fresh JVMs each segment; see per-segment CSVs in `/tmp`:

```
state-trees-rocksdb/tools/jmt_soak.sh /var/jmt 6 3600 "--batch=1000 --value-size=128 --mix=60:30:10:0 \
  --prune-interval=10000 --prune-to=window:100000 --stats-period=10 --sync-commit=true"
```

Use the plotting helper to visualize ops/s, heap/db MB and compaction:

```
python3 state-trees-rocksdb/tools/jmt_stats_plot.py /tmp/jmt-stats.csv
```

## Safe vs Fast Defaults (Summary)

| Goal                   | WAL           | syncOnCommit | syncOnPrune/Truncate | Caches                 | Expected                           |
|------------------------|---------------|--------------|----------------------|------------------------|-------------------------------------|
| Safe (production)      | Enabled       | true         | true                 | Node+Value as needed   | Durable commits; steady throughput |
| Fast (benchmark only)  | Disabled      | false        | true/false           | Node+Value sized up    | Higher ingest; crash can lose data |

Notes:
- Disabling WAL and sync is unsafe; use only for stress testing on disposable data.
- Keep prune ops synced in production. For pure benchmarking, you may set syncOnPrune=false.
