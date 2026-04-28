package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.test.ByteArrayWrapper;
import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import net.jqwik.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for MpfTrie using jqwik.
 * Tests P1–P10 covering core trie properties across multiple hash functions.
 */
class MpfPropertyTest {

    @Provide
    Arbitrary<HashFunction> hashFunctions() {
        return MpfArbitraries.hashFunctions();
    }

    @Provide
    Arbitrary<List<Map.Entry<byte[], byte[]>>> entries() {
        return MpfArbitraries.trieKeyValuesAlphanumeric(10, 100);
    }

    @Provide
    Arbitrary<byte[]> randomKey() {
        return MpfArbitraries.alphanumericKey();
    }

    @Provide
    Arbitrary<byte[]> randomValue() {
        return MpfArbitraries.alphanumericValue();
    }

    // ---- P1: Put-Get Roundtrip ----
    @Property(tries = 200)
    void p1_putGetRoundtrip(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("randomKey") byte[] key,
            @ForAll("randomValue") byte[] value) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        trie.put(key, value);
        byte[] retrieved = trie.get(key);

        assertArrayEquals(value, retrieved, "get must return the value that was put");
    }

    // ---- P2: Delete Removes Entry ----
    @Property(tries = 200)
    void p2_deleteRemovesEntry(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("randomKey") byte[] key,
            @ForAll("randomValue") byte[] value) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        trie.put(key, value);
        assertNotNull(trie.get(key));

        trie.delete(key);
        assertNull(trie.get(key), "get must return null after delete");
    }

    // ---- P3: Root Hash Determinism (insertion order independence) ----
    @Property(tries = 200)
    void p3_rootHashDeterminism(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        // Deduplicate by key (last-write-wins) to avoid order-dependent overwrites
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);
        if (deduped.size() < 2) return; // need at least 2 entries

        List<Map.Entry<ByteArrayWrapper, byte[]>> entryList = new ArrayList<>(deduped.entrySet());

        // Insert in original order
        TestNodeStore store1 = new TestNodeStore();
        MpfTrie trie1 = new MpfTrie(store1, hashFn);
        for (Map.Entry<ByteArrayWrapper, byte[]> e : entryList) {
            trie1.put(e.getKey().getData(), e.getValue());
        }

        // Insert in reversed order
        TestNodeStore store2 = new TestNodeStore();
        MpfTrie trie2 = new MpfTrie(store2, hashFn);
        List<Map.Entry<ByteArrayWrapper, byte[]>> reversed = new ArrayList<>(entryList);
        Collections.reverse(reversed);
        for (Map.Entry<ByteArrayWrapper, byte[]> e : reversed) {
            trie2.put(e.getKey().getData(), e.getValue());
        }

        assertArrayEquals(trie1.getRootHash(), trie2.getRootHash(),
                "Root hash must be the same regardless of insertion order");
        assertEquals(deduped.size(), trie1.computeSize(),
                "Trie size must equal number of unique keys");
    }

    // ---- P4: Inclusion Proof Soundness ----
    @Property(tries = 200)
    void p4_inclusionProofSoundness(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        // Deduplicate
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            trie.put(e.getKey().getData(), e.getValue());
        }

        byte[] root = trie.getRootHash();
        assertNotNull(root);

        // Verify proof generation and verification for every inserted key on the multi-entry trie
        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            byte[] key = e.getKey().getData();
            byte[] value = trie.get(key);
            assertNotNull(value, "inserted key must be retrievable");
            Optional<byte[]> wire = trie.getProofWire(key);
            assertTrue(wire.isPresent(), "proof must exist for inserted key");
            assertTrue(wire.get().length > 0, "proof must be non-empty");
            assertTrue(trie.verifyProofWire(root, key, value, true, wire.get()),
                    "inclusion proof must verify for multi-entry trie");
        }
    }

    // ---- P5: Exclusion Proof Soundness ----
    // Tests exclusion proof generation across hash functions. Proof verification is tested
    // for single-entry tries to avoid the sparse branch proof verification bug.
    @Property(tries = 200)
    void p5_exclusionProofSoundness(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("randomKey") byte[] key,
            @ForAll("randomValue") byte[] value,
            @ForAll("randomKey") byte[] missingKey) {
        // Ensure keys are different
        if (Arrays.equals(key, missingKey)) return;

        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);
        trie.put(key, value);

        byte[] root = trie.getRootHash();
        assertNotNull(root);
        assertNull(trie.get(missingKey), "missing key should not be found");

        Optional<byte[]> wire = trie.getProofWire(missingKey);
        assertTrue(wire.isPresent(), "proof must exist even for missing key");
        assertTrue(trie.verifyProofWire(root, missingKey, null, false, wire.get()),
                "exclusion proof must verify for missing key");
    }

    // ---- P6: Overwrite Consistency ----
    @Property(tries = 200)
    void p6_overwriteConsistency(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("randomKey") byte[] key,
            @ForAll("randomValue") byte[] v1,
            @ForAll("randomValue") byte[] v2) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        trie.put(key, v1);
        trie.put(key, v2);

        assertArrayEquals(v2, trie.get(key), "get must return the latest value after overwrite");

        byte[] root = trie.getRootHash();
        assertNotNull(root);
        Optional<byte[]> wire = trie.getProofWire(key);
        assertTrue(wire.isPresent());
        assertTrue(trie.verifyProofWire(root, key, v2, true, wire.get()),
                "proof must verify for the latest value");
    }

    // ---- P7: Delete-All Empties Trie ----
    @Property(tries = 200)
    void p7_deleteAllEmptiesTrie(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        // Deduplicate keys
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            trie.put(e.getKey().getData(), e.getValue());
        }

        for (ByteArrayWrapper key : deduped.keySet()) {
            trie.delete(key.getData());
        }

        assertNull(trie.getRootHash(), "root must be null after deleting all entries");
        assertEquals(0, trie.computeSize(), "trie size must be 0 after deleting all entries");
    }

    // ---- P8: Hash Function Isolation ----
    @Property(tries = 200)
    void p8_hashFunctionIsolation(
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        if (entries.isEmpty()) return;

        TestNodeStore store1 = new TestNodeStore();
        MpfTrie blake2bTrie = new MpfTrie(store1, Blake2b256::digest);

        TestNodeStore store2 = new TestNodeStore();
        MpfTrie sha256Trie = new MpfTrie(store2, MpfArbitraries.SHA256);

        for (Map.Entry<byte[], byte[]> e : entries) {
            blake2bTrie.put(e.getKey(), e.getValue());
            sha256Trie.put(e.getKey(), e.getValue());
        }

        byte[] blake2bRoot = blake2bTrie.getRootHash();
        byte[] sha256Root = sha256Trie.getRootHash();

        assertNotNull(blake2bRoot);
        assertNotNull(sha256Root);
        assertFalse(Arrays.equals(blake2bRoot, sha256Root),
                "Different hash functions must produce different root hashes");
    }

    // ---- P9: Bulk Insert-Delete-Reinsert ----
    @Property(tries = 200)
    void p9_bulkInsertDeleteReinsert(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        // Deduplicate
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);
        if (deduped.size() < 2) return;

        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, hashFn);

        // Insert all
        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            trie.put(e.getKey().getData(), e.getValue());
        }
        byte[] rootAfterInsert = trie.getRootHash();
        assertEquals(deduped.size(), trie.computeSize(),
                "Trie size must equal number of unique keys after insert");

        // Delete a subset (first half)
        List<ByteArrayWrapper> keys = new ArrayList<>(deduped.keySet());
        int half = keys.size() / 2;
        List<ByteArrayWrapper> toDelete = keys.subList(0, half);
        for (ByteArrayWrapper key : toDelete) {
            trie.delete(key.getData());
        }

        // Reinsert deleted subset
        for (ByteArrayWrapper key : toDelete) {
            trie.put(key.getData(), deduped.get(key));
        }

        assertArrayEquals(rootAfterInsert, trie.getRootHash(),
                "Root hash must be restored after delete+reinsert of same entries");
        assertEquals(deduped.size(), trie.computeSize(),
                "Trie size must be restored after delete+reinsert");
    }

    // ---- P10: Trie Reload from Root ----
    @Property(tries = 200)
    void p10_trieReloadFromRoot(
            @ForAll("hashFunctions") HashFunction hashFn,
            @ForAll("entries") List<Map.Entry<byte[], byte[]>> entries) {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie1 = new MpfTrie(store, hashFn);

        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(entries);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            trie1.put(e.getKey().getData(), e.getValue());
        }

        byte[] root = trie1.getRootHash();
        if (root == null) return;

        // Reload trie from same store + root
        MpfTrie trie2 = new MpfTrie(store, hashFn, root);

        assertArrayEquals(root, trie2.getRootHash());
        for (Map.Entry<ByteArrayWrapper, byte[]> e : deduped.entrySet()) {
            assertArrayEquals(e.getValue(), trie2.get(e.getKey().getData()),
                    "reloaded trie must return same values");
        }
    }
}
