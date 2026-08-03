package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.client.test.ByteArrayWrapper;
import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Assume;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests (jqwik) for the Jellyfish Merkle Tree, exercised across multiple hash
 * functions and randomized workloads. Covers root determinism, proof roundtrips (object + wire),
 * non-inclusion, versioned reads, and randomized forgery rejection.
 */
class JmtPropertyBasedTest {

    @Provide
    Arbitrary<HashFunction> hashFunctions() {
        return MpfArbitraries.hashFunctions();
    }

    @Provide
    Arbitrary<List<Map.Entry<byte[], byte[]>>> entries() {
        return MpfArbitraries.trieKeyValues(1, 60);
    }

    @Provide
    Arbitrary<List<Map.Entry<byte[], byte[]>>> entriesMin2() {
        return MpfArbitraries.trieKeyValues(2, 60);
    }

    @Provide
    Arbitrary<byte[]> randomKey() {
        return MpfArbitraries.alphanumericKey();
    }

    private static final class Fixture {
        final JellyfishMerkleTree tree;
        final CommitmentScheme commitments;
        final HashFunction hashFn;
        final Map<ByteArrayWrapper, byte[]> expected;
        final byte[] root;
        final long version = 1L;

        Fixture(HashFunction hashFn, List<Map.Entry<byte[], byte[]>> raw) {
            this.hashFn = hashFn;
            this.commitments = new ClassicJmtCommitmentScheme(hashFn);
            this.tree = new JellyfishMerkleTree(new InMemoryJmtStore(), commitments, hashFn);
            this.expected = MpfArbitraries.deduplicateEntries(raw);
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            for (Map.Entry<ByteArrayWrapper, byte[]> e : expected.entrySet()) {
                updates.put(e.getKey().getData(), e.getValue());
            }
            this.root = tree.put(version, updates).rootHash();
        }
    }

    // P1: root is a function of the final key->value set, independent of insertion order.
    @Property(tries = 200)
    void rootIsOrderIndependent(@ForAll("hashFunctions") HashFunction hashFn,
                                @ForAll("entries") List<Map.Entry<byte[], byte[]>> raw) {
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(raw);

        List<Map.Entry<ByteArrayWrapper, byte[]>> order1 = new ArrayList<>(deduped.entrySet());
        List<Map.Entry<ByteArrayWrapper, byte[]>> order2 = new ArrayList<>(deduped.entrySet());
        Collections.reverse(order2);

        byte[] r1 = rootFor(hashFn, order1);
        byte[] r2 = rootFor(hashFn, order2);
        assertArrayEquals(r1, r2, "root must not depend on insertion order");
    }

    private static byte[] rootFor(HashFunction hashFn, List<Map.Entry<ByteArrayWrapper, byte[]>> ordered) {
        CommitmentScheme cs = new ClassicJmtCommitmentScheme(hashFn);
        JellyfishMerkleTree tree = new JellyfishMerkleTree(new InMemoryJmtStore(), cs, hashFn);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        for (Map.Entry<ByteArrayWrapper, byte[]> e : ordered) {
            updates.put(e.getKey().getData(), e.getValue());
        }
        return tree.put(1L, updates).rootHash();
    }

    // P2: every present key produces a verifiable inclusion proof (object + wire), and get() agrees.
    @Property(tries = 150)
    void inclusionProofsRoundtrip(@ForAll("hashFunctions") HashFunction hashFn,
                                  @ForAll("entries") List<Map.Entry<byte[], byte[]>> raw) {
        Fixture f = new Fixture(hashFn, raw);
        for (Map.Entry<ByteArrayWrapper, byte[]> e : f.expected.entrySet()) {
            byte[] key = e.getKey().getData();
            byte[] value = e.getValue();

            assertArrayEquals(value, f.tree.get(key).orElse(null));

            JmtProof proof = f.tree.getProof(key, f.version).orElseThrow();
            assertEquals(JmtProof.ProofType.INCLUSION, proof.type());
            assertArrayEquals(value, proof.value());
            assertTrue(JmtProofVerifier.verify(f.root, key, value, proof, hashFn, f.commitments),
                    "object inclusion proof must verify");

            byte[] wire = f.tree.getProofWire(key, f.version).orElseThrow();
            assertTrue(f.tree.verifyProofWire(f.root, key, value, true, wire),
                    "wire inclusion proof must verify");
        }
    }

    // P3: keys not in the tree produce verifiable non-inclusion proofs.
    @Property(tries = 200)
    void nonInclusionProofsVerify(@ForAll("hashFunctions") HashFunction hashFn,
                                  @ForAll("entries") List<Map.Entry<byte[], byte[]>> raw,
                                  @ForAll("randomKey") byte[] probe) {
        Fixture f = new Fixture(hashFn, raw);
        Assume.that(!f.expected.containsKey(new ByteArrayWrapper(probe)));

        assertTrue(f.tree.get(probe).isEmpty());
        JmtProof proof = f.tree.getProof(probe, f.version).orElseThrow();
        assertNotEquals(JmtProof.ProofType.INCLUSION, proof.type());
        assertTrue(JmtProofVerifier.verify(f.root, probe, null, proof, hashFn, f.commitments),
                "non-inclusion proof must verify for an absent key");
    }

    // P4: forged / tampered inclusion checks are rejected (value swap, wrong root, wrong key).
    @Property(tries = 150)
    void tamperedInclusionRejected(@ForAll("hashFunctions") HashFunction hashFn,
                                   @ForAll("entriesMin2") List<Map.Entry<byte[], byte[]>> raw) {
        Fixture f = new Fixture(hashFn, raw);
        Assume.that(f.expected.size() >= 2);

        List<Map.Entry<ByteArrayWrapper, byte[]>> present = new ArrayList<>(f.expected.entrySet());
        byte[] key = present.get(0).getKey().getData();
        byte[] value = present.get(0).getValue();
        byte[] otherValue = present.get(1).getValue();

        JmtProof proof = f.tree.getProof(key, f.version).orElseThrow();
        byte[] wire = f.tree.getProofWire(key, f.version).orElseThrow();

        // Wrong root.
        byte[] wrongRoot = f.root.clone();
        wrongRoot[0] ^= 0x01;
        assertFalse(JmtProofVerifier.verify(wrongRoot, key, value, proof, hashFn, f.commitments));
        assertFalse(f.tree.verifyProofWire(wrongRoot, key, value, true, wire));

        // Wrong value (only meaningful when the two values differ).
        if (!java.util.Arrays.equals(value, otherValue)) {
            assertFalse(JmtProofVerifier.verify(f.root, key, otherValue, proof, hashFn, f.commitments));
            assertFalse(f.tree.verifyProofWire(f.root, key, otherValue, true, wire));
        }

        // A proof for `key` must not verify a FALSE statement about another key. Claiming
        // (otherKey, value) is only false when value != otherKey's real value; when the two keys
        // happen to share a value, (otherKey, value) is genuinely in the tree and may verify
        // (the proof's sibling hashes cover otherKey's leaf) — that is sound, not a forgery.
        byte[] otherKey = present.get(1).getKey().getData();
        if (!java.util.Arrays.equals(value, otherValue)) {
            assertFalse(JmtProofVerifier.verify(f.root, otherKey, value, proof, hashFn, f.commitments),
                    "a proof must not verify a false (key,value) claim for another key");
        }
    }

    // P5: historical reads reflect the value as of each version.
    @Property(tries = 100)
    void versionedReadsReflectHistory(@ForAll("hashFunctions") HashFunction hashFn,
                                      @ForAll("entriesMin2") List<Map.Entry<byte[], byte[]>> raw) {
        Map<ByteArrayWrapper, byte[]> deduped = MpfArbitraries.deduplicateEntries(raw);
        Assume.that(deduped.size() >= 2);

        CommitmentScheme cs = new ClassicJmtCommitmentScheme(hashFn);
        JellyfishMerkleTree tree = new JellyfishMerkleTree(new InMemoryJmtStore(), cs, hashFn);

        List<Map.Entry<ByteArrayWrapper, byte[]>> items = new ArrayList<>(deduped.entrySet());
        // v1: insert the first key. v2: insert the rest. v3: update the first key.
        byte[] firstKey = items.get(0).getKey().getData();
        byte[] firstV1 = items.get(0).getValue();

        Map<byte[], byte[]> v1 = new LinkedHashMap<>();
        v1.put(firstKey, firstV1);
        tree.put(1L, v1);

        Map<byte[], byte[]> v2 = new LinkedHashMap<>();
        for (int i = 1; i < items.size(); i++) {
            v2.put(items.get(i).getKey().getData(), items.get(i).getValue());
        }
        if (!v2.isEmpty()) tree.put(2L, v2);

        byte[] firstV3 = new byte[]{9, 9, 9, 9};
        Map<byte[], byte[]> v3 = new LinkedHashMap<>();
        v3.put(firstKey, firstV3);
        long lastVersion = v2.isEmpty() ? 2L : 3L;
        tree.put(lastVersion, v3);

        assertArrayEquals(firstV1, tree.get(firstKey, 1L).orElse(null), "v1 read");
        assertArrayEquals(firstV3, tree.get(firstKey, lastVersion).orElse(null), "latest read");
        // A key inserted at v2 must be absent at v1.
        if (!v2.isEmpty()) {
            byte[] laterKey = items.get(1).getKey().getData();
            if (!java.util.Arrays.equals(laterKey, firstKey)) {
                assertTrue(tree.get(laterKey, 1L).isEmpty(), "key added at v2 must be absent at v1");
            }
        }
    }
}
