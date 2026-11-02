---
title: "Jellyfish Merkle Tree"
description: "Versioned, authenticated off-chain state with inclusion and absence proofs."
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

A Jellyfish Merkle Tree (JMT) keeps every version of your state, so you can read and prove a key as of any version. Each commit is one batch of updates with a version number. It's inspired by Diem's JMT, but its commitments and proofs aren't compatible with Diem or Aptos.

There's no on-chain verifier, and keys can't be deleted. If a validator needs to check proofs, [use MPF](/verified-structures/mpf/). The supported production scope is serialized off-chain state with one logical writer per namespace.

## Quick start

Add the [dependency](/verified-structures/overview/#dependencies), then:

```java
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;

try (RocksDbJmtStore store = new RocksDbJmtStore("data/jmt")) {
    JellyfishMerkleTree tree = new JellyfishMerkleTree(store);

    // Version 1
    Map<byte[], byte[]> updates = new HashMap<>();
    updates.put("alice".getBytes(), "balance:100".getBytes());
    updates.put("bob".getBytes(), "balance:200".getBytes());
    tree.put(1L, updates);

    // Version 2
    JellyfishMerkleTree.CommitResult commit =
            tree.put(2L, Map.of("alice".getBytes(), "balance:150".getBytes()));
    byte[] rootV2 = commit.rootHash();

    Optional<byte[]> latest = tree.get("alice".getBytes());        // "balance:150"
    Optional<byte[]> old = tree.get("alice".getBytes(), 1L);       // "balance:100"
}
```

For tests, `InMemoryJmtStore` works without any storage module.

Put many updates in one commit rather than committing each key on its own: batching normally reduces storage and rewriting.

## Versions

- New versions must increase. Gaps are allowed, and writing below the latest version throws `IllegalArgumentException`.
- `get(key, version)` answers *as of* that version, so a version between two commits reads the earlier commit.
- `get(key, version)` throws `IllegalArgumentException` for a version newer than the latest commit, or below the prune watermark. It doesn't fall back to some other state.
- `getProof(key, version)` needs a root committed at exactly that version, and returns empty otherwise.

If your position can be ahead of the latest commit, such as a chain tip that skipped empty blocks, read at `min(position, store.latestRoot().get().version())`.

## Proofs

`getProof` returns an inclusion proof for a key that exists and an absence proof for one that doesn't.

```java
JmtProfile profile = JmtProfile.classicBlake2b256V1();

JmtProof proof = tree.getProof("alice".getBytes(), 2L).orElseThrow();
boolean included = JmtProofVerifier.verify(rootV2, "alice".getBytes(), proof.value(), proof,
        profile.hashFunction(), profile.commitmentScheme());

// value() is null for an absence proof, and null asks the verifier to check absence
JmtProof absence = tree.getProof("nobody".getBytes(), 2L).orElseThrow();
boolean absent = JmtProofVerifier.verify(rootV2, "nobody".getBytes(), null, absence,
        profile.hashFunction(), profile.commitmentScheme());
```

The verifier returns true only when the proof shows exactly that statement: an inclusion proof never proves absence, and an absence proof never proves a value.

To send a proof over the network, use CBOR:

```java
byte[] wire = tree.getProofWire("alice".getBytes(), 2L).orElseThrow();
boolean valid = tree.verifyProofWire(rootV2, "alice".getBytes(), "balance:150".getBytes(), true, wire);
```

## Pruning

```java
int removed = tree.pruneUpTo(2L);
OptionalLong watermark = store.pruneWatermark();
```

The watermark is the greatest committed version at or below the horizon. Its state stays readable and provable. Reads and proofs below it are rejected, and `truncateAfter` below it throws `IllegalStateException`.

With the RocksDB `AGGRESSIVE` value-prune policy, the watermark moves to the latest version at prune time, because that policy drops older values of rewritten keys.

## Thread safety

Competing writes to a namespace fail immediately with `JmtConcurrentMutationException`. Pruning and rollback are exclusive and fail fast if a read or update is active. Every tree or store wrapper for a namespace must share one coordinator, and PostgreSQL also enforces the single writer across processes.

## Format

Persistent stores record the `classic-radix16-blake2b256-v1` format and refuse to open incompatible data. The deprecated `JellyfishMerkleTree(store, hashFn)` constructors are for in-memory stores only: RocksDB and SQL stores reject them with `JmtFormatMismatchException`.

For metrics, the design and the production checklist, see the [module README](https://github.com/bloxbean/cardano-client-lib/tree/master/verified-structures/jellyfish-merkle).
