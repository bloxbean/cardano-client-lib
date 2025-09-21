# Sparse Merkle Tree (SMT)

A 256-bit Sparse Merkle Tree implementation aligned with the existing MPT patterns, featuring immutable nodes, deterministic CBOR encoding, and pluggable storage via `NodeStore`.

- Key hashing: 256-bit (via `HashFunction`, typically Blake2b-256)
- Structure: Fixed-depth binary tree (depth 256)
- Leaves: Store `keyHash` and raw `value` bytes
- Persistence: Any `NodeStore` (in-memory, RocksDB, etc.)
- Since: Public SMT API is available from `0.8.0`

## API Overview

- `com.bloxbean.cardano.statetrees.api.SparseMerkleTree` (facade)
  - `put(byte[] key, byte[] value)` — insert/update
  - `get(byte[] key)` — fetch value
  - `delete(byte[] key)` — remove
  - `getRootHash()` / `setRootHash(byte[])` — state management
  - `getProof(byte[] key)` — inclusion or non-inclusion (empty-path) proof
- `com.bloxbean.cardano.statetrees.api.SparseMerkleProof`
  - Type: `INCLUSION` or `NON_INCLUSION_EMPTY`, siblings[256], optional value
- `com.bloxbean.cardano.statetrees.api.SparseMerkleVerifier`
  - `verifyInclusion(root, hashFn, key, value, siblings)`
  - `verifyNonInclusion(root, hashFn, key, siblings)`

All new public SMT classes/methods are marked `@since 0.8.0`.

## Basic Usage

```java
import com.bloxbean.cardano.statetrees.api.*;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

NodeStore store = new com.bloxbean.cardano.statetrees.TestNodeStore(); // in-memory
HashFunction hashFn = Blake2b256::digest;

SparseMerkleTree smt = new SparseMerkleTree(store, hashFn);

smt.put("alice".getBytes(), "100".getBytes());
smt.put("bob".getBytes(),   "200".getBytes());

byte[] v = smt.get("alice".getBytes()); // -> "100"
byte[] root = smt.getRootHash();        // cryptographic state root

smt.delete("alice".getBytes());
```

## Inclusion Proofs

```java
import com.bloxbean.cardano.statetrees.api.*;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

NodeStore store = new com.bloxbean.cardano.statetrees.TestNodeStore();
HashFunction hashFn = Blake2b256::digest;
SparseMerkleTree smt = new SparseMerkleTree(store, hashFn);

byte[] key = "alice".getBytes();
byte[] val = "100".getBytes();
smt.put(key, val);

SparseMerkleProof proof = smt.getProof(key);
boolean ok = SparseMerkleVerifier.verifyInclusion(
    smt.getRootHash(), hashFn, key, val, proof.getSiblings());
```

## Non-Inclusion Proofs (Empty Path)

```java
byte[] unknownKey = "absent".getBytes();
SparseMerkleProof nonInclusion = smt.getProof(unknownKey);
boolean ok = SparseMerkleVerifier.verifyNonInclusion(
    smt.getRootHash(), hashFn, unknownKey, nonInclusion.getSiblings());
```

## RocksDB Example (Atomic Batch + Versioned Roots)

```java
import com.bloxbean.cardano.statetrees.api.*;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.rocksdb.*;
import com.bloxbean.cardano.statetrees.rocksdb.resources.RocksDbInitializer;
import org.rocksdb.*;

HashFunction hashFn = Blake2b256::digest;

try (RocksDbInitializer.Result result = RocksDbInitializer.builder("/path/to/db")
    .withRequiredColumnFamily(RocksDbNodeStore.CF_NODES)
    .withRequiredColumnFamily(RocksDbRootsIndex.CF_ROOTS)
    .initialize()) {

  RocksDB db = result.getDatabase();
  ColumnFamilyHandle cfNodes = result.getColumnFamily(RocksDbNodeStore.CF_NODES);
  ColumnFamilyHandle cfRoots = result.getColumnFamily(RocksDbRootsIndex.CF_ROOTS);

  RocksDbNodeStore nodeStore = new RocksDbNodeStore(db, cfNodes);
  RocksDbRootsIndex rootsIndex = new RocksDbRootsIndex(db, cfRoots);
  SparseMerkleTree smt = new SparseMerkleTree(nodeStore, hashFn);

  try (WriteBatch batch = new WriteBatch(); WriteOptions wopts = new WriteOptions()) {
    nodeStore.withBatch(batch, () ->
      rootsIndex.withBatch(batch, () -> {
        smt.put("alice".getBytes(), "100".getBytes());
        smt.put("bob".getBytes(),   "200".getBytes());
        long ver = rootsIndex.nextVersion();
        rootsIndex.put(ver, smt.getRootHash());
        return null;
      })
    );
    db.write(wopts, batch); // atomic commit
  }

  byte[] latest = rootsIndex.latest();
  SparseMerkleTree reloaded = new SparseMerkleTree(nodeStore, hashFn, latest);
}
```

## Notes

- Deterministic CBOR encoding is used for node hashing, consistent with MPT style.
- Empty subtree commitments are precomputed for all depths, enabling efficient non-inclusion proofs.
- The SMT API is not thread-safe; use external synchronization in concurrent contexts.

