# Merkle Patricia Forestry

Cardano-compatible Merkle Patricia Forestry (MPF): the off-chain counterpart of the Aiken library
[`aiken-lang/merkle-patricia-forestry`](https://github.com/aiken-lang/merkle-patricia-forestry). Roots and proofs are
identical to the ones produced by its official off-chain JS library, and are checked against the on-chain code in CI
(see [onchain/](onchain/README.md)).

## Not Ethereum Compatible

This library uses **Blake2b-256** and **CBOR encoding**, not Ethereum's Keccak-256 and RLP. While the radix tree
structure is similar, proofs are **not interoperable** with Ethereum.

## Gradle Dependency

```gradle
dependencies {
    // Core library (in-memory or custom NodeStore)
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry:<version>'

    // Storage backend: RocksDB is recommended; RDBMS (PostgreSQL/H2/SQLite) is an alternative
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rocksdb:<version>'
    // or
    implementation 'com.bloxbean.cardano:cardano-client-merkle-patricia-forestry-rdbms:<version>'
}
```

## Quick Start

```java
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.RocksDbStateTrees;

try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("data/mpf")) {
    MpfTrie trie = new MpfTrie(stateTrees.nodeStore());

    // Keys are hashed with Blake2b-256 before they are stored
    trie.put("account123".getBytes(), accountData);
    trie.put("account456".getBytes(), accountData2);

    byte[] value = trie.get("account123".getBytes());
    byte[] rootForDatum = trie.getRootHashOrEmpty();   // 32 bytes; all zeros when the trie is empty
}
```

See [merkle-patricia-forestry-rocksdb](../merkle-patricia-forestry-rocksdb/README.md) for versions, sessions and
garbage collection.

## Using proofs with Aiken

```java
// Proof as PlutusData, ready for a redeemer (Aiken's `Proof` type)
ListPlutusData proof = trie.getProofPlutusData(key).orElseThrow();

// Same proof as wire CBOR, and off-chain verification (same semantics as Aiken has/miss)
byte[] wire = trie.getProofWire(key).orElseThrow();
boolean present = trie.verifyProofWire(root, key, value, true, wire);   // Aiken mpf.has
boolean absent  = trie.verifyProofWire(root, key, null, false, wire);   // Aiken mpf.miss
```

`getProofWire` / `getProofPlutusData` return the canonical proof for a key: for a present key its inclusion proof,
for an absent key the proof of that key in the trie where it would be inserted. One proof therefore serves several
Aiken operations:

| Aiken call | Proof to pass | Taken from |
|---|---|---|
| `mpf.has(trie, key, value, proof)` | proof of `key` | the trie that contains `key` |
| `mpf.update(trie, key, proof, old, new)` | proof of `key` | the trie before the update |
| `mpf.delete(trie, key, value, proof)` | proof of `key` | the trie before the delete (or after it; it is the same proof) |
| `mpf.insert(trie, key, value, proof)` | proof of `key` | the trie before the insert (or after it; it is the same proof) |
| `mpf.miss(trie, key, proof)` | proof of `key` | the trie that does not contain `key` |

The empty trie: `getRootHash()` returns `null` for an empty trie, while Aiken's `mpf.empty` root is 32 zero bytes.
Use `getRootHashOrEmpty()` for datums; `new MpfTrie(store, <32 zero bytes>)` and `MpfTrie.open(store, <32 zero bytes>)`
open an empty trie, and verification treats a `null` root and the all-zero root alike.

When the root comes from an on-chain datum, open the trie with `MpfTrie.open(store, root)`: it fails fast if the root
is not in the store. The constructors are lenient: with an unknown root, reads return `null` and the next `put`
starts a new trie (`getProofWire` throws `IllegalStateException` for a root or node missing from the store).

> **Security note for validators.** `aiken-lang/merkle-patricia-forestry` 2.1.0 does not check the shape of proof
> steps (nibble range, prefix nibbles, digest lengths) or that a terminal `Leaf`/`Fork` neighbor differs from the
> queried key. With a crafted proof, `mpf.miss` accepts a key that is present, and `mpf.insert` accepts re-inserting
> a key that is already present (changing its value). We reproduced both with `aiken check`
> (`MpfForgedProofTest` documents the cases). CCL never produces such proofs and `verifyProofWire` rejects them,
> but on-chain code does not. A validator that relies on `miss` or `insert` to enforce absence or uniqueness should
> also check every step of the redeemer proof: `Fork` nibble and prefix bytes in 0..15, 32-byte `root`, `key` and
> `value`, and a neighbor nibble different from the key's nibble at that position.

> Proofs produced by CCL 0.8.0-pre6 and earlier differ: exclusion proofs ending in a `Fork` step were rejected
> by Aiken, and inclusion proofs only used `Branch` steps, which Aiken accepts for `has`/`update` but not for
> `delete`. Regenerate proofs with the current version. Old inclusion proofs still verify with `verifyProofWire`;
> old exclusion proofs ending in a `Fork` step (the ones Aiken rejected) now fail it, and older CCL versions reject the
> new Fork-ending exclusion proofs.

## API Reference

```java
// Constructors (lenient) and strict factories
MpfTrie(NodeStore store)                                  // Blake2b-256, MPF commitments
MpfTrie(NodeStore store, byte[] rootHash)                 // existing trie
MpfTrie(NodeStore store, HashFunction hashFn)             // custom hash (not Aiken-compatible)
static MpfTrie open(NodeStore store, byte[] rootHash)     // fails if rootHash is not in the store

// Core operations
void put(byte[] key, byte[] value)
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()                                      // copy; null for an empty trie
byte[] getRootHashOrEmpty()                               // copy; all zeros for an empty trie
void setRootHash(byte[] root)                             // switch trie state (copied)

// Proofs (instance methods: they use the trie's hash function)
Optional<byte[]> getProofWire(byte[] key)
Optional<ListPlutusData> getProofPlutusData(byte[] key)
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean including, byte[] wire)
// Without a trie instance: ProofVerifier.verify(root, key, value, including, wire)

// Introspection
List<Entry> getAllEntries()
List<Entry> getEntries(int limit)
TrieStatistics getStatistics()
int computeSize()
String printTree()
String printTreeJson()
TreeNode getTreeStructure()
TreeNode getTreeStructure(int[] prefix, int maxNodes)
```

`verifyProofWire` and `ProofVerifier.verify` never throw for a malformed proof; they return `false`.

Keys are hashed, so the trie supports point lookups and full iteration, but no prefix or range queries over the
original keys.

## Compatibility testing

- `MpfJsGoldenVectorsTest`: roots and proofs equal the official JS library's for 13 tries (`src/test/js` regenerates
  the vectors with the pinned library), and the 30 fruit proofs in Aiken's own test suite.
- `onchain/lib/ccl_vectors.ak`: CCL proofs run through `has`/`miss`/`insert`/`delete`/`update` by `aiken check`.
- `MpfAikenPropertyTest` (jqwik) and `MpfFuzzTest` (Jazzer): random histories against an independent reference
  model, and untrusted or mutated proof bytes. These use CCL's verifier (the Aiken algorithm plus the stricter
  shape checks above), not the on-chain code; the on-chain code is exercised by `aiken check`.
- `LegacyStoreCompatibilityTest`: tries written by 0.8.0-pre6 still open with identical roots and values.

## Thread Safety

`MpfTrie` is NOT thread-safe, and the storage backends do not coordinate concurrent writers. Use one writer per trie
(or namespace) and external synchronization for shared access.

## Design Documentation

- [MPF Design](docs/design.md) - Architecture and Aiken integration
- [Algorithm Details](docs/design-mpt.md) - Internal implementation (advanced)

## References

- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry)
