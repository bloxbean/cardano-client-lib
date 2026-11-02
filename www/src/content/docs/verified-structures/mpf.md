---
title: "MpfTrie"
description: "Merkle Patricia Forestry on RocksDB, with proofs that Aiken validators can verify."
---

> **Preview feature**: available in the [latest preview](/start-here/installation/#versions). APIs may change before the stable release.

`MpfTrie` is the off-chain counterpart of Aiken's [merkle-patricia-forestry](https://github.com/aiken-lang/merkle-patricia-forestry) library. Its roots and proofs are identical to those of the library's official JavaScript implementation.

It uses Blake2b-256 and CBOR, so its proofs don't work with Ethereum's Merkle Patricia tries.

## Quick start

Add the [dependency](/verified-structures/overview/#dependencies), then:

```java
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbMptSession;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbStateTrees;

try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore());
    RocksDbMptSession session = RocksDbMptSession.of(stateTrees);

    // One atomic batch: trie nodes, version entry and refcounts
    session.write(() -> {
        trie.put("alice".getBytes(), "100".getBytes());
        trie.put("bob".getBytes(), "200".getBytes());
        return stateTrees.putRootWithRefcount(trie.getRootHash());
    });

    byte[] value = trie.get("alice".getBytes());     // "100"
    byte[] rootForDatum = trie.getRootHashOrEmpty(); // store this on chain
}
```

Keys are hashed before they're stored, so you can look up a key or iterate over all entries, but you can't query by prefix or range.

## Reopen a trie

```java
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore(), stateTrees.rootsIndex().latest());
    byte[] value = trie.get("alice".getBytes());
}
```

When the root comes from an on-chain datum, use `MpfTrie.open(store, root)`, available from 0.8.0-pre7. It throws `IllegalStateException` if the store doesn't have that root. The constructors are lenient: with an unknown root, reads return `null` and the next `put` starts a new trie.

## The empty trie

`getRootHash()` returns `null` for an empty trie, while Aiken's `mpf.empty` root is 32 zero bytes. For datums, use `getRootHashOrEmpty()`, available from 0.8.0-pre7, which returns those zero bytes. `new MpfTrie(store, <32 zero bytes>)` and `MpfTrie.open(store, <32 zero bytes>)` both open an empty trie.

## Proofs for Aiken

```java
byte[] key = "alice".getBytes();
byte[] value = trie.get(key);
byte[] root = trie.getRootHash();

// Ready to use in a redeemer, as Aiken's `Proof` type
ListPlutusData proof = trie.getProofPlutusData(key).orElseThrow();

// The same proof as wire CBOR, with off-chain checks
byte[] wire = trie.getProofWire(key).orElseThrow();
boolean present = trie.verifyProofWire(root, key, value, true, wire);

byte[] missing = "carol".getBytes();
byte[] missingWire = trie.getProofWire(missing).orElseThrow();
boolean absent = trie.verifyProofWire(root, missing, null, false, missingWire);
```

`verifyProofWire` returns `false` for a malformed proof and never throws. To verify without a trie, use `ProofVerifier.verify(root, key, value, including, wire)`.

A key's proof depends on the trie you take it from, and one proof serves several Aiken operations:

| Aiken call | Take the proof of `key` from |
|---|---|
| `mpf.has(trie, key, value, proof)` | The trie that contains `key` |
| `mpf.miss(trie, key, proof)` | The trie that doesn't contain `key` |
| `mpf.insert(trie, key, value, proof)` | The trie before the insert, or after it |
| `mpf.delete(trie, key, value, proof)` | The trie before the delete, or after it |
| `mpf.update(trie, key, proof, old, new)` | The trie before the update |

:::danger[Validators that use `miss` or `insert`]
`aiken-lang/merkle-patricia-forestry` 2.1.0 doesn't check the shape of proof steps, or that a terminal `Leaf` or `Fork` neighbor differs from the queried key. With a crafted proof, `mpf.miss` accepts a key that is present, and `mpf.insert` accepts re-inserting a present key with a new value. We reproduced both with `aiken check`.

CCL never produces such proofs and `verifyProofWire` rejects them, but on-chain code doesn't. If your validator relies on `miss` or `insert` to enforce absence or uniqueness, also check every step of the redeemer proof: `Fork` nibble and prefix bytes in 0..15, 32-byte `root`, `key` and `value`, and a neighbor nibble that differs from the key's nibble at that position.
:::

The same proof can be encoded several ways, and `verifyProofWire` accepts all of them. Don't use proof bytes or their hash as an identifier; use the key and the root.

## Upgrading from 0.8.0-pre6 or earlier

Proofs changed in 0.8.0-pre7, so **regenerate stored proofs**:

- Earlier exclusion proofs ending in a `Fork` step were rejected by Aiken, and they now fail `verifyProofWire` too. Older CCL versions reject the new ones.
- Earlier inclusion proofs used only `Branch` steps. Aiken accepts those for `has` and `update` but not for `delete`. They still pass `verifyProofWire`.

Tries written by 0.8.0-pre6 still open with identical roots and values. See [garbage collection](/verified-structures/multi-version-gc/) if you ran GC on them.

## API

```java
// Constructors (lenient) and strict factory
MpfTrie(NodeStore store)
MpfTrie(NodeStore store, byte[] rootHash)
MpfTrie(NodeStore store, HashFunction hashFn)             // not Aiken-compatible
static MpfTrie open(NodeStore store, byte[] rootHash)     // fails if the root isn't in the store

// Core operations
void put(byte[] key, byte[] value)
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()                                      // null for an empty trie
byte[] getRootHashOrEmpty()                               // all zeros for an empty trie
void setRootHash(byte[] root)

// Proofs
Optional<byte[]> getProofWire(byte[] key)
Optional<ListPlutusData> getProofPlutusData(byte[] key)
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean including, byte[] wire)

// Introspection
List<Entry> getAllEntries()
List<Entry> getEntries(int limit)
TrieStatistics getStatistics()
int computeSize()
String printTree()
```

`MpfTrie` isn't thread-safe, and the storage backends don't coordinate concurrent writers. Use one writer per trie.

For more, see the [module README](https://github.com/bloxbean/cardano-client-lib/tree/master/verified-structures/merkle-patricia-forestry).
