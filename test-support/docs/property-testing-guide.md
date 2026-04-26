# test-support: Property-Based Testing Guide

The `test-support` module provides [jqwik](https://jqwik.net/) arbitraries (random data generators) for writing property-based tests against Cardano Client Library modules. Instead of hand-picking a few examples, property tests let jqwik generate hundreds of random inputs and automatically shrink failures to minimal reproducers.

## Setup

### 1. Add the dependency

In the consuming module's `build.gradle`:

```gradle
dependencies {
    testImplementation project(':test-support')
}
```

This transitively brings in:
- `net.jqwik:jqwik:1.9.3` (property-based testing engine)
- `:common` (HexUtil, etc.)
- `:crypto` (Blake2bUtil)

No additional jqwik dependency declaration is needed.

`MpfArbitraries` is available in the same module, but it is an optional verified-structures helper. If a test imports `MpfArbitraries`, `HashFunction`, `NodeStore`, or MPF trie classes, add the relevant verified-structures module explicitly:

```gradle
dependencies {
    testImplementation project(':test-support')
    testImplementation project(':verified-structures:verified-structures-core')
    testImplementation project(':verified-structures:merkle-patricia-forestry')
}
```

Consumers that only use `CardanoArbitraries`, `ByteArrayWrapper`, or other non-verified-structures utilities do not need verified-structures dependencies on their test classpath.

### 2. Verify the test runner

The root `build.gradle` already configures `useJUnitJupiter()` for all subprojects, and jqwik integrates with JUnit Platform. No extra configuration is required.

## Module Contents

### CardanoArbitraries

General-purpose generators for Cardano data types.

| Method | Returns | Description |
|--------|---------|-------------|
| `bytes(int size)` | `Arbitrary<byte[]>` | Fixed-size random byte array |
| `bytesRange(int min, int max)` | `Arbitrary<byte[]>` | Random byte array with size in [min, max] |
| `hashes()` | `Arbitrary<byte[]>` | 32-byte hash digests |
| `policyIds()` | `Arbitrary<byte[]>` | 28-byte policy IDs |
| `assetNames()` | `Arbitrary<byte[]>` | 0-32 byte asset names |
| `lovelaceAmounts()` | `Arbitrary<BigInteger>` | 0 to 45 quadrillion lovelace |
| `hexStrings(int byteLen)` | `Arbitrary<String>` | Hex-encoded string of given byte length |
| `keyValues()` | `Arbitrary<Map.Entry<byte[], byte[]>>` | Key (1-64 bytes) + value (1-256 bytes) pair |
| `keyValueMaps(int min, int max)` | `Arbitrary<Map<ByteArrayWrapper, byte[]>>` | Deduplicated map of key-value pairs |

### MpfArbitraries

Generators specific to the MPF (Merkle Patricia Forestry) trie.

| Method | Returns | Description |
|--------|---------|-------------|
| `hashFunctions()` | `Arbitrary<HashFunction>` | One of Blake2b-256, SHA-256, or SHA3-256 |
| `alphanumericKey()` | `Arbitrary<byte[]>` | Alphanumeric string key (4-32 chars) as UTF-8 bytes |
| `alphanumericValue()` | `Arbitrary<byte[]>` | Alphanumeric string value (1-64 chars) as UTF-8 bytes |
| `trieKeyValues(int min, int max)` | `Arbitrary<List<Map.Entry<byte[], byte[]>>>` | List of binary key-value pairs for trie insertion |
| `trieKeyValuesAlphanumeric(int min, int max)` | `Arbitrary<List<Map.Entry<byte[], byte[]>>>` | List of human-readable key-value pairs for easier shrinking and debugging |
| `deduplicateEntries(List<...>)` | `Map<ByteArrayWrapper, byte[]>` | Deduplicate entries by key (last-write-wins) |
| `trieOperations(List<byte[]> keys, int min, int max)` | `Arbitrary<List<TrieOperation>>` | Random PUT/DELETE/GET ops over a key pool |

Constants `MpfArbitraries.SHA256` and `MpfArbitraries.SHA3_256` are also available as standalone `HashFunction` instances.

### ByteArrayWrapper

A `byte[]` wrapper with proper `equals`/`hashCode` (using `Arrays.equals`/`Arrays.hashCode`). The constructor defensively copies the input array and pre-computes the hash code. Use this whenever you need `byte[]` as a `Map` key or `Set` element.

```java
Map<ByteArrayWrapper, byte[]> map = new LinkedHashMap<>();
map.put(new ByteArrayWrapper(key), value);
```

### TrieOperation

A record representing a single trie operation:

```java
public record TrieOperation(OpType type, byte[] key, byte[] value) {
    public enum OpType { PUT, DELETE, GET }
}
```

## Writing Property Tests

### Minimal example

```java
import com.bloxbean.cardano.client.test.CardanoArbitraries;
import net.jqwik.api.*;

class MyPropertyTest {

    @Property(tries = 100)
    void policyIdIsAlways28Bytes(@ForAll("policyIds") byte[] id) {
        assert id.length == 28;
    }

    @Provide
    Arbitrary<byte[]> policyIds() {
        return CardanoArbitraries.policyIds();
    }
}
```

### Testing a trie across hash functions

Use `MpfArbitraries.hashFunctions()` as a `@Provide` method to parameterize your property across Blake2b-256, SHA-256, and SHA3-256:

```java
import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.client.test.vds.TrieOperation;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import net.jqwik.api.*;

class TriePropertyTest {

    @Provide
    Arbitrary<HashFunction> hashFunctions() {
        return MpfArbitraries.hashFunctions();
    }

    @Property(tries = 200)
    void putThenGetReturnsValue(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("randomKey") byte[] key,
            @ForAll("randomValue") byte[] value) {

        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        trie.put(key, value);
        byte[] retrieved = trie.get(key);

        assertArrayEquals(value, retrieved);
    }

    @Provide
    Arbitrary<byte[]> randomKey() {
        return MpfArbitraries.alphanumericKey();
    }

    @Provide
    Arbitrary<byte[]> randomValue() {
        return MpfArbitraries.alphanumericValue();
    }
}
```

### Testing with bulk entries

Generate a list of key-value pairs and deduplicate by key before inserting into the trie:

```java
@Provide
Arbitrary<List<Map.Entry<byte[], byte[]>>> entries() {
    return MpfArbitraries.trieKeyValuesAlphanumeric(10, 100);
}

@Property(tries = 200)
void rootHashIsDeterministic(
        @ForAll("hashFunctions") HashFunction hashFn,
        @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {

    // Deduplicate keys (last-write-wins)
    Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);
    if (deduped.size() < 2) return;

    // Insert in original order
    TestNodeStore store1 = new TestNodeStore();
    MpfTrie trie1 = new MpfTrie(store1, hashFn);
    for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
        trie1.put(e.getKey().getData(), e.getValue());
    }

    // Insert in reversed order
    TestNodeStore store2 = new TestNodeStore();
    MpfTrie trie2 = new MpfTrie(store2, hashFn);
    List<Map.Entry<ByteArrayWrapper, byte[]>> reversed = new ArrayList<>(deduped.entrySet());
    Collections.reverse(reversed);
    for (Map.Entry<ByteArrayWrapper, byte[]> e : reversed) {
        trie2.put(e.getKey().getData(), e.getValue());
    }

    assertArrayEquals(trie1.getRootHash(), trie2.getRootHash());
}
```

### Testing with random operation sequences

Generate a sequence of random PUT/DELETE/GET operations against a shared key pool. Use a `@Provide` method so jqwik controls generation and shrinking:

```java
@Provide
Arbitrary<List<TrieOperation>> operations() {
    List<byte[]> keyPool = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
        keyPool.add(("key-" + i).getBytes());
    }
    return MpfArbitraries.trieOperations(keyPool, 10, 50);
}

@Property(tries = 100)
void trieHandlesRandomOperations(
        @ForAll("hashFunctions") HashFunction hashFn,
        @ForAll("operations") List<TrieOperation> ops) {

    TestNodeStore store = new TestNodeStore();
    MpfTrie trie = new MpfTrie(store, hashFn);
    Map<ByteArrayWrapper, byte[]> mirror = new HashMap<>();

    for (TrieOperation op : ops) {
        switch (op.type()) {
            case PUT -> {
                trie.put(op.key(), op.value());
                mirror.put(new ByteArrayWrapper(op.key()), op.value());
            }
            case DELETE -> {
                trie.delete(op.key());
                mirror.remove(new ByteArrayWrapper(op.key()));
            }
            case GET -> {
                byte[] expected = mirror.get(new ByteArrayWrapper(op.key()));
                byte[] actual = trie.get(op.key());
                assertArrayEquals(expected, actual);
            }
        }
    }
}
```

## Composing Custom Arbitraries

The generators are designed to be composed with jqwik's built-in combinators:

```java
// Combine a policy ID with an asset name into a full asset identifier
Arbitrary<byte[]> assetIds() {
    return Combinators.combine(
            CardanoArbitraries.policyIds(),
            CardanoArbitraries.assetNames()
    ).as((policy, name) -> {
        byte[] result = new byte[policy.length + name.length];
        System.arraycopy(policy, 0, result, 0, policy.length);
        System.arraycopy(name, 0, result, policy.length, name.length);
        return result;
    });
}

// Map an arbitrary to produce hex-encoded output
Arbitrary<String> policyIdHex() {
    return CardanoArbitraries.hexStrings(28);
}
```

## Running Tests

```bash
# Run all tests in a module
./gradlew :verified-structures:merkle-patricia-forestry:test

# Run a specific property test class
./gradlew :verified-structures:merkle-patricia-forestry:test --tests "*MpfPropertyTest"

# Run a single property
./gradlew :verified-structures:merkle-patricia-forestry:test --tests "*MpfPropertyTest.p1*"
```

## Tips

**Controlling tries.** Use `@Property(tries = N)` to set how many random inputs jqwik generates. 200 is a good default; use fewer (50) for slow tests like RocksDB.

**Reproducibility.** When a property fails, jqwik records the failing seed in `.jqwik-database` and replays it on the next run. The repository ignores these generated files by default; use an explicit `@Property(seed = "...")` when a failure must be preserved in source control.

**Shrinking.** jqwik automatically shrinks failing inputs to the smallest reproducer. Check the test output for `Shrunk Sample` to see the minimal case.

**Deduplication.** When generating lists of key-value pairs, always deduplicate by key before inserting into a trie. Use the shared utility:

```java
Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);
```

**Avoiding edge cases.** If your property doesn't hold for trivially small inputs (e.g., 0 or 1 entries), guard with an early return:

```java
if (deduped.size() < 2) return;
```

**RocksDB tests.** jqwik's `@Property` methods don't support JUnit's `@TempDir`. Use `Files.createTempDirectory()` instead, and wrap RocksDB operations in a try-catch for `UnsatisfiedLinkError`. Always clean up temp directories in a `finally` block:

```java
Path tempDir = Files.createTempDirectory("my-test-");
try (RocksDbNodeStore store = new RocksDbNodeStore(tempDir.resolve("db").toString())) {
    // ...
} catch (UnsatisfiedLinkError e) {
    Assumptions.assumeTrue(false, "RocksDB JNI not available: " + e.getMessage());
} finally {
    // Clean up temp directory
    try (var walk = Files.walk(tempDir)) {
        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (Exception ignore) {}
        });
    } catch (Exception ignore) {}
}
```

## CI/CD Tips

**`.jqwik-database` reproducibility.** The `.jqwik-database` file stores seeds for failed properties so they are replayed on subsequent runs. The repository ignores these files because they are generated local state. If CI must replay a known failure, pin the seed in the property or add a focused regression test.

**Fixed seeds for debugging.** To pin a specific seed for deterministic reproduction:

```java
@Property(tries = 200, seed = "1234567890")
void myProperty(...) { ... }
```

Remove the `seed` parameter once the issue is resolved.

**Reduced tries in CI.** If property tests are too slow in CI, reduce `tries` via a system property pattern:

```java
@Property(tries = 200) // local default
void myProperty(...) { ... }
```

Or configure globally in `jqwik.properties`:

```properties
jqwik.tries.default=50
```

**Temp directory cleanup.** RocksDB property tests create temp directories. The test code includes cleanup in `finally` blocks, but if tests are interrupted, orphaned directories may remain in the system temp folder. Consider a periodic CI cleanup step for directories matching `mpf-rocksdb-prop-*`.

## Existing Property Tests

For full working examples, see:

- **In-memory (10 properties):** `verified-structures/merkle-patricia-forestry/src/test/java/.../MpfPropertyTest.java`
- **RocksDB (3 properties):** `verified-structures/merkle-patricia-forestry-rocksdb/src/test/java/.../MpfRocksDbPropertyTest.java`
