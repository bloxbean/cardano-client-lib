# Merkle Patricia Trie

Pure Java implementation of Merkle Patricia Trie (MPT) and Merkle Patricia Forestry (MPF) data structures.

## Overview

This module provides a production-ready implementation of the Merkle Patricia Trie, a cryptographically authenticated radix tree combining Patricia trie path compression with Merkle tree authentication. Supports both classic MPT (Ethereum-inspired structure) and MPF (Cardano/Aiken-compatible) modes.

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

### Cardano-Compatible SecureTrie (Recommended for Smart Contracts)

For Cardano smart contracts and Aiken compatibility, use `SecureTrie`:

```java
import com.bloxbean.cardano.vds.mpt.SecureTrie;

// Simplest way: Blake2b-256 + MPF mode hardcoded
NodeStore store = new RocksDbNodeStore(db);
SecureTrie trie = new SecureTrie(store);

// Keys are automatically hashed with Blake2b-256
trie.put("account123".getBytes(), accountData);
trie.put("account456".getBytes(), accountData2);

// Get root hash for Cardano transaction
byte[] rootHash = trie.getRootHash();

// Load existing trie
SecureTrie existingTrie = new SecureTrie(store, rootHash);
```

**Why SecureTrie prevents branch values:**
- All keys hashed to exactly 32 bytes (64 nibbles)
- All keys terminate at same depth (64 levels)
- No prefix termination possible
- All values automatically at leaves
- ✅ Full Cardano/Aiken compatibility guaranteed!

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

### CLASSIC Mode (MPF + Branch Values)

```java
import com.bloxbean.cardano.vds.mpt.mode.Modes;

MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, Modes.CLASSIC);

// Node hashing: Binary Merkle tree (16-17 leaf tree with branch values)
// Hash function: Blake2b-256
// Encoding: CBOR
// Branch values: Included as 17th element in commitment
// Use case: Off-chain indexing with full branch value integrity
```

### MPF Mode (Merkle Patricia Forestry - Cardano Compatible)

```java
import com.bloxbean.cardano.vds.mpt.mode.Modes;

MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn, Modes.MPF);

// Node hashing: Binary Merkle tree (fixed 16 leaf tree)
// Hash function: Blake2b-256
// Encoding: CBOR
// Branch values: Excluded from commitment (by design)
// Use case: Cardano smart contracts, Aiken compatibility
```

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

## Architecture: MerklePatriciaTrie vs SecureTrie

### MerklePatriciaTrie (Low-Level, Generic)
- **Default Mode**: CLASSIC (supports branch values with full integrity)
- **Keys**: Raw bytes (as provided by application)
- **Use Cases**:
  - Off-chain indexing and databases
  - General-purpose authenticated data structures
  - When you need prefix queries on original keys
- **Branch Values**: Fully supported in CLASSIC mode

```java
// Generic MPT with CLASSIC mode (default)
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);
trie.put("user:alice".getBytes(), data1);  // Works with any keys
trie.put("user:bob".getBytes(), data2);

// Prefix queries work
List<Entry> users = trie.scanByPrefix("user:".getBytes(), 100);
```

### SecureTrie (High-Level, Cardano-Specific)
- **Mode**: MPF (enforced for Aiken compatibility)
- **Keys**: Always hashed to 32 bytes (Blake2b-256)
- **Use Cases**:
  - Cardano smart contracts
  - Aiken on-chain verification
  - User-provided keys (DoS protection)
- **Branch Values**: Impossible by design (all keys same length → all terminate at depth 64)

```java
// Cardano-optimized: Blake2b-256 + MPF hardcoded
SecureTrie trie = new SecureTrie(store);
trie.put("account123".getBytes(), data1);  // Hashed to 32 bytes
trie.put("account456".getBytes(), data2);  // Hashed to 32 bytes

// All values at leaves, no branch values possible
// ✅ Guaranteed Aiken compatibility
```

**Recommendation**:
- ✅ Use **SecureTrie** for Cardano/Aiken
- ✅ Use **MerklePatriciaTrie** for off-chain/indexing

---

## Commitment Schemes

Two cryptographic commitment schemes are supported:

### ClassicMptCommitmentScheme - **DEFAULT for MerklePatriciaTrie**
- Binary Merkle tree with branch value included as 17th element
- Full cryptographic integrity for branch values
- **Default mode** for generic `MerklePatriciaTrie`
- Supports any application keys

**Relationship to MPF:**
CLASSIC mode is essentially **MPF + branch value support**. Both modes use identical algorithms:
- **Hash function**: Blake2b-256 (not Keccak-256)
- **Encoding**: CBOR (not RLP)
- **Tree structure**: Binary Merkle tree for 16-way branches

The **only difference** is branch value handling:
- **CLASSIC**: 16-17 leaf Merkle tree (includes branch value as 17th element)
- **MPF**: Fixed 16 leaf Merkle tree (excludes branch value)

⚠️ **Not Ethereum-compatible** despite the "Classic" name. This implementation uses Blake2b-256 and CBOR encoding, not Ethereum's Keccak-256 and RLP.

### MpfCommitmentScheme (Cardano/Aiken) - **DEFAULT for SecureTrie**
- CBOR encoding with chunked bytestrings
- Optimized for Cardano smart contracts
- Compatible with Aiken merkle-patricia-forestry library
- **Does not include branch values in commitments** (by design)
- Recommended for Cardano/Aiken use cases

```java
// Manual mode selection (advanced usage)
import com.bloxbean.cardano.vds.mpt.mode.Modes;

// MPF mode for Cardano compatibility
MerklePatriciaTrie mptTrie = new MerklePatriciaTrie(store, hashFn, Modes.mpf(hashFn));

// CLASSIC mode for branch value support
MerklePatriciaTrie classicTrie = new MerklePatriciaTrie(store, hashFn, Modes.classic(hashFn));
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
// Cardano-optimized constructors (Blake2b-256 + MPF)
SecureTrie(NodeStore store)
SecureTrie(NodeStore store, byte[] root)

// Custom hash function constructors (uses MPF mode)
SecureTrie(NodeStore store, HashFunction hashFn)
SecureTrie(NodeStore store, HashFunction hashFn, byte[] root)

// Advanced: custom mode
SecureTrie(NodeStore store, HashFunction hashFn, MptMode mode)
SecureTrie(NodeStore store, HashFunction hashFn, byte[] root, MptMode mode)

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
