# Merkle Patricia Trie Design

**Module:** `merkle-patricia-trie`

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Node Types](#node-types)
3. [Algorithms](#algorithms)
4. [Proof Generation](#proof-generation)
5. [HP Encoding](#hp-encoding)
6. [Design Decisions](#design-decisions)
7. [Performance Characteristics](#performance-characteristics)
8. [References](#references)

---

## 1. Architecture Overview

### 1.1 What is a Merkle Patricia Trie?

A Merkle Patricia Trie (MPT) is a cryptographically authenticated data structure that combines:
- **Merkle Tree:** Each node hash cryptographically commits to its subtree
- **Patricia Trie:** Path compression reduces storage for sparse key spaces
- **Radix-16 (Hexary):** 16-way branching using nibbles (4-bit values)

**Use Cases:**
- Ethereum world state (account balances, storage)
- Cardano Merkle Patricia Forestry (on-chain verification)
- Authenticated dictionaries (verify key-value pairs)

### 1.2 High-Level Structure

```
Root (Branch)
   ├─[0]: null
   ├─[1]: null
   ├─[2]: Extension([3,4,5])
   │       └─ Branch
   │          ├─[0]: Leaf([6,7], "value1")
   │          └─[1]: Leaf([8,9], "value2")
   ├─[3..E]: null
   └─[F]: Leaf([0,1,2], "value3")
```

**Properties:**
- **Deterministic:** Same key-value set always produces same root hash
- **Efficient:** Path compression reduces node count for sparse keys
- **Verifiable:** Proofs allow third parties to verify inclusion/exclusion
- **Immutable:** Nodes never modified (copy-on-write updates)

### 1.3 Architecture Diagram

```
┌──────────────────────────────────────────────────────┐
│              MerklePatriciaTrie                      │
│  ┌────────────────────────────────────────────────┐ │
│  │  put(key, value)                               │ │
│  │    → Hash key (optional: SecureTrie)           │ │
│  │    → Convert to nibbles                        │ │
│  │    → Navigate to insertion point               │ │
│  │    → Create/update nodes (PutOperationVisitor) │ │
│  │    → Recompute path hashes to root             │ │
│  └────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────┐ │
│  │  get(key)                                      │ │
│  │    → Hash key (if SecureTrie)                  │ │
│  │    → Navigate trie following nibbles           │ │
│  │    → Return value from leaf (GetOperationVisitor)│ │
│  └────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────┐
│              Node Storage Layer                      │
│  (NodeStore: RocksDB, RDBMS, InMemory)              │
└──────────────────────────────────────────────────────┘
```

---

## 2. Node Types

### 2.1 Leaf Node

**Purpose:** Store terminal key-value pairs

**Structure:**
```java
final class LeafNode {
    byte[] hp;     // HP-encoded key suffix (with isLeaf=true)
    byte[] value;  // The actual value data
}
```

**CBOR Encoding:**
```
[HP-encoded key suffix, value]
```

**Example:**
```
Key: "hello" (hex: 68656c6c6f)
Nibbles: [6,8,6,5,6,c,6,c,6,f]
If leaf at nibble path [6,8]:
  HP: [3, 6, 5, 6, c, 6, c, 6, f]  // 0x20 prefix for odd-length leaf
  Value: "world" (as bytes)
```

**Hash Computation (MPF):**
```
H(path_suffix || value_hash)
```

### 2.2 Branch Node

**Purpose:** Provide 16-way branching at a fork point

**Structure:**
```java
final class BranchNode {
    byte[][] children = new byte[16][];  // 16 child hashes (null if absent)
    byte[] value;                        // Optional value at this node
}
```

**CBOR Encoding:**
```
[child[0], child[1], ..., child[15], value]
```

**Example:**
```
Branch at root:
  [0]: null
  [1]: null
  [2]: <hash of Extension node>
  [3..E]: null
  [F]: <hash of Leaf node>
  value: null (no value at this branch)
```

**Hash Computation (MPF):**
```
H(child_hashes[0] || ... || child_hashes[15] || value_hash)
```

**Optimization:** Empty children represented as empty byte arrays (not null) in CBOR

### 2.3 Extension Node

**Purpose:** Compress long paths with no branching

**Structure:**
```java
final class ExtensionNode {
    byte[] hp;     // HP-encoded path (with isLeaf=false)
    byte[] child;  // Hash of single child node
}
```

**CBOR Encoding:**
```
[HP-encoded path, child hash]
```

**Example:**
```
If keys [abc...], [abd...], [abe...] share prefix [a,b]:
  Extension([a,b]) → Branch
                      ├─[c]: Leaf(...)
                      ├─[d]: Leaf(...)
                      └─[e]: Leaf(...)
```

**Hash Computation (MPF):**
```
H(path || child_hash)
```

**Design Note:** Extension nodes are automatically created/removed during insert/delete to maintain optimal compression.

---

## 3. Algorithms

### 3.1 Insertion Algorithm

**High-Level:**
```
INSERT(key, value):
1. Convert key to nibbles (hash if SecureTrie)
2. Start at root node
3. Navigate to insertion point:
   - Branch: Follow nibble at current depth
   - Extension: Match prefix, split if necessary
   - Leaf: Check if same key (update) or different (split)
   - Null: Create new leaf
4. Create new nodes along path (copy-on-write)
5. Recompute hashes from leaf to root
6. Store new nodes in NodeStore
7. Return new root hash
```

**Detailed Algorithm (Pseudo-code):**
```python
def insert(node, key_nibbles, depth, value):
    if node is NULL:
        # Empty position: create leaf
        hp = pack_hp(key_nibbles[depth:], is_leaf=True)
        leaf = LeafNode(hp, value)
        store.put(hash(leaf), encode(leaf))
        return hash(leaf)

    elif node is LeafNode:
        # Hit existing leaf
        existing_nibbles = unpack_hp(node.hp).nibbles
        if existing_nibbles == key_nibbles[depth:]:
            # Same key: update value
            new_leaf = LeafNode(node.hp, value)
            store.put(hash(new_leaf), encode(new_leaf))
            return hash(new_leaf)
        else:
            # Different key: split into branch
            return split_into_branch(node, key_nibbles, depth, value)

    elif node is ExtensionNode:
        # Traverse extension
        ext_nibbles = unpack_hp(node.hp).nibbles
        common_len = common_prefix_length(ext_nibbles, key_nibbles[depth:])

        if common_len == len(ext_nibbles):
            # Full match: recurse into child
            child_hash = insert(load(node.child), key_nibbles, depth + common_len, value)
            new_ext = ExtensionNode(node.hp, child_hash)
            store.put(hash(new_ext), encode(new_ext))
            return hash(new_ext)
        else:
            # Partial match: split extension
            return split_extension(node, common_len, key_nibbles, depth, value)

    elif node is BranchNode:
        # Navigate branch
        nibble = key_nibbles[depth]
        child_hash = node.children[nibble]

        if child_hash is NULL:
            # Empty slot: create leaf
            remaining = key_nibbles[depth + 1:]
            hp = pack_hp(remaining, is_leaf=True)
            leaf = LeafNode(hp, value)
            store.put(hash(leaf), encode(leaf))

            # Update branch with new child
            new_branch = BranchNode(node.children, node.value)
            new_branch.children[nibble] = hash(leaf)
            store.put(hash(new_branch), encode(new_branch))
            return hash(new_branch)
        else:
            # Recurse into child
            new_child_hash = insert(load(child_hash), key_nibbles, depth + 1, value)

            # Update branch
            new_branch = BranchNode(node.children, node.value)
            new_branch.children[nibble] = new_child_hash
            store.put(hash(new_branch), encode(new_branch))
            return hash(new_branch)
```

**Complexity:**
- Time: O(k) where k = key length in nibbles (typically 64 for 32-byte keys)
- Space: O(k) new nodes created (one per level on path)
- Storage writes: O(k) node puts

### 3.2 Lookup Algorithm

**High-Level:**
```
GET(key):
1. Convert key to nibbles (hash if SecureTrie)
2. Start at root node
3. Navigate trie:
   - Branch: Follow nibble at current depth
   - Extension: Match prefix, continue if full match
   - Leaf: Check if key matches, return value
   - Null: Key not found
4. Return value or null
```

**Detailed Algorithm:**
```python
def get(node, key_nibbles, depth):
    if node is NULL:
        return NULL  # Key not found

    elif node is LeafNode:
        existing_nibbles = unpack_hp(node.hp).nibbles
        if existing_nibbles == key_nibbles[depth:]:
            return node.value
        else:
            return NULL  # Different key

    elif node is ExtensionNode:
        ext_nibbles = unpack_hp(node.hp).nibbles
        if key_nibbles[depth:depth+len(ext_nibbles)] == ext_nibbles:
            # Prefix matches: continue
            return get(load(node.child), key_nibbles, depth + len(ext_nibbles))
        else:
            return NULL  # Prefix mismatch

    elif node is BranchNode:
        nibble = key_nibbles[depth]
        child_hash = node.children[nibble]
        if child_hash is NULL:
            return NULL  # Path doesn't exist
        else:
            return get(load(child_hash), key_nibbles, depth + 1)
```

**Complexity:**
- Time: O(k) where k = key length
- Storage reads: O(d) where d = depth (~20-30 for typical trees)

### 3.3 Deletion Algorithm

**High-Level:**
```
DELETE(key):
1. Convert key to nibbles
2. Navigate to leaf node
3. Remove leaf
4. Compress path (remove unnecessary branches/extensions):
   - Branch with 1 child + no value → convert to extension
   - Extension → Extension → merge into single extension
5. Recompute hashes to root
6. Mark old nodes as stale (for GC)
```

**Compression Rules:**
```
1. Branch with 0 children + no value → Delete
2. Branch with 1 child + no value → Replace with Extension
3. Branch with 1 child + value → Keep as Branch
4. Extension → Leaf → Collapse (leaf absorbs extension path)
5. Extension → Extension → Merge paths
```

**Complexity:**
- Time: O(k)
- Space: O(k) new nodes
- Compression: O(d) parent traversal

---

## 4. Proof Generation

### 4.1 Classic MPT Proof

**Purpose:** Prove key-value inclusion or exclusion

**Proof Structure:**
```
Proof = [
    node_1,  // Root node
    node_2,  // Child on path
    ...
    node_n   // Terminal (leaf or branch)
]
```

**Verification Algorithm:**
```python
def verify(root_hash, key, value, proof):
    nibbles = to_nibbles(key)
    depth = 0
    computed_hash = root_hash

    for node in proof:
        if hash(node) != computed_hash:
            return False  # Hash mismatch

        if node is LeafNode:
            # Terminal: check key and value
            leaf_nibbles = unpack_hp(node.hp).nibbles
            return (leaf_nibbles == nibbles[depth:] and node.value == value)

        elif node is ExtensionNode:
            ext_nibbles = unpack_hp(node.hp).nibbles
            if nibbles[depth:depth+len(ext_nibbles)] != ext_nibbles:
                return False  # Prefix mismatch
            depth += len(ext_nibbles)
            computed_hash = node.child

        elif node is BranchNode:
            nibble = nibbles[depth]
            computed_hash = node.children[nibble]
            if computed_hash is NULL:
                return False  # Path doesn't exist
            depth += 1

    return False  # Proof incomplete
```

**Proof Size:**
- Average: 2-3 KB for 32-byte keys
- Worst case: 6-8 KB (deep tree)
- Components: ~30-40 nodes × 70 bytes/node (avg)

### 4.2 Non-Inclusion Proof

**Two Cases:**

1. **Path Doesn't Exist:**
   - Proof ends at branch with null child
   - Verification: Check that child at nibble is null

2. **Different Leaf:**
   - Proof ends at leaf with different key
   - Verification: Check that leaf key ≠ target key

**Example (Different Leaf):**
```
Query: key "abc"
Proof: [Root Branch, Extension, Leaf("abd", val)]
Verification: "abc" ≠ "abd" → non-inclusion proven
```

---

## 5. HP Encoding

### 5.1 Format Specification

**Purpose:** Compact representation of nibble paths with leaf/extension distinction

**Encoding:**
```
┌─────────────────────────────────────────────────────┐
│  Byte 0:  [f f l l] [n3 n2 n1 n0]                 │
│            ─┬─ ─┬─   ─────┬─────                   │
│             │   │          └─ First nibble (odd)    │
│             │   └─ Leaf/Extension flag              │
│             └─ Padding flags                        │
│                                                      │
│  f f = 0 0  → Even length, no first nibble          │
│  f f = 0 1  → Odd length, first nibble in byte 0    │
│  l l = 0 0  → Extension node                        │
│  l l = 1 0  → Leaf node                             │
│                                                      │
│  Byte 1+: Remaining nibbles packed as bytes         │
└─────────────────────────────────────────────────────┘
```

**Examples:**
```
Nibbles: [1,2,3,4,5] (odd), Leaf
HP: [0x31, 0x23, 0x45]
       ││   ││   ││
       ││   ││   └─ nibbles [4,5]
       ││   └─ nibbles [2,3]
       │└─ first nibble [1]
       └─ flags: 0x30 = 0011 0000 (odd + leaf)

Nibbles: [1,2,3,4] (even), Extension
HP: [0x00, 0x12, 0x34]
       ││   ││   ││
       ││   ││   └─ nibbles [3,4]
       ││   └─ nibbles [1,2]
       │└─ no first nibble
       └─ flags: 0x00 = 0000 0000 (even + extension)
```

### 5.2 Packing Algorithm

```python
def pack_hp(nibbles, is_leaf):
    flags = 0x20 if is_leaf else 0x00
    odd = len(nibbles) % 2 == 1

    if odd:
        flags |= 0x10
        flags |= nibbles[0]
        packed = [flags]
        for i in range(1, len(nibbles), 2):
            packed.append((nibbles[i] << 4) | nibbles[i+1])
    else:
        packed = [flags]
        for i in range(0, len(nibbles), 2):
            packed.append((nibbles[i] << 4) | nibbles[i+1])

    return bytes(packed)
```

### 5.3 Unpacking Algorithm

```python
def unpack_hp(hp):
    flags = hp[0]
    is_leaf = (flags & 0x20) != 0
    odd = (flags & 0x10) != 0

    nibbles = []
    if odd:
        nibbles.append(flags & 0x0F)

    for byte in hp[1:]:
        nibbles.append((byte >> 4) & 0x0F)
        nibbles.append(byte & 0x0F)

    return HP(nibbles, is_leaf)
```

**Efficiency:**
- Space: ~50% saving vs raw nibble array
- Time: O(n) unpack, amortized by node caching

---

## 6. Design Decisions

### 6.1 Why 16-Way Branching?

**Alternatives:**

| Branching | Depth (32-byte key) | Node Size | Decision |
|-----------|---------------------|-----------|----------|
| Binary (2) | 256 levels | 16 bytes | ❌ Too deep |
| Hex (16) | 64 levels | ~550 bytes | ✅ **Chosen** |
| 256-way | 32 levels | ~8 KB | ❌ Too large |

**Rationale:**
- 16-way provides good balance: depth = 2 × key bytes
- Branch nodes fit in cache lines (512-1024 bytes)
- Hex representation (nibbles) is human-readable

### 6.2 Why Copy-on-Write?

**Problem:** Updating nodes in-place breaks immutability

**Solution:** Create new nodes, never modify existing

**Benefits:**
1. **Immutability:** Old roots remain valid (historical state)
2. **Concurrency:** Multiple readers don't need locks
3. **Versioning:** Each update creates new version
4. **Safety:** No aliasing bugs

**Cost:**
- More storage (need GC to reclaim old nodes)
- More allocations (transient objects)

**Trade-off:** Acceptable for safety and versioning benefits

### 6.3 Why SecureTrie (Hashed Keys)?

**Problem:** Malicious actors can craft keys to create deep trees (DOS attack)

**Solution:** Hash keys before insertion

**Benefits:**
- Uniform key distribution (balanced tree)
- Predictable depth (always 64 nibbles for 32-byte hash)
- DOS resistance

**Cost:**
- Extra hash computation (~1 µs)
- Cannot do prefix queries
- Lost key ordering

**When to Use:**
- **SecureTrie:** Untrusted input, need DOS protection (Ethereum accounts)
- **Plain MPT:** Trusted input, need prefix queries (Cardano metadata)

---
