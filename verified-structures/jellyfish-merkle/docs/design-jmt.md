# Jellyfish-Merkle Module Design

**Status:** experimental

**Hash width:** 256 bits

**Branch radix:** 16

**Compatibility:** custom commitments and proofs; not Diem/Aptos compatible

## Purpose

The module is an authenticated key/value tree for versioned off-chain state. It borrows JMT's
copy-on-write storage model—versioned node keys, immutable historical nodes, and stale-node indexes—
but uses a custom radix-16 node commitment and CBOR proof representation.

Use the [security, performance, and Cardano audit](security-performance-audit.md) before deploying
it. In particular, writes are single-writer, deletion is not implemented, and databases created
with the former suffix-only leaf commitment must be rebuilt.

## Components

```text
JellyfishMerkleTree
  ├── TreeCache                 batch-local copy-on-write state
  ├── CommitmentScheme         leaf and radix-16 branch commitments
  ├── JmtProof / verifier      object proof generation and verification
  ├── JmtProofCodec            CBOR wire proof generation and verification
  └── JmtStore
        ├── InMemoryJmtStore
        ├── RocksDbJmtStore
        └── RdbmsJmtStore
```

`JellyfishMerkleTree.put(version, updates)` hashes original keys and values, applies each update to
one `TreeCache`, freezes the resulting nodes/root/stale indexes, and commits nodes, values, root,
and stale markers through one backend batch.

## Keys and paths

The configured hash function must return 32 bytes. A key becomes 64 nibbles and traversal consumes
one nibble at each branch.

Persisted node keys are:

```text
0x4e || unsigned-varint(path_nibble_length) || packed_path || version_be_u64
```

Public versions are non-negative Java `long` values. New versions must increase. Gaps are allowed;
the cache resolves the actual previous root node so stale tracking remains correct. Replaying an
already committed version is allowed only when it produces the same immutable root. Creating a
new historical version below the current latest version is rejected.

The length-prefixed byte encoding groups paths by length before content and therefore does not
have the same ordering as `NodeKey.compareTo` for mixed-depth paths. Exact-path floor-by-version
lookups are supported; do not use the generic floor/ceiling SPI as a logical path index.

## Nodes

### Leaf

A leaf stores `keyHash` and `valueHash`. Its commitment is:

```text
H(0x00 || keyHash || valueHash)
```

Binding the full key is required for inclusion and different-leaf non-inclusion soundness.

### Internal

An internal node stores a 16-bit bitmap and the hashes of present children in nibble order. Its
commitment expands absent children to the 32-byte zero placeholder:

```text
H(0x01 || bitmap_be16 || child_0 || ... || child_15)
```

The Blake2b-256 preimage is always 515 bytes. The bitmap distinguishes an absent child from a
present child whose commitment happens to equal the placeholder.

`JmtExtensionNode` exists in the generic node codec, but the tree update algorithm does not create
extension nodes. It should not be treated as a supported persisted tree shape without additional
tests and a commitment-scheme API for it.

## Copy-on-write updates

For each node changed at version `v`, the cache:

1. resolves the node visible immediately before `v`;
2. marks that exact node key stale (or removes it if it was only staged in the current batch);
3. creates the replacement at the same path and version `v`; and
4. propagates the new child hash to the root.

Multiple key updates in one call share staged nodes. A later update sees earlier updates in the same
batch. The final backend commit is atomic, but the complete read/compute/commit protocol is not;
commit, prune, and rollback must be serialized outside the tree, including across processes.

Individual key deletion is not implemented. Store-level tombstone methods are reserved plumbing,
not a supported tree operation.

## Values and historical reads

Raw values are stored separately under `(keyHash, version)`. `get(key)` and `get(key, version)` use
that value index without traversing authenticated nodes. They are appropriate only when the store
is trusted. A caller that needs cryptographic assurance must obtain a proof and verify it against an
independently authenticated root.

Safe value pruning retains the newest value at or below the prune boundary as a sentinel, so reads
at/after the retained boundary remain possible. Older historical reads are intentionally lost.

## Proofs

An object proof contains root-to-terminal branch steps, with a 16-slot child-hash matrix at each
step, and one of:

- inclusion: queried leaf and raw value;
- non-inclusion at an empty child; or
- non-inclusion at a different leaf sharing the traversed prefix.

Verification never trusts the proof's claimed queried key/suffix. It derives `keyHash` from the
caller's key, derives `valueHash` from the caller's value for inclusion, reconstructs the leaf
commitment, then replaces the queried child while ascending the supplied branches.

The default wire proof is one outer CBOR array of byte strings, where each byte string contains a
CBOR-encoded internal/terminal node. It is not PlutusData and is not Diem/Aptos serialization. The
verifier rejects trailing CBOR items, paths deeper than the 64-nibble key, inconsistent claim types,
invalid digest sizes, malformed nodes, and inputs larger than 1 MiB.

## Backend behavior

### In memory

Reference/testing backend. Commits and lifecycle operations are synchronized, abandoned batches
discard writes, divergent replay is rejected, and rollback rebuilds the latest-value view.

### RocksDB

Uses column families for nodes, values, roots, stale markers, and optional node/value rollback
indexes. One synced `WriteBatch` is used for a commit by default. Cardano chain followers must
enable rollback indexes when the database is created. Prune/truncate scans use total-order seeks
bounded to the active namespace.

### RDBMS

Uses one transaction per commit/prune/truncate and batched prepared statements. DDL is shipped for
PostgreSQL, H2, and SQLite but schema application is the caller's responsibility. `DbConfig` closes
only a data source that it created; externally supplied data sources remain caller-owned.

## Cardano positioning

Blake2b-256 roots are suitable for off-chain checkpoints or authenticated datums. There is no
Aiken/Plutus verifier, transition validator, PlutusData codec, or cross-language golden vector for
this JMT. For current on-chain membership and update workflows, use the MPF module and its Aiken
package. Any future JMT validator needs a separately versioned commitment/codec and measured
transaction-size and execution-unit costs.
