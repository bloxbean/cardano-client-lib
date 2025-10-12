# Verified Structures Core

Common APIs, interfaces, and utilities for verified data structures.

## Overview

This module provides the foundational abstractions and utilities used by all verified data structure implementations (MPT, JMT, etc.). It defines storage interfaces, hashing utilities, nibble path manipulation, and common data types.

## Key Features

- **Storage Abstraction** - `NodeStore` interface for pluggable backends
- **Hash Functions** - Generic `HashFunction` interface with Blake2b-256 implementation
- **Nibble Path Utilities** - Efficient nibble (4-bit) manipulation for Merkle tries
- **Common Data Types** - Reusable types like `NodeHash`, `StorageMode`, `RootsIndex`
- **Proof Interfaces** - Generic proof structures for cryptographic verification

## Core Interfaces

### NodeStore

Storage abstraction for Merkle trie nodes.

```java
public interface NodeStore {
    byte[] get(byte[] hash);           // Retrieve node by hash
    void put(byte[] hash, byte[] node); // Store node
    void delete(byte[] hash);           // Delete node (GC)
}
```

**Implementations:**
- `RocksDbNodeStore` (in merkle-patricia-trie-rocksdb)
- `RdbmsNodeStore` (in merkle-patricia-trie-rdbms)
- `InMemoryNodeStore` (for testing)

### HashFunction

Generic hash function interface for computing digests.

```java
@FunctionalInterface
public interface HashFunction {
    byte[] digest(byte[] data);
}
```

**Usage:**
```java
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

HashFunction hashFn = Blake2b256::digest;
byte[] hash = hashFn.digest("hello world".getBytes());
```

### StateTrees

Multi-tree storage interface for managing multiple independent tries.

```java
public interface StateTrees extends AutoCloseable {
    MerklePatriciaTrie getTree(String name);
    byte[] getRootHash(String name);
    void commit(String name);
}
```

### StorageMode

Enum defining storage strategy.

```java
public enum StorageMode {
    SINGLE_VERSION,  // Latest state only (space-efficient)
    MULTI_VERSION    // Full history (supports rollback)
}
```

## Utility Classes

### NibblePath

Represents a path in a Merkle trie as a sequence of nibbles (4-bit values).

```java
// Create from nibbles
NibblePath path = NibblePath.of(new int[]{1, 2, 3, 4});

// Create from byte array (converts to nibbles)
NibblePath path = NibblePath.fromBytes(keyBytes);

// Operations
int length = path.length();
int nibble = path.get(index);
NibblePath prefix = path.slice(0, 4);
NibblePath concat = path1.concat(path2);
```

### Nibbles

Static utility for nibble manipulation.

```java
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

// Convert bytes to nibbles (each byte becomes 2 nibbles)
int[] nibbles = Nibbles.toNibbles(new byte[]{(byte) 0xAB, (byte) 0xCD});
// Result: [10, 11, 12, 13]

// Convert nibbles back to bytes
byte[] bytes = Nibbles.toBytes(new int[]{10, 11, 12, 13});
// Result: [0xAB, 0xCD]
```

### Bytes

Utility methods for byte array operations.

```java
import com.bloxbean.cardano.vds.core.util.Bytes;

// Hex encoding/decoding
String hex = Bytes.toHexString(bytes);
byte[] bytes = Bytes.fromHexString("deadbeef");

// Comparison
boolean equal = Bytes.equals(bytes1, bytes2);
int cmp = Bytes.compare(bytes1, bytes2);

// Concatenation
byte[] combined = Bytes.concat(bytes1, bytes2, bytes3);
```

### VarInts

Variable-length integer encoding (used in CBOR serialization).

```java
import com.bloxbean.cardano.vds.core.util.VarInts;

// Encode integer to variable-length bytes
byte[] encoded = VarInts.encode(12345);

// Decode
int value = VarInts.decode(encoded);
```

## Common Data Types

### NodeHash

Type-safe wrapper for node hashes.

```java
NodeHash hash = NodeHash.of(hashBytes);
byte[] bytes = hash.bytes();
String hex = hash.toHexString();
```

### RootsIndex

Interface for storing and retrieving root hashes by name or version.

```java
public interface RootsIndex {
    byte[] getRootHash(String name);
    void putRootHash(String name, byte[] hash);
    byte[] getRootHashAtVersion(String name, long version);
}
```

### ClassicProof

Generic proof structure for inclusion/exclusion verification.

```java
public interface ClassicProof {
    List<byte[]> nodes();           // Proof path nodes
    byte[] key();                   // Key being proved
    byte[] value();                 // Value (null for exclusion)
    boolean isInclusion();          // Inclusion vs exclusion
}
```

## Usage Examples

### Basic Storage Pattern

```java
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;

// Create a simple in-memory node store
class InMemoryNodeStore implements NodeStore {
    private final Map<ByteArrayWrapper, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public byte[] get(byte[] hash) {
        return storage.get(new ByteArrayWrapper(hash));
    }

    @Override
    public void put(byte[] hash, byte[] nodeBytes) {
        storage.put(new ByteArrayWrapper(hash), nodeBytes);
    }

    @Override
    public void delete(byte[] hash) {
        storage.remove(new ByteArrayWrapper(hash));
    }
}

// Use it
NodeStore store = new InMemoryNodeStore();
HashFunction hashFn = Blake2b256::digest;
```

### Nibble Path Manipulation

```java
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

// Convert key to nibble path
byte[] key = "account123".getBytes();
int[] nibbles = Nibbles.toNibbles(key);
NibblePath path = NibblePath.of(nibbles);

// Path operations
NibblePath prefix = path.slice(0, 4);          // First 4 nibbles
NibblePath suffix = path.slice(4, path.length()); // Remaining
NibblePath combined = prefix.concat(suffix);    // Reconstruct

// Check prefix matching
boolean matches = path.startsWith(prefix);
```

### Custom Hash Function

```java
import com.bloxbean.cardano.vds.core.api.HashFunction;
import java.security.MessageDigest;

// SHA-256 hash function
HashFunction sha256 = data -> {
    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data);
    } catch (Exception e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
};

// Use in trie
MerklePatriciaTrie trie = new MerklePatriciaTrie(store, sha256);
```

## Gradle Dependency

```gradle
dependencies {
    api 'com.bloxbean.cardano:verified-structures-core:0.8.0'

    // Required for Blake2b
    implementation 'com.bloxbean.cardano:crypto:0.8.0'
}
```

## Package Structure

```
com.bloxbean.cardano.vds.core
├── api/                    # Core interfaces
│   ├── NodeStore.java
│   ├── HashFunction.java
│   ├── StateTrees.java
│   ├── StorageMode.java
│   ├── RootsIndex.java
│   └── ClassicProof.java
├── hash/                   # Hash implementations
│   └── Blake2b256.java
├── nibbles/                # Nibble utilities
│   └── Nibbles.java
├── util/                   # General utilities
│   ├── Bytes.java
│   └── VarInts.java
├── NibblePath.java         # Nibble path type
└── NodeHash.java           # Hash wrapper
```

## Design Documentation

See [../docs/design-core.md](../docs/design-core.md) for detailed architecture and design decisions.

## Thread Safety

- All utility classes (`Nibbles`, `Bytes`, `VarInts`) are thread-safe (stateless)
- `NibblePath` and `NodeHash` are immutable and thread-safe
- `NodeStore` implementations may or may not be thread-safe (check specific implementations)

## Performance Considerations

- **NibblePath**: Uses copy-on-write for immutability; minimize slice/concat operations
- **Nibbles.toNibbles()**: Creates new array; cache results when possible
- **Blake2b256**: Native implementation via crypto module; fast but not zero-cost

## Related Modules

- [merkle-patricia-trie](../merkle-patricia-trie/) - MPT implementation
- [jellyfish-merkle](../jellyfish-merkle/) - JMT implementation
- [rocksdb-core](../rocksdb-core/) - RocksDB utilities
- [rdbms-core](../rdbms-core/) - RDBMS utilities
