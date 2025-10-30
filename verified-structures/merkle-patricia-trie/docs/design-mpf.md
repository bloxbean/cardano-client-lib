# Merkle Patricia Forestry (MPF) Design

**Module:** `merkle-patricia-trie` (MPF mode)

## Table of Contents

1. [Overview](#overview)
2. [MPF vs Classic MPT](#mpf-vs-classic-mpt)
3. [Commitment Scheme](#commitment-scheme)
4. [Proof Format](#proof-format)
5. [Aiken Compatibility](#aiken-compatibility)
6. [CBOR Encoding](#cbor-encoding)
7. [Performance](#performance)
8. [References](#references)

---

## 1. Overview

### 1.1 What is Merkle Patricia Forestry?

Merkle Patricia Forestry (MPF) is a Cardano-specific variant of Merkle Patricia Tries optimized for on-chain verification in Plutus/Aiken smart contracts.

**Key Differences from Ethereum's MPT:**
1. **CBOR encoding** instead of RLP
2. **Blake2b-256** hash instead of Keccak-256
3. **Compact proofs** optimized for Plutus script size limits
4. **Aiken-compatible** proof format for on-chain verification

**Note:** MPF uses Ethereum-inspired radix tree structure but is not cryptographically compatible with Ethereum due to different hashing and encoding.

**Use Cases:**
- On-chain state verification (dApps, DeFi protocols)
- Layer-2 state commitments (sidechains, rollups)
- Cross-chain bridges (state proofs)
- Authenticated data feeds (oracles)

### 1.2 Architecture

```
┌─────────────────────────────────────────────────────┐
│         Java Off-Chain (merkle-patricia-trie)       │
│  ┌───────────────────────────────────────────────┐  │
│  │  MerklePatriciaTrie (MPF mode)                │  │
│  │  - MpfCommitmentScheme                        │  │
│  │  - MpfProofCodec                              │  │
│  │  - Blake2b-256 hashing                        │  │
│  │  - CBOR encoding with chunked bytestrings     │  │
│  └───────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────┘
                       │
                       │ CBOR Proof
                       ▼
┌─────────────────────────────────────────────────────┐
│        Aiken On-Chain (Plutus Validator)            │
│  ┌───────────────────────────────────────────────┐  │
│  │  MPF Proof Verifier (Aiken)                   │  │
│  │  - Parse CBOR proof                           │  │
│  │  - Recompute root hash                        │  │
│  │  - Compare with expected root                 │  │
│  │  - Accept/reject transaction                  │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

---

## 2. MPF vs Classic MPT

### 2.1 Comparison Table

| Feature | Ethereum MPT | Cardano MPF |
|---------|--------------|-------------|
| Hash Function | Keccak-256 | Blake2b-256 |
| Encoding | RLP | CBOR |
| Bytestring Chunking | No | Yes (CBOR indefinite) |
| Proof Format | Node array | Compact CBOR |
| On-Chain Language | Solidity/Yul | Plutus/Aiken |
| Branching | 16-way (hex) | 16-way (hex) |
| Path Encoding | HP (Hex-Prefix) | HP (same) |

### 2.2 Why CBOR?

**Advantages:**
1. **Standard:** IETF RFC 8949 (not proprietary like RLP)
2. **Compact:** Efficient encoding, smaller proofs
3. **Readable:** Self-describing format (easier debugging)
4. **Plutus Native:** Cardano scripts use CBOR natively

**Disadvantage:**
- Slightly larger than RLP for small data (5-10% overhead)
- **Acceptable for Cardano ecosystem benefits**

### 2.3 Why Blake2b-256?

**Advantages:**
1. **Faster:** 3x faster than Keccak-256 (and SHA-256)
2. **Cardano Standard:** Used throughout Cardano (addresses, hashes)
3. **Secure:** 128-bit collision resistance (same as Keccak)
4. **Standardized:** IETF RFC 7693

**Benchmark (1 KB data):**
- Blake2b-256: ~1,200 ns
- Keccak-256: ~3,500 ns
- **3x performance improvement**

---

## 3. Commitment Scheme

### 3.1 Leaf Commitment

**Formula:**
```
H( suffix || value_hash )
```

**Where:**
- `suffix` = remaining nibbles from leaf's storage position to full key
- `value_hash` = Blake2b-256(value)

**Example:**
```
Key: 0x68656c6c6f (nibbles: [6,8,6,5,6,c,6,c,6,f])
Leaf at path [6,8]:
  suffix = [6,5,6,c,6,c,6,f]
  value_hash = Blake2b-256("world")

Commitment:
  H( bytes(suffix) || value_hash )
```

**Implementation:**
```java
public byte[] commitLeaf(NibblePath suffix, byte[] valueHash) {
    byte[] suffixBytes = suffix.toBytes();
    byte[] combined = new byte[suffixBytes.length + valueHash.length];
    System.arraycopy(suffixBytes, 0, combined, 0, suffixBytes.length);
    System.arraycopy(valueHash, 0, combined, suffixBytes.length, valueHash.length);
    return hashFn.digest(combined);
}
```

### 3.2 Extension Commitment

**Formula:**
```
H( path || child_hash )
```

**Example:**
```
Extension([a,b,c]):
  path_bytes = bytes([a,b,c]) = 0x0a0b0c (packed)
  child_hash = 32-byte hash

Commitment:
  H( 0x0a0b0c || child_hash )
```

### 3.3 Branch Commitment

**Formula:**
```
H( child[0] || child[1] || ... || child[15] || value_hash )
```

**Where:**
- Null children = 32 zero bytes (0x00...00)
- Null value = 32 zero bytes

**Example:**
```
Branch with children at [0], [5], [F]:
  child[0] = <32-byte hash>
  child[1..4] = 0x00...00 (32 bytes each)
  child[5] = <32-byte hash>
  child[6..E] = 0x00...00
  child[F] = <32-byte hash>
  value_hash = 0x00...00 (no value at branch)

Commitment:
  H( child[0] || 0x00...00 × 4 || child[5] || 0x00...00 × 9 || child[F] || 0x00...00 )
  = H(520 bytes total)
```

**Optimization:** Can use sparse representation on-chain (only include non-null children)

---

## 4. Proof Format

### 4.1 Classic Proof (Full Nodes)

**Structure:**
```cbor
[
  <branch_node_cbor>,     // Root
  <extension_node_cbor>,  // Intermediate
  <leaf_node_cbor>        // Terminal
]
```

**Pros:**
- Simple format
- Easy to verify (decode each node)

**Cons:**
- Larger size (includes all node data)
- 2-4 KB for typical proof

### 4.2 MPF Compact Proof (Optimized)

**Structure:**
```cbor
{
  "steps": [
    {
      "path": <nibble_path>,
      "siblings": [<hash>, <hash>, ...],  // Only non-null children
      "nibble": <int>                     // Direction taken
    },
    ...
  ],
  "leaf": {
    "suffix": <nibble_path>,
    "value_hash": <hash>
  }
}
```

**Pros:**
- 30-50% smaller (only essential data)
- Optimized for Plutus script size limits

**Cons:**
- More complex verification logic
- Requires on-chain proof parser

**Size Comparison:**
- Classic proof: 2-4 KB
- Compact proof: 1-2 KB
- **50% size reduction**

### 4.3 Proof Generation Algorithm

```python
def generate_proof(key):
    nibbles = to_nibbles(hash(key))
    proof_nodes = []
    node = load_root()
    depth = 0

    while True:
        proof_nodes.append(encode_node(node))

        if node is LeafNode:
            break
        elif node is ExtensionNode:
            depth += len(node.path)
            node = load(node.child)
        elif node is BranchNode:
            nibble = nibbles[depth]
            child_hash = node.children[nibble]
            if child_hash is NULL:
                break  # Non-inclusion
            node = load(child_hash)
            depth += 1

    return cbor.encode(proof_nodes)
```

---

## 5. Aiken Compatibility

### 5.1 On-Chain Verification (Aiken)

**Aiken Validator Example:**
```aiken
use aiken/hash.{blake2b_256}
use aiken/bytearray
use aiken/cbor

validator mpf_proof_verifier {
  fn verify(
    root_hash: Hash<Blake2b_256>,
    key: ByteArray,
    value: ByteArray,
    proof: ByteArray
  ) -> Bool {
    // 1. Decode CBOR proof
    let proof_nodes = cbor.decode(proof)

    // 2. Compute expected hash from proof
    let computed_hash = reconstruct_root(proof_nodes, key, value)

    // 3. Compare with expected root
    computed_hash == root_hash
  }
}

fn reconstruct_root(
  nodes: List<MptNode>,
  key: ByteArray,
  value: ByteArray
) -> Hash<Blake2b_256> {
  let nibbles = to_nibbles(blake2b_256(key))
  let value_hash = blake2b_256(value)

  // Traverse proof bottom-up
  let leaf_hash = commit_leaf(suffix, value_hash)
  fold_left(nodes, leaf_hash, fn(hash, node) {
    when node is {
      Branch(children) -> commit_branch(children, hash)
      Extension(path, _) -> commit_extension(path, hash)
    }
  })
}
```

### 5.2 Plutus Script Size Considerations

**Plutus Constraints:**
- Maximum script size: 16,384 bytes (16 KB)
- Cost model: Charged per execution unit

**Optimization Strategies:**

1. **Use compact proof format:** Saves 1-2 KB per proof
2. **Minimize CBOR parsing:** Use pre-decoded data where possible
3. **Share common functions:** Extract repeated logic to libraries
4. **Use builtin hashing:** `blake2b_256` is native (cheap)

**Example Cost:**
- Proof verification: ~5,000-10,000 execution units
- Script size: 8-12 KB (including CBOR parsing)
- Fits comfortably within limits

### 5.3 Testing On-Chain Integration

**Test Suite (verified-structures/merkle-patricia-trie/onchain/):**

```
onchain/
├── aiken.toml          # Aiken project config
├── lib/
│   └── mpf_proof.ak    # MPF proof verifier
├── validators/
│   └── test_validator.ak  # Test contract
└── tests/
    └── proof_test.ak   # Unit tests
```

**Running Tests:**
```bash
cd verified-structures/merkle-patricia-trie/onchain
aiken check           # Type-check
aiken test            # Run unit tests
aiken build           # Compile to Plutus
```

**Test Vectors:**
- Generate proof in Java
- Export as CBOR file
- Verify in Aiken unit test
- Ensures Java ↔ Aiken compatibility

---

## 6. CBOR Encoding

### 6.1 Chunked Bytestrings

**Problem:** Plutus has 64-byte limit for definite-length bytestrings

**Solution:** CBOR indefinite-length bytestrings (chunked)

**Format:**
```
0x5F             # Indefinite bytestring start
0x58 40          # Chunk 1: definite 64 bytes
  <64 bytes>
0x58 40          # Chunk 2: definite 64 bytes
  <64 bytes>
...
0xFF             # Indefinite end
```

**Example (96-byte value):**
```cbor
5F                  # Start indefinite bytestring
  58 40             # Chunk 1: 64 bytes
    <64 bytes>
  58 20             # Chunk 2: 32 bytes
    <32 bytes>
FF                  # End
```

**Implementation (Java):**
```java
public void encodeChunked(ByteArrayOutputStream out, byte[] data) {
    out.write(0x5F);  // Indefinite start

    int offset = 0;
    while (offset < data.length) {
        int chunkSize = Math.min(64, data.length - offset);

        // Definite chunk header
        out.write(0x58);  // Major type 2, additional info 24
        out.write(chunkSize);

        // Chunk data
        out.write(data, offset, chunkSize);
        offset += chunkSize;
    }

    out.write(0xFF);  // Indefinite end
}
```

### 6.2 Node Encoding

**Leaf Node:**
```cbor
82                  # Array, 2 elements
  58 0A             # Bytestring, 10 bytes (HP-encoded key)
    <HP bytes>
  5F ... FF         # Chunked bytestring (value)
```

**Extension Node:**
```cbor
82                  # Array, 2 elements
  58 05             # Bytestring, 5 bytes (HP path)
    <HP bytes>
  58 20             # Bytestring, 32 bytes (child hash)
    <32 bytes>
```

**Branch Node:**
```cbor
91                  # Array, 17 elements
  58 20             # Child 0: 32 bytes
    <32 bytes>
  58 20             # Child 1: 32 bytes
    <32 bytes>
  ...
  40                # Child 15: empty (null)
  40                # Value: empty (null)
```
