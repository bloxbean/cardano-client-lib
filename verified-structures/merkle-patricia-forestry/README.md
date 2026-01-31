# Merkle Patricia Forestry

Cardano-compatible Merkle Patricia Forestry (MPF) implementation with full Aiken on-chain verifier compatibility.

## Not Ethereum Compatible

This library uses **Blake2b-256** and **CBOR encoding**, not Ethereum's Keccak-256 and RLP. While the radix tree structure is similar, proofs are **not interoperable** with Ethereum.

## Quick Start

```java
import com.bloxbean.cardano.vds.mpf.MpfTrie;

// Create trie with RocksDB storage
NodeStore store = new RocksDbNodeStore(db);
        MpfTrie trie = new MpfTrie(store);

// Store data (keys automatically hashed with Blake2b-256)
trie.

        put("account123".getBytes(),accountData);
        trie.

        put("account456".getBytes(),accountData2);

        // Get root hash for Cardano transaction
        byte[] rootHash = trie.getRootHash();

        // Retrieve data
        byte[] value = trie.get("account123".getBytes());
```

## Proof Generation

Generate proofs for on-chain verification in Aiken validators:

```java
// Generate proof as PlutusData (for Aiken)
Optional<ListPlutusData> proof = trie.getProofPlutusData("account123".getBytes());

// Or as wire format (CBOR bytes)
Optional<byte[]> proofWire = trie.getProofWire("account123".getBytes());

// Verify proof off-chain
boolean valid = trie.verifyProofWire(
    rootHash,
    "account123".getBytes(),
    accountData,
    true,  // inclusion proof
    proofWire.get()
);
```

## Why MpfTrie?

`MpfTrie` guarantees Aiken compatibility by:
- Hashing all keys to exactly 32 bytes (Blake2b-256)
- Ensuring uniform tree depth (64 nibbles)
- Placing all values at leaf nodes
- Using MPF commitment scheme

## Gradle Dependency

```gradle
dependencies {
    // Core library
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry:0.8.0'

    // Storage backend (choose one)
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rocksdb:0.8.0'
    // or
    implementation 'com.bloxbean.cardano:merkle-patricia-forestry-rdbms:0.8.0'
}
```

## Storage Backends

- **[merkle-patricia-forestry-rocksdb](../merkle-patricia-forestry-rocksdb/)** - High-performance embedded storage
- **[merkle-patricia-forestry-rdbms](../merkle-patricia-forestry-rdbms/)** - PostgreSQL/H2/SQLite support

## API Reference

### MpfTrie

```java
// Constructors
MpfTrie(NodeStore store)                    // Blake2b-256, MPF mode
MpfTrie(NodeStore store, byte[] rootHash)   // Load existing trie

// Core operations
void put(byte[] key, byte[] value)
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()

// Proof generation
Optional<byte[]> getProofWire(byte[] key)
Optional<ListPlutusData> getProofPlutusData(byte[] key)
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean inclusion, byte[] wire)
```

## Design Documentation

- [MPF Design](docs/design.md) - Architecture and Aiken integration
- [Algorithm Details](docs/design-mpt.md) - Internal implementation (advanced)

## Aiken Integration

The `/onchain` folder contains Aiken validators for testing MPF compatibility:

```
onchain/
├── aiken.toml
├── validators/
│   └── mpf_test.ak
└── README.md
```

See [onchain/README.md](onchain/README.md) for on-chain verification testing.

## Performance

| Operation | Complexity |
|-----------|------------|
| Insert/Update/Delete | O(log n) |
| Lookup | O(log n) |
| Proof Generation | O(log n) |

## Thread Safety

`MpfTrie` is NOT thread-safe. Use external synchronization for concurrent access.

## References

- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry)
