# Merkle Patricia Trie (MPT) Implementation Guide

A comprehensive guide to understanding the Merkle Patricia Trie implementation in the state-trees module.

## Table of Contents

- [What is a Merkle Patricia Trie?](#what-is-a-merkle-patricia-trie)
- [Why Use MPT?](#why-use-mpt)
- [Key Concepts](#key-concepts)
- [Node Types](#node-types)
- [How It Works](#how-it-works)
- [Implementation Details](#implementation-details)
- [Code Examples](#code-examples)
- [Performance Characteristics](#performance-characteristics)
- [Advanced Topics](#advanced-topics)

## What is a Merkle Patricia Trie?

A **Merkle Patricia Trie (MPT)** is a cryptographically authenticated data structure that combines three important concepts:

1. **Patricia Trie**: A compressed trie (prefix tree) for efficient storage
2. **Merkle Tree**: Cryptographic hashing for data integrity
3. **Radix Tree**: Base-16 (hexadecimal) branching for optimal performance

Think of it as a **smart dictionary** where:
- Keys can be looked up efficiently
- The entire structure has a cryptographic "fingerprint" (root hash)
- Any change in data creates a different fingerprint
- Storage is optimized through path compression

## Why Use MPT?

### 🔍 **Efficient Lookups**
- Find any key-value pair in O(log n) time
- Support for prefix-based searches
- Optimized for blockchain-style workloads

### 🔒 **Cryptographic Security**
- Every node has a cryptographic hash
- Root hash represents the entire state
- Impossible to modify without detection

### 💾 **Storage Efficiency**
- Path compression reduces storage overhead
- Only stores necessary intermediate nodes
- Persistent data structure (shares common subtrees)

### ⚡ **Blockchain Optimized**
- Used in Ethereum, Cardano, and other blockchains
- Supports state root verification
- Enables efficient state synchronization

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

### Basic Usage

```java
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

// Create a trie with in-memory storage
NodeStore store = new TestNodeStore();
HashFunction hashFn = Blake2b256::digest;
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);

// Store key-value pairs
trie.put("hello".getBytes(), "world".getBytes());
trie.put("help".getBytes(), "assist".getBytes());
trie.put("hero".getBytes(), "champion".getBytes());

// Retrieve values
byte[] value = trie.get("hello".getBytes());
System.out.println(new String(value)); // "world"

// Get cryptographic root hash
byte[] rootHash = trie.getRootHash();
System.out.println("Root hash: " + bytesToHex(rootHash));

// Prefix scanning
List<MerklePatriciaTrie.Entry> results = trie.scanByPrefix("he".getBytes(), 10);
for (var entry : results) {
    System.out.println(new String(entry.getKey()) + " → " + new String(entry.getValue()));
}
// Output:
// hello → world
// help → assist
// hero → champion
```

### State Persistence

```java
// Build initial state
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);
trie.put("account1".getBytes(), "balance100".getBytes());
trie.put("account2".getBytes(), "balance200".getBytes());

// Get state root
byte[] stateRoot = trie.getRootHash();

// Later: reconstruct trie from state root
MerklePatriciaTrie restoredTrie = new MerklePatriciaTrie(store, hashFn, stateRoot);

// Verify state integrity
byte[] balance = restoredTrie.get("account1".getBytes());
assert Arrays.equals(balance, "balance100".getBytes());
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
NodePersistence persistence = new NodePersistence(store);
NodeHash nodeHash = persistence.persist(someNode);
Node loadedNode = persistence.load(nodeHash);
boolean exists = persistence.exists(nodeHash);
```

## Performance Characteristics

### Time Complexity

| Operation | Time Complexity | Notes |
|-----------|----------------|--------|
| Insert | O(k) | k = key length (typically 20-32 bytes) |
| Lookup | O(k) | Constant for practical key sizes |
| Delete | O(k) | Includes tree compression |
| Prefix Scan | O(k + m) | m = number of results |
| Root Hash | O(1) | Cached at root level |

### Space Complexity

- **Storage**: O(n) where n = number of unique key-value pairs
- **Memory**: O(k) for temporary objects during operations
- **Compression**: Path compression reduces internal nodes significantly

### Benchmark Results

Recent performance measurements (reference):

```
Sequential Inserts (ops/sec):
- 100 items:  ~1,000 ops/sec
- 1,000 items:   ~70 ops/sec  
- 5,000 items:   ~11 ops/sec

Random Reads: ~50,000 ops/sec (from populated trie)
Mixed Workload: ~500 ops/sec (60% reads, 30% writes, 10% deletes)
```

## Advanced Topics

### 1. Secure Trie Wrapper

For additional security, keys can be hashed before storage:

```java
SecureTrie secureTrie = new SecureTrie(normalTrie, Blake2b256::digest);

// Keys are hashed automatically
secureTrie.put("plaintext-key".getBytes(), "value".getBytes());
// Internally stores: put(hash("plaintext-key"), "value")
```

### 2. Proof Generation

While not yet implemented, MPT supports **Merkle proofs**:

```
Proof: Path from leaf to root with all intermediate hashes
Verification: Recompute root hash from proof
Use case: Light clients can verify specific values without full state
```

### 3. State Synchronization

MPT enables efficient state sync:

```
1. Compare root hashes
2. If different, request differing subtree hashes
3. Download only changed nodes
4. Verify against known root hash
```

### 4. Garbage Collection

Optimize storage by removing unused nodes:

```java
// Pseudocode for GC
Set<NodeHash> reachableNodes = traverseFromRoot(currentRoot);
Set<NodeHash> allNodes = store.getAllNodes();
Set<NodeHash> unreachableNodes = allNodes - reachableNodes;

for (NodeHash hash : unreachableNodes) {
    store.delete(hash);
}
```

## Troubleshooting

### Common Issues

**Q: Getting null when value should exist**
```java
// Check key encoding
byte[] key = "test".getBytes(StandardCharsets.UTF_8);
trie.put(key, value);
// Must use same encoding for retrieval
byte[] result = trie.get("test".getBytes(StandardCharsets.UTF_8));
```

**Q: Root hash changes unexpectedly**
```java
// Any modification changes root hash - this is expected
byte[] hash1 = trie.getRootHash();
trie.put("new".getBytes(), "value".getBytes());
byte[] hash2 = trie.getRootHash();
// hash1 != hash2 (this is correct behavior)
```

**Q: Performance seems slow**
```java
// Consider batching operations
for (int i = 0; i < 1000; i++) {
    trie.put(("key" + i).getBytes(), ("value" + i).getBytes());
}
// Get hash only once at the end
byte[] finalHash = trie.getRootHash();
```

### Debugging Tools

```java
// Enable detailed logging (if implemented)
System.setProperty("mpt.debug", "true");

// Inspect trie structure
void printTrieStructure(MerklePatriciaTrie trie) {
    // Custom traversal logic here
    // Useful for understanding tree shape
}

// Benchmark specific operations
// See BENCHMARKING.md for JMH setup
```

## Further Reading

- **Original Paper**: [Merkle Patricia Trees](https://ethereum.github.io/yellowpaper/paper.pdf) (Ethereum Yellow Paper)
- **CBOR Encoding**: [RFC 7049](https://tools.ietf.org/html/rfc7049)
- **Blake2b Hashing**: [RFC 7693](https://tools.ietf.org/html/rfc7693)
- **Benchmarking**: See `BENCHMARKING.md` in this directory

## Contributing

When working with the MPT implementation:

1. **Run tests**: `./gradlew :state-trees:test`
2. **Check performance**: `./gradlew :state-trees:jmh`
3. **Follow patterns**: Use type-safe wrappers for new code
4. **Update docs**: Keep this README current with changes

---

*This implementation is optimized for Cardano blockchain use cases but is general-purpose enough for other applications requiring cryptographically authenticated key-value storage.*