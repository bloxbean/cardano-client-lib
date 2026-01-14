# Merkle Patricia Forestry Design

## Not Ethereum Compatible

This library is **NOT compatible with Ethereum's Merkle Patricia Trie**.

| Aspect | Ethereum MPT | Cardano MPF |
|--------|--------------|-------------|
| Hash Function | Keccak-256 | Blake2b-256 |
| Encoding | RLP | CBOR |
| Target | Solidity/EVM | Plutus/Aiken |

While the radix tree structure is conceptually similar, the different hashing and encoding means proofs are not interoperable.

---

## Overview

Merkle Patricia Forestry (MPF) is a cryptographically authenticated radix tree optimized for Cardano smart contracts. It combines:

- **Patricia Trie**: Path compression for space efficiency
- **Merkle Tree**: Cryptographic authentication via hashing
- **16-way Branching**: Efficient navigation using hex nibbles

**Use Cases:**
- On-chain state verification (dApps, DeFi)
- Layer-2 state commitments (sidechains, rollups)
- Cross-chain bridges (state proofs)
- Authenticated data feeds (oracles)

---

## MpfTrie

`MpfTrie` is the primary API for Cardano developers.

### Key Hashing

All keys are hashed with Blake2b-256 before insertion:

```
user_key → Blake2b-256(user_key) → 32 bytes → 64 nibbles
```

This guarantees:
- **Uniform depth**: All keys are exactly 64 nibbles
- **Balanced tree**: Hash output is uniformly distributed
- **DoS protection**: Attackers cannot craft deep paths
- **Aiken compatibility**: Matches on-chain verifier expectations

### Architecture

```
┌─────────────────────────────────────────────────┐
│                   MpfTrie                        │
│  ┌─────────────────────────────────────────────┐│
│  │  put(key, value)                            ││
│  │    → Hash key with Blake2b-256              ││
│  │    → Insert into MerklePatriciaTrie (MPF)   ││
│  │    → Return new root hash                   ││
│  └─────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────┐│
│  │  getProofPlutusData(key)                    ││
│  │    → Generate inclusion/exclusion proof     ││
│  │    → Format as PlutusData for Aiken         ││
│  └─────────────────────────────────────────────┘│
└─────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│              NodeStore (Backend)                 │
│         RocksDB / PostgreSQL / H2                │
└─────────────────────────────────────────────────┘
```

---

## Proof Generation

### Proof Structure

A proof is a path from root to the target key, containing:

1. **Branch nodes**: 16-way decision points with sibling hashes
2. **Extension nodes**: Compressed path segments
3. **Leaf node**: Terminal node with value hash

### PlutusData Format

For Aiken validators, proofs are formatted as PlutusData:

```java
Optional<ListPlutusData> proof = trie.getProofPlutusData("key".getBytes());
```

The PlutusData can be passed directly to Aiken validators for on-chain verification.

### Wire Format

For serialization/storage, use CBOR wire format:

```java
Optional<byte[]> proofWire = trie.getProofWire("key".getBytes());
```

---

## Commitment Scheme

The root hash commits to the entire trie state.

### Leaf Commitment
```
H( key_suffix || H(value) )
```

### Extension Commitment
```
H( path || child_hash )
```

### Branch Commitment
```
MerkleRoot( child[0], child[1], ..., child[15] )
```

Where `H` is Blake2b-256 and `MerkleRoot` builds a binary Merkle tree over the 16 children.

For detailed algorithm descriptions, see [design-mpt.md](design-mpt.md).

---

## Aiken Integration

### Off-Chain (Java)

```java
MpfTrie trie = new MpfTrie(nodeStore);
trie.put("key".getBytes(), "value".getBytes());

byte[] rootHash = trie.getRootHash();
Optional<ListPlutusData> proof = trie.getProofPlutusData("key".getBytes());
```

### On-Chain (Aiken)

```aiken
use merkle_patricia_forestry/trie

validator {
  fn verify(datum, redeemer, ctx) {
    let root_hash = datum.root
    let key = redeemer.key
    let value = redeemer.value
    let proof = redeemer.proof

    trie.verify(root_hash, key, value, proof)
  }
}
```

### Testing

The `/onchain` folder contains Aiken tests validating Java ↔ Aiken compatibility:

```bash
cd merkle-patricia-forestry/onchain
aiken check   # Type-check
aiken test    # Run unit tests
aiken build   # Compile to Plutus
```

---

## References

- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry)
- [CBOR (RFC 8949)](https://www.rfc-editor.org/rfc/rfc8949.html)
- [Blake2b (RFC 7693)](https://www.rfc-editor.org/rfc/rfc7693.html)
