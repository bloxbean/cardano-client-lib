# ADR-SMT: Sparse Merkle Tree 
Status: Prototype

This is the single, live design document for the SMT implementation. It reflects the current (basic) source and acts as the authoritative summary. The implementation is not yet production-ready.

## Summary

- Binary Sparse Merkle Tree (internal nodes with left/right child hashes; leaves hold key/valueHash).
- In-memory reference implementation; no store-backed streaming engine yet.
- Basic inclusion/non-inclusion proof shape exists; no wire-first CBOR interface finalized.

## Current Architecture

- Nodes
  - Internal: `[leftHash, rightHash]` with empty commitments substituted for missing children when hashing.
  - Leaf: key + valueHash.
- Hashing
  - Pluggable `HashFunction` abstraction (e.g., Blake2b-256) used across the codebase.
- Proofs
  - Inclusion: path witnesses along the binary path.
  - Non-inclusion: path witness ending at an absent or conflicting leaf.
  - Wire encoding: not yet standardized; tests exercise in-process proof objects.

## Gaps vs. Production

- No persistence abstraction comparable to `JmtStore` or MPT’s RocksDB store.
- No streaming engine; operations rebuild state in memory.
- No pruning/rollback orchestration.
- No metrics/observability hooks or soak tooling.
- No Aiken-interop or golden-vector alignment defined.

## Suggested Roadmap

1) Define a store contract (e.g., `SmtStore`) and a streaming façade (`SparseMerkleTreeStore`) modeled after JMT.
2) Implement `RocksDbSmtStore` with:
   - Nodes/values/roots/stale CFs; optional per-version indexes for rollback.
   - Options for WAL/Sync akin to JMT’s `RocksDbJmtStore.Options`.
3) Specify wire-first CBOR for inclusion and non-inclusion, plus a corresponding proof codec.
4) Add a small `SmtLoadTester` (duration-bound runs, mix, prune cadence) and CSV stats.
5) Integrate metrics via the `JmtMetrics`-style hook (or a generic `TreeMetrics`).

## Status

- The current SMT is useful as a correctness reference and for API exploration but should not be used in production flows.

