# ADR-MPT: Merkle Patricia Trie 


This is the single, live design document for the MPT implementation. It consolidates earlier ADRs and reflects the current source. The code is the source of truth; this document summarizes how it works and how to run it in production.

## Summary

- Hexary Merkle Patricia Trie with two end-to-end schemes:
  - MPF mode: Aiken-compatible commitments and CBOR proofs (recommended with SecureTrie).
  - Classic mode: “node-encoding” commitments and CBOR proofs (off-chain re-hash simplicity; not Aiken-compatible).
- Two facades:
  - `MerklePatriciaTrie`: plain keys.
  - `SecureTrie`: hashed key-space; recommended for MPF interoperability.
- RocksDB-backed persistence (no full in-memory snapshots). GC strategies: mark-sweep and refcount.

## Node Model

- Branch: 16-way child table. Classic mode includes a branch terminal value in the branch commitment. In MPF mode, the branch value slot is ignored/empty by design.
- Leaf: terminal for a key. Value mixing depends on scheme.
- Extension: single-child path compression; commitments flatten extension prefixes where required by scheme parity.

## Commitment and Proofs (wire-first)

- MPF mode
  - Commitments: `MpfCommitmentScheme` (extension prefixes folded into parent hashing per MPF parity).
  - Proof wire: MPF CBOR (Aiken-compatible), no top-level mode tag.
  - API: `getProofWire(key)` and `verifyProofWire(expectedRoot, key, valueOrNull, including, wire)`.
- Classic mode
  - Commitments: `ClassicMptCommitmentScheme` (branch value terminal is included into branch commitment).
  - Proof wire: CBOR array of node ByteStrings (node-encoding). Verifier re-hashes nodes exactly as transmitted.

Notes
- MPF production encoder emits definite CBOR. Golden fixtures with indefinite encodings are exercised via a test-only fallback decoder.
- Mode pairing is enforced at API level; do not mix commitment and proof schemes.

## Storage & Streaming (RocksDB)

- MPT integrates with RocksDB via the `state-trees-rocksdb` module.
- Production paths stream mutations; no full-tree snapshot loads.
- GC strategies:
  - Mark-sweep: on-disk traversal, delete stale nodes.
  - Refcount: increment on new root reachability; decrement on pruned roots. Suitable for workflows that maintain a root retention window.
- Helper: `RocksDbMptSession` provides one-shot batch and WriteOptions configuration similar to JMT ergonomics while keeping MPT API datastore-agnostic.

## Interoperability

- MPF proofs are Aiken-compatible (no leading tags, MPF CBOR shape).
- SecureTrie recommended with MPF to align key hashing with MPF ecosystem.

## Observability & Testing

- Proofs are wire-first, with inclusion and non-inclusion (missing-branch / conflicting-leaf) supported and tested.
- Golden vectors (Aiken repo) are verified via production decoder (definite encodings) or a test-only fallback decoder for indefinite encodings.
- RocksDB integration tests exercise pruning/GC.

## Production Readiness

Recommended defaults
- SecureTrie + MPF mode for Aiken interop.
- RocksDB WAL enabled; synchronous commits.
- Periodic mark-sweep pruning or refcount with a retention window.
- Avoid holding large in-memory snapshots; keep operations streaming.

Known limits
- Production MPF decoder targets definite CBOR encodings. Test-only fallback handles indefinite encodings for golden fixtures.

## Future Work (Optional)

- Broader negative testing with malformed wires.
- Extended branch/extension edge-case coverage in Classic mode.

