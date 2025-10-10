# ADR-JMT: Jellyfish Merkle Tree

This is the single, live design document for the JMT implementation. It consolidates earlier ADRs and reflects the current source. The code is the source of truth; this summarizes how it works and how to run it in production.

## Summary

- Versioned hexary Jellyfish Merkle Tree with a store-backed streaming engine and an in-memory reference engine.
- End-to-end mode binding (commitments + proofs):
  - MPF mode (default): Aiken-compatible commitments and CBOR proofs.
  - Classic mode: node-encoding commitments and CBOR proofs (off-chain re-hash simplicity; not Aiken-compatible).
- Wire-first proof APIs; mode pairing enforced by API.

## Core Types & API

- `JellyfishMerkleTreeStore` (production façade)
  - `commit(version, updates)`: streams node/value changes directly to the backing store.
  - `get(key)` / `get(key, version)`: latest or snapshot reads.
  - `getProofWire(key, version)`, `verifyProofWire(expectedRoot, key, valueOrNull, including, wire)`.
  - `prune(versionInclusive)`: removes stale nodes/values and evicts caches.
  - `truncateAfter(version)`: drops state above a target (requires rollback indexes in store options).
- Engines
  - ReferenceEngine: in-memory, correctness parity.
  - StreamingEngine: store-backed, with LRU node cache, negative node cache, value cache.
- Mode binding
  - `JmtMode` and factories `JmtModes.mpf(hashFn)` / `JmtModes.classic(hashFn)` pair a commitment scheme with a proof codec.

## Proofs (wire-first)

- MPF
  - Commitments: `MpfCommitmentScheme` (extension handling per MPF parity).
  - Proof wire: MPF CBOR (Aiken-compatible; no top-level tag).
- Classic
  - Commitments: `ClassicJmtCommitmentScheme` (domain tags per node type; explicit internal/extension/leaf hashing rules).
  - Proof wire: CBOR array of node encodings; verifier re-hashes nodes as-is.

## Storage: `RocksDbJmtStore`

- Column families: nodes, values, roots, stale; optional nodesByVersion, valuesByVersion for rollback/truncation.
- Value history stored under composite keys `(keyHash || version)` with tombstones for deletes.
- Options
  - `disableWalForBatches(boolean)`: UNSAFE, benchmark only.
  - `syncOnCommit`, `syncOnPrune`, `syncOnTruncate`: fsync toggles (defaults true) for durability.
  - Optional namespaces for CF names.
- Roots API: `latestRoot()` and `rootHash(version)` from the roots CF.

## Observability

- `JmtMetrics` hook with NOOP default; the streaming engine records:
  - Node/value cache hits; commit/prune latencies; commit put/delete and stale counts.
- Optional Micrometer adapter: `JmtMicrometerAdapter` (reflection-based; no compile-time dependency).
- Load tester can emit periodic CSV stats including RocksDB properties (pending compaction bytes, num running compactions/flushes, memtable sizes).

## Load & Soak Testing

- `JmtLoadTester` (state-trees-rocksdb/tools):
  - Records- or duration-bound runs.
  - Mixed workload `--mix=put:update:delete:proof` with a bounded live-key pool (`--live-keys` cap).
  - Periodic pruning (`--prune-interval`, `--prune-to=V|window:W`).
  - Durability toggles (`--no-wal`, `--sync-commit=BOOL`).
  - Stats CSV (`--stats-csv`, `--stats-period=SEC`) + optional quick plotting script.

## Production Readiness

Recommended defaults
- Mode: MPF (Aiken interop) 
- Store: WAL enabled; `syncOnCommit=true`; `syncOnPrune/Truncate=true`.
- Enable rollback indexes if you need truncateAfter.
- Caches sized to available heap; use modest value cache.
- Prune on a moving window aligned to your retention policy.
- Single writer, multi reader. For higher ingest, shard state by writer instead of adding writers to one tree.

Ops guidance
- Monitor throughput (ops/s), pending compaction, memtable sizes via stats CSV.
- Scale write buffers and background compaction threads for sustained ingest.
- Keep WAL on in production. Use `--no-wal` only for disposable benchmarks.

## Future Work (Optional)

- Additional negative tests for malformed wires.
- Extended proof/path edge cases in Classic mode (long extension chains, terminal mismatches).

