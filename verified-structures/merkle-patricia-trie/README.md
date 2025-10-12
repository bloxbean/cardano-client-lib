# Merkle Patricia Trie

Pure Java implementation of Merkle Patricia Trie (MPT) and Merkle Patricia Forestry (MPF) data structures.

## Overview

This module provides a production-ready implementation of the Merkle Patricia Trie, a cryptographically authenticated radix tree combining Patricia trie path compression with Merkle tree authentication. Supports both classic MPT (Ethereum-compatible) and MPF (Cardano/Aiken-compatible) modes.

## Key Features

- **Path Compression** - Extension nodes minimize storage for sparse key spaces
- **Cryptographic Authentication** - Every node is Merkle-hashed for tamper detection
- **Deterministic** - Same data always produces identical root hash
- **Prefix Scanning** - Efficient range queries via prefix matching
- **Two Commitment Schemes** - Classic MPT and MPF (Merkle Patricia Forestry)
- **Proof Generation** - Inclusion/exclusion proofs for trustless verification
- **SecureTrie** - Cardano/Aiken-compatible wrapper with automatic key hashing

## When to Use

### Use Merkle Patricia Trie When:
- Building off-chain indexers or databases
- Need prefix-based range queries
- Want to recover original keys from the trie
- Trusted key environment (not user-provided)

### Use SecureTrie When:
- Building Cardano smart contracts
- Need Aiken merkle-patricia-forestry compatibility
- Storing untrusted/user-provided keys
- DoS protection is critical

### Don't Use MPT When:
- Need versioned state storage → use [JellyfishMerkleTree](../jellyfish-merkle/)
- No prefix queries needed → consider JMT for better performance

## Quick Start

### Basic MPT Usage

```java
import com.bloxbean.cardano.vds.mpt.MerklePatriciaTrie;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// Setup storage (in-memory for this example)
NodeStore store = new InMemoryNodeStore();
HashFunction hashFn = Blake2b256::digest;

// Create empty trie
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);

// Insert key-value pairs
trie.put("user:alice".getBytes(), "balance:100".getBytes());
trie.put("user:bob".getBytes(), "balance:200".getBytes());
trie.put("admin:root".getBytes(), "permission:all".getBytes());

// Get root hash (uniquely identifies the trie state)
byte[] rootHash = trie.getRootHash();

// Retrieve values
byte[] aliceBalance = trie.get("user:alice".getBytes());

// Delete keys
trie.delete("user:bob".getBytes());

// Prefix scan (find all users)
List<MerklePatriciaTrie.Entry> users = trie.scanByPrefix("user:".getBytes(), 100);
for (MerklePatriciaTrie.Entry entry : users) {
    System.out.println(new String(entry.getKey()) + " = " + new String(entry.getValue()));
}
```

### Cardano-Compatible SecureTrie

For Cardano smart contracts and Aiken compatibility:

```java
import com.bloxbean.cardano.vds.mpt.SecureTrie;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// SecureTrie automatically hashes keys (matches Aiken's merkle-patricia-forestry)
NodeStore store = new RocksDbNodeStore(db);
HashFunction blake2b = Blake2b256::digest;

SecureTrie trie = new SecureTrie(store, blake2b);

// Keys are hashed with Blake2b-256 before storage
trie.put("account123".getBytes(), accountData);
trie.put("account456".getBytes(), accountData2);

// Get root hash for Cardano transaction
byte[] rootHash = trie.getRootHash();

// Verify in smart contract:
// - On-chain validator uses Aiken MPF to verify inclusion
// - Root hash commits to the entire trie state
```

### Loading Existing Trie

```java
// Load trie from existing root hash
byte[] existingRoot = loadRootFromDatabase();
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, existingRoot);

// Query without modifying
byte[] value = trie.get("key".getBytes());

// Modify and get new root
trie.put("newkey".getBytes(), "newvalue".getBytes());
byte[] newRoot = trie.getRootHash();
```

### Proof Generation and Verification

```java
import com.bloxbean.cardano.vds.mpt.mode.Modes;

// Create MPF-mode trie (for compact proofs)
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, Modes.MPF);

// Insert data
trie.put("alice".getBytes(), "100".getBytes());
byte[] rootHash = trie.getRootHash();

// Generate inclusion proof (wire format)
Optional<byte[]> proofWire = trie.getProofWire("alice".getBytes());

// Verify proof (can be done without access to full trie)
boolean valid = trie.verifyProofWire(
    rootHash,
    "alice".getBytes(),
    "100".getBytes(),
    true,  // inclusion proof
    proofWire.get()
);

// Proof is cryptographically valid
assert valid;
```

## MPT vs MPF Modes

### Classic MPT (Ethereum-Compatible)

```java
import com.bloxbean.cardano.vds.mpt.mode.Modes;

MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, Modes.CLASSIC);

// Node hashing: RLP-based (Ethereum compatible)
// Proof format: List of RLP-encoded nodes
// Use case: Ethereum compatibility, interop with geth/erigon
```

### MPF (Merkle Patricia Forestry - Cardano Compatible)

```java
import com.bloxbean.cardano.vds.mpt.mode.Modes;

MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, Modes.MPF);

// Node hashing: CBOR-based with optimized structure
// Proof format: Compact CBOR encoding
// Use case: Cardano smart contracts, Aiken compatibility
```

**Recommendation**: Use `Modes.MPF` for new projects unless Ethereum compatibility is required.

## Prefix Scanning

Efficient range queries via prefix matching:

```java
// Find all keys starting with "user:"
List<MerklePatriciaTrie.Entry> users = trie.scanByPrefix("user:".getBytes(), 100);

// Find all keys (empty prefix)
List<MerklePatriciaTrie.Entry> allKeys = trie.scanByPrefix(new byte[0], -1);

// Limit results
List<MerklePatriciaTrie.Entry> first10 = trie.scanByPrefix("item:".getBytes(), 10);
```

**Performance**: O(k + n) where k is prefix length and n is number of matching keys.

**Note**: Prefix scanning doesn't work with `SecureTrie` because key hashing destroys prefix relationships.

## Node Types

### Leaf Node
- Stores key suffix and value
- Terminal node in the trie path

### Extension Node
- Path compression for single-child paths
- Stores nibble prefix and child hash
- Reduces tree depth for sparse key spaces

### Branch Node
- 16 children (one per hex digit)
- Stores child hashes
- Optional value if key ends at this node

## Commitment Schemes

Two cryptographic commitment schemes are supported:

### ClassicMptCommitmentScheme (Default)
- Ethereum RLP-based encoding
- Compatible with geth, erigon
- Standard MPT proofs

### MpfCommitmentScheme (Cardano/Aiken)
- CBOR encoding with chunked bytestrings
- Optimized for Cardano smart contracts
- Compatible with Aiken merkle-patricia-forestry library

```java
import com.bloxbean.cardano.vds.mpt.commitment.MpfCommitmentScheme;

CommitmentScheme mpf = new MpfCommitmentScheme(hashFn);
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, null, mpf);
```

## Aiken Compatibility

The `/onchain` folder contains Aiken validators for testing MPF compatibility:

```
merkle-patricia-trie/
└── onchain/                  # Aiken smart contract tests
    ├── aiken.toml
    ├── validators/
    │   └── mpf_test.ak      # MPF verification validator
    └── README.md            # On-chain testing instructions
```

**Purpose**: Validates that MPF proofs generated by this library can be verified by Aiken smart contracts on Cardano.

See [onchain/README.md](onchain/README.md) for details.

## Gradle Dependency

```gradle
dependencies {
    // MPT core only
    implementation 'com.bloxbean.cardano:merkle-patricia-trie:0.8.0'

    // For production use, also add a storage backend:
    // RocksDB backend
    implementation 'com.bloxbean.cardano:merkle-patricia-trie-rocksdb:0.8.0'

    // Or PostgreSQL backend
    implementation 'com.bloxbean.cardano:merkle-patricia-trie-rdbms:0.8.0'
    implementation 'org.postgresql:postgresql:42.6.0'
}
```

## Storage Backends

This module provides the core MPT algorithm. For persistence, use one of:

- **[merkle-patricia-trie-rocksdb](../merkle-patricia-trie-rocksdb/)** - Embedded RocksDB with GC
- **[merkle-patricia-trie-rdbms](../merkle-patricia-trie-rdbms/)** - PostgreSQL/H2/SQLite

## API Overview

### MerklePatriciaTrie

```java
// Constructors
MerklePatriciaTrie(NodeStore store, HashFunction hashFn)
MerklePatriciaTrie(NodeStore store, HashFunction hashFn, byte[] root)
MerklePatriciaTrie(NodeStore store, HashFunction hashFn, MptMode mode)

// Core operations
void put(byte[] key, byte[] value)
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()
void setRootHash(byte[] root)

// Scanning
List<Entry> scanByPrefix(byte[] prefix, int limit)

// Proofs
Optional<byte[]> getProofWire(byte[] key)
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean including, byte[] wire)
```

### SecureTrie

```java
// Constructors
SecureTrie(NodeStore store, HashFunction hashFn)
SecureTrie(NodeStore store, HashFunction hashFn, byte[] root)

// Core operations (same as MerklePatriciaTrie but keys are auto-hashed)
void put(byte[] key, byte[] value)
byte[] get(byte[] key)
void delete(byte[] key)
byte[] getRootHash()

// Proofs
Optional<byte[]> getProofWire(byte[] key)
boolean verifyProofWire(byte[] root, byte[] key, byte[] value, boolean including, byte[] wire)
```

## Design Documentation

- [MPT Design](docs/design-mpt.md) - Core algorithm and node structure
- [MPF Design](docs/design-mpf.md) - Merkle Patricia Forestry details
- [Aiken Compatibility](onchain/README.md) - On-chain verification testing

## Performance

### Time Complexity
- Insert/Update/Delete: O(log₁₆ n) where n is number of keys
- Get: O(log₁₆ n)
- Prefix Scan: O(k + m) where k is prefix length, m is results

### Space Complexity
- Worst case: O(k × n) where k is average key length
- Best case (with compression): O(n)

## Thread Safety

- **MerklePatriciaTrie**: NOT thread-safe, requires external synchronization
- **SecureTrie**: NOT thread-safe, requires external synchronization
- **NodeStore implementations**: Check specific backend documentation

## Related Modules

- [verified-structures-core](../verified-structures-core/) - Core interfaces
- [merkle-patricia-trie-rocksdb](../merkle-patricia-trie-rocksdb/) - RocksDB persistence
- [merkle-patricia-trie-rdbms](../merkle-patricia-trie-rdbms/) - SQL persistence
- [jellyfish-merkle](../jellyfish-merkle/) - Alternative: JMT for versioned state

## References

- [Aiken Merkle Patricia Forestry](https://github.com/aiken-lang/merkle-patricia-forestry) - Cardano MPF library
- [Ethereum Yellow Paper](https://ethereum.github.io/yellowpaper/paper.pdf) - Original MPT specification
