# Merkle Patricia Trie (MPT) Implementation Guide

A comprehensive guide to the Merkle Patricia Trie implementation in the state-trees module, optimized for Cardano's Merkle Patricia Forestry (MPF) on-chain verification.

## Table of Contents

- [What is a Merkle Patricia Trie?](#what-is-a-merkle-patricia-trie)
- [Why Use MPT for Cardano?](#why-use-mpt-for-cardano)
- [MPF vs Classic MPT](#mpf-vs-classic-mpt)
- [Key Concepts](#key-concepts)
- [Node Types](#node-types)
- [How It Works](#how-it-works)
- [Implementation Details](#implementation-details)
- [Code Examples](#code-examples)
- [RocksDB Integration](#rocksdb-integration)
- [Advanced Topics](#advanced-topics)

## What is a Merkle Patricia Trie?

A **Merkle Patricia Trie (MPT)** is a cryptographically authenticated data structure that combines:

1. **Patricia Trie**: A compressed trie (prefix tree) for efficient storage
2. **Merkle Tree**: Cryptographic hashing for data integrity
3. **Radix Tree**: Base-16 (hexadecimal) branching for optimal performance

Think of it as a **smart dictionary** where:
- Keys can be looked up efficiently
- The entire structure has a cryptographic "fingerprint" (root hash)
- Any change in data creates a different fingerprint
- Storage is optimized through path compression

## Why Use MPT for Cardano?

### 🔗 **Cardano On-Chain Verification**
- **MPF (Merkle Patricia Forestry) compatible** - Proofs verified by Aiken smart contracts
- **Optimized commitment scheme** - Designed for Cardano's on-chain validators
- **SecureTrie integration** - Hashed keys prevent branch-terminal values (MPF requirement)

### 🔍 **Efficient State Management**
- Find any key-value pair in O(log n) time
- Support for prefix-based searches
- Optimized for blockchain state workloads

### 🔒 **Cryptographic Security**
- Every node has a cryptographic hash (Blake2b-256)
- Root hash represents the entire state
- Impossible to modify without detection

### 💾 **Storage Efficiency**
- Path compression reduces storage overhead
- Only stores necessary intermediate nodes
- Persistent data structure (shares common subtrees)
- RocksDB backend for production deployments

## MPF vs Classic MPT

Our MPT implementation supports two commitment schemes:

### MPF (Merkle Patricia Forestry) - Default for Cardano

**Recommended for Cardano applications** - Compatible with Aiken on-chain verifiers

- **Branch value slot is empty**: Not mixed into branch commitment
- **Optimized for SecureTrie**: Keys are hashed, preventing branch-terminal values
- **Smaller proofs**: Branch steps omit optional branch-value hash (~32 bytes savings per affected step)
- **Aiken compatible**: Proofs verified by MPF validator in Plutus scripts

```java
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

// MPF mode (default) - for Cardano
NodeStore store = new InMemoryNodeStore();
MerklePatriciaTrie mpfTrie = new MerklePatriciaTrie(store, Blake2b256::digest);

// SecureTrie ensures hashed keys (required for MPF)
SecureTrie secureTrie = new SecureTrie(store, Blake2b256::digest);
secureTrie.put("plaintext-key".getBytes(), "value".getBytes());
```

### Classic MPT - Legacy Compatibility

**Use only for off-chain legacy MPT compatibility** - Not compatible with Cardano MPF validators

- **Branch value slot preserved**: Terminal value mixed into branch commitment
- **Different root hashes**: When keys terminate at branches, roots differ from MPF
- **Not Aiken compatible**: Proofs cannot be verified by MPF on-chain validator

```java
import com.bloxbean.cardano.statetrees.mpt.commitment.ClassicMptCommitmentScheme;

// Classic mode - off-chain only
MerklePatriciaTrie classicTrie = new MerklePatriciaTrie(
    store,
    Blake2b256::digest,
    new ClassicMptCommitmentScheme(Blake2b256::digest)
);
```

**Important**: For Cardano smart contracts, always use MPF mode with SecureTrie. Classic mode is provided only for compatibility with legacy off-chain MPT implementations.

## Key Concepts

### 1. Nibbles (4-bit values)

MPT operates on **nibbles** (half-bytes) rather than full bytes:

```
Byte: 0xAB = 171 in decimal
Nibbles: [A, B] = [10, 11] in decimal

Key "hello" (hex: 68656c6c6f)
Nibbles: [6, 8, 6, 5, 6, c, 6, c, 6, f]
```

**Why nibbles?** They provide 16-way branching, which is optimal for most datasets.

### 2. Hex-Prefix (HP) Encoding

HP encoding efficiently stores nibble paths in bytes:

```
HP Encoding Header:
- Bit 1 (value 2): Leaf node (1) or Extension node (0)
- Bit 0 (value 1): Odd length (1) or Even length (0)

Examples:
- HP([1,2,3,4], leaf=true)  → [0x20, 0x12, 0x34] (even length leaf)
- HP([1,2,3], leaf=true)    → [0x31, 0x23]       (odd length leaf)
- HP([1,2,3,4], leaf=false) → [0x00, 0x12, 0x34] (even length extension)
```

### 3. Cryptographic Hashing

Every node is hashed using **Blake2b-256**:

```
Node Data → CBOR Encoding → Blake2b-256 → 32-byte Hash
```

This creates a Merkle tree where the root hash represents the entire state.

## Node Types

The MPT uses three types of nodes:

### 1. Leaf Node 🍃

Stores the actual key-value pairs at the end of paths.

```
Structure: [HP-encoded key suffix, value]
Example: [HP([6,c,6,f]), "world"] for key "hello" → value "world"
```

### 2. Extension Node ➡️

Compresses long paths with no branching (path compression).

```
Structure: [HP-encoded path, child_hash]
Example: [HP([6,8,6,5]), hash_of_child] for common prefix "he"
```

### 3. Branch Node 🌳

Provides 16-way branching (one for each nibble value 0-F).

```
Structure: [child0, child1, ..., child15, value]
- child0-15: Hashes of child nodes (or null)
- value: Optional value if this node represents a complete key
```

## How It Works

Let's trace through building a trie with keys: "hello" → "world", "help" → "assist"

### Step 1: Insert "hello" → "world"

```
Key: "hello" = 68656c6c6f (hex) = [6,8,6,5,6,c,6,c,6,f] (nibbles)

Trie after insertion:
Root (LeafNode)
└── HP([6,8,6,5,6,c,6,c,6,f], "world")
```

### Step 2: Insert "help" → "assist"

```
Key: "help" = 68656c70 (hex) = [6,8,6,5,6,c,7,0] (nibbles)

Common prefix: [6,8,6,5,6,c] = "hel"
Divergence at position 6: 'l' vs 'p'

Trie after insertion:
Root (ExtensionNode)
├── HP([6,8,6,5,6,c]) → Branch Node
    ├── child[6] → LeafNode HP([6,f], "world")  # "lo"
    └── child[7] → LeafNode HP([0], "assist")   # "p"
```

### Visual Representation

```
                Root (Extension)
                HP([6,8,6,5,6,c])  // "hel"
                       │
                  Branch Node
                  /         \
            child[6]      child[7]
               │             │
          LeafNode       LeafNode
        HP([6,f],"world") HP([0],"assist")
           "lo"             "p"
```

## Implementation Details

### Core Classes

```java
// Main API
public class MerklePatriciaTrie {
    void put(byte[] key, byte[] value)
    byte[] get(byte[] key)
    void delete(byte[] key)
    List<Entry> scanByPrefix(byte[] prefix, int limit)
    byte[] getRootHash()
}

// Node types (package-private)
abstract class Node {
    abstract byte[] hash();
    abstract byte[] encode();
}

class LeafNode extends Node {
    byte[] hp;    // HP-encoded key suffix
    byte[] value; // Actual value
}

class ExtensionNode extends Node {
    byte[] hp;    // HP-encoded path
    byte[] next;  // Hash of child node
}

class BranchNode extends Node {
    byte[][] children; // 16 child hashes + optional value
}
```

### Storage Interface

```java
public interface NodeStore {
    byte[] get(byte[] hash);           // Load node by hash
    void put(byte[] hash, byte[] data); // Store encoded node
    void delete(byte[] hash);          // Remove node
}
```

### Type-Safe Wrappers (New in v0.6.0)

```java
// Type-safe hash wrapper
public class NodeHash {
    private final byte[32] bytes;
    
    public static NodeHash of(byte[] bytes);
    public byte[] getBytes();
    public String toHexString();
}

// Type-safe nibble path wrapper
public class NibblePath {
    private final int[] nibbles;
    
    public static NibblePath of(int... nibbles);
    public static NibblePath fromBytes(byte[] bytes);
    public NibblePath slice(int start, int end);
    public boolean startsWith(NibblePath prefix);
}

// Clean persistence abstraction
public class NodePersistence {
    public NodeHash persist(Node node);
    public Node load(NodeHash hash);
    public boolean exists(NodeHash hash);
}
```

## Code Examples

### Basic Usage with MPF (Recommended for Cardano)

```java
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.api.NodeStore;

// Create SecureTrie (MPF mode with hashed keys) - RECOMMENDED for Cardano
NodeStore store = new InMemoryNodeStore();
HashFunction hashFn = Blake2b256::digest;
SecureTrie trie = new SecureTrie(store, hashFn);

// Store key-value pairs (keys are automatically hashed)
trie.put("account:alice".getBytes(), "balance:1000".getBytes());
trie.put("account:bob".getBytes(), "balance:500".getBytes());
trie.put("contract:state".getBytes(), "active".getBytes());

// Retrieve values
byte[] value = trie.get("account:alice".getBytes());
System.out.println(new String(value)); // "balance:1000"

// Get cryptographic root hash (for on-chain verification)
byte[] rootHash = trie.getRootHash();
System.out.println("State root: " + bytesToHex(rootHash));

// Generate MPF-compatible proof (for Aiken verifier)
Optional<byte[]> proofWire = trie.getProofWire("account:alice".getBytes());
if (proofWire.isPresent()) {
    // Verify proof off-chain
    boolean valid = trie.verifyProofWire(
        rootHash,
        "account:alice".getBytes(),
        "balance:1000".getBytes(),
        true,  // inclusion proof
        proofWire.get()
    );
    System.out.println("Proof valid: " + valid);

    // Submit proofWire bytes to Cardano smart contract for on-chain verification
}
```

### Alternative: Direct MPT Usage (MPF Mode)

```java
// Direct MPT with MPF commitment scheme (default)
MerklePatriciaTrie mpfTrie = new MerklePatriciaTrie(store, Blake2b256::digest);

// Note: Use SecureTrie wrapper to ensure keys are hashed (MPF requirement)
// Otherwise, branch-terminal values may occur and cause root hash differences
mpfTrie.put("hello".getBytes(), "world".getBytes());
byte[] rootHash = mpfTrie.getRootHash();
```

### RocksDB Integration for Production

```java
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;

// Open RocksDB-backed node store
try (RocksDbNodeStore store = new RocksDbNodeStore("/var/data/cardano-state")) {
    SecureTrie trie = new SecureTrie(store, Blake2b256::digest);

    // Store Cardano account states
    trie.put("addr1:stake_key1".getBytes(), serializeAccountState(...));
    trie.put("addr2:stake_key2".getBytes(), serializeAccountState(...));

    // Get state root for block
    byte[] stateRoot = trie.getRootHash();

    // Later: reconstruct trie from persisted state
    SecureTrie restoredTrie = new SecureTrie(store, Blake2b256::digest, stateRoot);

    // Query historical state
    byte[] accountState = restoredTrie.get("addr1:stake_key1".getBytes());
}
```

### Working with Type-Safe Wrappers

```java
import com.bloxbean.cardano.statetrees.common.*;
import com.bloxbean.cardano.statetrees.mpt.NodePersistence;

// Type-safe hash operations
NodeHash hash = NodeHash.of(someHashBytes);
String hexString = hash.toHexString();
NodeHash parsed = NodeHash.fromHexString("abcd1234...");

// Type-safe nibble operations  
NibblePath path = NibblePath.fromBytes("hello".getBytes());
NibblePath prefix = path.prefix(6); // First 6 nibbles
boolean matches = path.startsWith(NibblePath.fromHexString("68656c"));

// Clean persistence operations
HashFunction hashFn = Blake2b256::digest;
CommitmentScheme commitments = new MpfCommitmentScheme(hashFn);
NodePersistence persistence = new NodePersistence(store, commitments, hashFn);
NodeHash nodeHash = persistence.persist(someNode);
Node loadedNode = persistence.load(nodeHash);
boolean exists = persistence.exists(nodeHash);
```

## RocksDB Integration

### Production Deployment with RocksDB

For production Cardano applications, use RocksDB for persistent, high-performance storage:

```java
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbNodeStore;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbRootsIndex;
import com.bloxbean.cardano.statetrees.rocksdb.RocksDbStateTrees;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;

// Option 1: Unified manager (recommended)
try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("/var/cardano/state")) {
    NodeStore nodeStore = stateTrees.nodeStore();
    RootsIndex rootsIndex = stateTrees.rootsIndex();

    // Create MPF trie
    SecureTrie trie = new SecureTrie(nodeStore, Blake2b256::digest);

    // Process block N
    trie.put("account:alice".getBytes(), blockNData);
    byte[] rootHash = trie.getRootHash();

    // Store root hash indexed by block height
    rootsIndex.put(blockHeight, rootHash);

    // Query historical state
    byte[] historicalRoot = rootsIndex.get(blockHeight - 100);
    SecureTrie historicalTrie = new SecureTrie(nodeStore, Blake2b256::digest, historicalRoot);
}

// Option 2: Separate node store
try (RocksDbNodeStore nodeStore = new RocksDbNodeStore("/var/cardano/nodes")) {
    SecureTrie trie = new SecureTrie(nodeStore, Blake2b256::digest);
    // Use trie...
}
```

### Batch Operations for Performance

```java
import com.bloxbean.cardano.statetrees.rocksdb.mpt.RocksDbMptSession;

try (RocksDbNodeStore nodeStore = new RocksDbNodeStore("/var/cardano/state")) {
    SecureTrie trie = new SecureTrie(nodeStore, Blake2b256::digest);

    // Configure batch options (optional)
    RocksDbMptSession.Options opts = RocksDbMptSession.Options.builder()
        .disableWal(false)  // Keep WAL enabled for durability
        .build();

    // Execute batched writes
    try (RocksDbMptSession session = RocksDbMptSession.of(nodeStore, opts)) {
        session.write(() -> {
            // All operations batched atomically
            trie.put("account:1".getBytes(), data1);
            trie.put("account:2".getBytes(), data2);
            trie.put("account:3".getBytes(), data3);
            return null;
        });
    }

    byte[] rootHash = trie.getRootHash();
}
```

### Garbage Collection

Reclaim storage from old states:

```java
import com.bloxbean.cardano.statetrees.rocksdb.gc.*;

try (RocksDbStateTrees stateTrees = new RocksDbStateTrees("/var/cardano/state")) {
    GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());

    // Keep last 1000 blocks
    RetentionPolicy policy = RetentionPolicy.keepLastVersions(1000);

    GcOptions options = GcOptions.builder()
        .setBatchSize(1000)
        .setReportProgress(true)
        .build();

    GcStrategy strategy = new RefcountGcStrategy();

    // Run garbage collection
    GcReport report = gcManager.runSync(strategy, policy, options);
    System.out.println("Deleted " + report.getDeletedNodeCount() + " nodes");
    System.out.println("Reclaimed " + report.getReclaimedBytes() + " bytes");
}
```

## Performance Characteristics

### Time Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|--------|
| Insert (SecureTrie) | O(k) | k = 32 bytes (hashed key) |
| Lookup | O(k) | Constant for 32-byte keys |
| Delete | O(k) | Includes tree compression |
| Prefix Scan | O(k + m) | m = number of results |
| Root Hash | O(1) | Cached at root level |
| Proof Generation | O(k) | Tree traversal with sibling collection |

### Space Complexity

- **Storage**: O(n) where n = number of unique key-value pairs
- **Memory**: O(k) for temporary objects during operations
- **Compression**: Path compression reduces internal nodes significantly
- **RocksDB overhead**: ~100-200 bytes per key-value (with compression)

### MPF Proof Size

MPF proofs are compact:
- **Average proof**: 1-3 KB for typical tree depths (20-30 levels)
- **Proof components**: Branch steps (16 hashes each) + leaf data
- **CBOR encoded**: Efficient binary serialization for on-chain verification

## Advanced Topics

### 1. MPF Proof Generation for On-Chain Verification

**Recommended for Cardano smart contracts** - Generate MPF-compatible proofs:

```java
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;

// Create MPF trie with hashed keys (required for Cardano)
SecureTrie trie = new SecureTrie(store, Blake2b256::digest);

// Insert data
trie.put("account:alice".getBytes(), "balance:1000".getBytes());
trie.put("account:bob".getBytes(), "balance:500".getBytes());

byte[] rootHash = trie.getRootHash();

// Generate MPF proof (CBOR-encoded, Aiken-compatible)
Optional<byte[]> proofWire = trie.getProofWire("account:alice".getBytes());
if (proofWire.isPresent()) {
    // Verify off-chain
    boolean valid = trie.verifyProofWire(
        rootHash,
        "account:alice".getBytes(),
        "balance:1000".getBytes(),
        true,  // inclusion proof
        proofWire.get()
    );

    // Submit to Cardano smart contract
    // The proofWire bytes can be passed directly to your Aiken MPF verifier
    submitToContract(rootHash, proofWire.get());
}
```

### 2. On-Chain Verification with Aiken

The MPF proof format is designed for efficient on-chain verification in Aiken:

```aiken
// Aiken validator example
use aiken/merkle_patricia_forestry as mpf

validator validate_state_proof(datum: Datum, redeemer: Redeemer, ctx: ScriptContext) {
  // Extract proof from redeemer
  let proof_bytes = redeemer.proof
  let state_root = datum.state_root
  let key = redeemer.key
  let value = redeemer.value

  // Verify MPF proof on-chain
  mpf.verify_inclusion(state_root, key, value, proof_bytes)
}
```

**Key Points**:
- Use `SecureTrie` to ensure keys are hashed (MPF requirement)
- Proof bytes are CBOR-encoded and optimized for size
- Direct compatibility with Aiken MPF verifier
- No additional transformation needed

### 3. Classic MPT Mode (Off-Chain Only)

For legacy off-chain MPT compatibility:

```java
import com.bloxbean.cardano.statetrees.mpt.commitment.ClassicMptCommitmentScheme;
import com.bloxbean.cardano.statetrees.api.ClassicProof;
import com.bloxbean.cardano.statetrees.mpt.ClassicProofVerifier;

// Classic mode - NOT for Cardano on-chain use
MerklePatriciaTrie classicTrie = new MerklePatriciaTrie(
    store,
    Blake2b256::digest,
    new ClassicMptCommitmentScheme(Blake2b256::digest)
);

classicTrie.put("key".getBytes(), "value".getBytes());

// Generate classic proof
byte[] wire = classicTrie.getProofWire("key".getBytes()).orElseThrow();
ClassicProof proof = ClassicProof.fromWire(wire);

// Verify classic proof
boolean valid = ClassicProofVerifier.verifyInclusion(
    classicTrie.getRootHash(),
    Blake2b256::digest,
    "key".getBytes(),
    "value".getBytes(),
    proof,
    new ClassicMptCommitmentScheme(Blake2b256::digest)
);
```

**Warning**: Classic mode is NOT compatible with Cardano MPF validators. Use only for off-chain legacy MPT applications.

### 4. Cardano State Management Example

Complete example for Cardano blockchain state:

```java
import com.bloxbean.cardano.statetrees.rocksdb.*;
import com.bloxbean.cardano.statetrees.mpt.SecureTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

public class CardanoStateManager {
    private final RocksDbStateTrees stateTrees;
    private final SecureTrie trie;

    public CardanoStateManager(String dbPath) throws Exception {
        this.stateTrees = new RocksDbStateTrees(dbPath);
        this.trie = new SecureTrie(stateTrees.nodeStore(), Blake2b256::digest);
    }

    // Process new block
    public byte[] processBlock(long blockHeight, Map<String, byte[]> stateUpdates) {
        // Apply state updates
        stateUpdates.forEach((key, value) ->
            trie.put(key.getBytes(), value)
        );

        // Get state root
        byte[] stateRoot = trie.getRootHash();

        // Store root indexed by block height
        stateTrees.rootsIndex().put(blockHeight, stateRoot);

        return stateRoot;
    }

    // Generate proof for smart contract
    public byte[] generateProof(String key) {
        return trie.getProofWire(key.getBytes()).orElse(null);
    }

    // Verify proof off-chain
    public boolean verifyProof(byte[] rootHash, String key, byte[] value, byte[] proof) {
        return trie.verifyProofWire(rootHash, key.getBytes(), value, true, proof);
    }

    // Query historical state
    public SecureTrie getHistoricalState(long blockHeight) {
        byte[] historicalRoot = stateTrees.rootsIndex().get(blockHeight);
        if (historicalRoot != null) {
            return new SecureTrie(stateTrees.nodeStore(), Blake2b256::digest, historicalRoot);
        }
        return null;
    }

    // Cleanup old states
    public void pruneOldBlocks(long keepLastN) {
        long currentBlock = stateTrees.rootsIndex().lastVersion();
        long pruneTarget = Math.max(0, currentBlock - keepLastN);

        if (pruneTarget > 0) {
            GcManager gcManager = new GcManager(
                stateTrees.nodeStore(),
                stateTrees.rootsIndex()
            );

            RetentionPolicy policy = RetentionPolicy.keepLastVersions((int) keepLastN);
            GcOptions options = GcOptions.builder().setBatchSize(1000).build();
            GcStrategy strategy = new RefcountGcStrategy();

            GcReport report = gcManager.runSync(strategy, policy, options);
            System.out.println("Pruned " + report.getDeletedNodeCount() + " nodes");
        }
    }

    public void close() throws Exception {
        stateTrees.close();
    }
}
```

## Troubleshooting

### Common Issues for Cardano Applications

**Q: MPF proof verification fails on-chain**
```java
// Solution: Ensure you're using SecureTrie (not plain MerklePatriciaTrie)
// SecureTrie hashes keys, which is required for MPF compatibility

// WRONG - may cause MPF verification failures
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, Blake2b256::digest);

// CORRECT - for Cardano/Aiken
SecureTrie trie = new SecureTrie(store, Blake2b256::digest);
```

**Q: Root hash doesn't match between off-chain and on-chain**
```java
// Ensure you're using MPF mode (default), not Classic mode
// Classic mode has different commitment scheme incompatible with Aiken

// WRONG for Cardano
MerklePatriciaTrie classic = new MerklePatriciaTrie(
    store, hashFn, new ClassicMptCommitmentScheme(hashFn)
);

// CORRECT for Cardano
SecureTrie mpf = new SecureTrie(store, hashFn);
```

**Q: Getting null for historical state queries**
```java
// Check that root hash exists for that block height
RootsIndex rootsIndex = stateTrees.rootsIndex();
byte[] historicalRoot = rootsIndex.get(blockHeight);

if (historicalRoot == null) {
    System.out.println("Block " + blockHeight + " not found or pruned");
} else {
    SecureTrie historical = new SecureTrie(store, hashFn, historicalRoot);
}
```

**Q: High storage usage**
```java
// Implement regular garbage collection
GcManager gcManager = new GcManager(nodeStore, rootsIndex);
RetentionPolicy policy = RetentionPolicy.keepLastVersions(1000);
GcReport report = gcManager.runSync(new RefcountGcStrategy(), policy, GcOptions.defaults());
System.out.println("Reclaimed " + report.getReclaimedBytes() / 1024 / 1024 + " MB");
```

**Q: Performance degradation with RocksDB**
```java
// Use batch operations for multiple updates
RocksDbMptSession.Options opts = RocksDbMptSession.Options.builder()
    .disableWal(false)  // Keep enabled for durability
    .build();

try (RocksDbMptSession session = RocksDbMptSession.of(nodeStore, opts)) {
    session.write(() -> {
        for (Map.Entry<String, byte[]> e : updates.entrySet()) {
            trie.put(e.getKey().getBytes(), e.getValue());
        }
        return null;
    });
}
```

## Further Reading

### Aiken and On-Chain Verification
- **Merkle Patricia Forestry**: [aiken-lang/merkle-patricia-forestry](https://github.com/aiken-lang/merkle-patricia-forestry)

### Standards and Specifications
- **Merkle Patricia Trees**: [Ethereum Yellow Paper](https://ethereum.github.io/yellowpaper/paper.pdf)

## Quick Reference

### For Cardano Applications (Recommended)

```java
// 1. Use SecureTrie for MPF compatibility
SecureTrie trie = new SecureTrie(store, Blake2b256::digest);

// 2. Store state with hashed keys
trie.put("account:alice".getBytes(), data);

// 3. Generate MPF proof for Aiken
byte[] proof = trie.getProofWire(key).orElse(null);

// 4. Use RocksDB for production
RocksDbNodeStore store = new RocksDbNodeStore("/var/cardano/state");

// 5. Implement garbage collection
GcManager gc = new GcManager(nodeStore, rootsIndex);
gc.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLastVersions(1000), options);
```

### Key Differences from JMT

| Aspect | MPT (MPF mode) | JMT |
|--------|---------------|-----|
| Use Case | State snapshots, on-chain proofs | Versioned state history |
| Versioning | Single version (snapshot-based) | Multi-version (MVCC) |
| Proof Target | Aiken smart contracts | Off-chain verification |
| Key Hashing | Required (SecureTrie) | Built-in (always hashed) |
| Rollback | Manual (new tree from old root) | Built-in (truncateAfter) |
| Storage | RocksDB (nodes + roots index) | RocksDB (nodes + values + roots + stale) |

**When to use MPT**: Cardano smart contracts requiring on-chain state proofs with Aiken MPF verifier

**When to use JMT**: Applications needing versioned state history, rollback support, or Diem-compatible proofs

## Contributing

When working with the MPT implementation for Cardano:

1. **Run tests**: `./gradlew :state-trees:test :state-trees-rocksdb:test`
2. **Test MPF compatibility**: Verify proofs work with Aiken MPF verifier
3. **Use SecureTrie**: Always use SecureTrie for Cardano applications
4. **Update docs**: Keep documentation current with changes
5. **Follow patterns**: Use MPF mode (default) unless explicitly requiring Classic

