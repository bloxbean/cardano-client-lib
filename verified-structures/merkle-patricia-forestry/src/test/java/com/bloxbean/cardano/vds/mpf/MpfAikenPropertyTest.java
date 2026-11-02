package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.test.vds.MpfArbitraries;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpf.proof.ProofFormatter;
import com.bloxbean.cardano.vds.mpf.proof.ProofVerifier;
import com.bloxbean.cardano.vds.mpf.reference.ReferenceMpf;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Properties tying {@link MpfTrie} to an independent reference model ({@link ReferenceMpf}) and to Aiken's proof
 * semantics, over random histories of puts and deletes (deletes exercise node compression).
 *
 * <ul>
 *   <li>A1: the root equals the reference root.</li>
 *   <li>A2: every proof (present or absent key) equals the reference's canonical proof.</li>
 *   <li>A3: Aiken transitions: for a present key {@code has} holds and {@code excluding(proof)} is the root without
 *   the key ({@code delete}); for an absent key {@code miss} holds and {@code including(proof)} is the root with the
 *   key ({@code insert}).</li>
 *   <li>A4: proofs do not prove the opposite membership, or a different value.</li>
 *   <li>A5: pre-#715 Branch-only inclusion proofs still verify for {@code has}.</li>
 *   <li>A6: roots match the reference for other hash functions too.</li>
 * </ul>
 */
class MpfAikenPropertyTest {

    private static final HashFunction HF = Blake2b256::digest;
    private static final MpfCommitmentScheme CS = new MpfCommitmentScheme(HF);
    private static final ObjectMapper OM = new ObjectMapper();

    /** A history: key pool, operations over it (index, value or null for delete), probe keys outside the pool. */
    static final class History {
        final List<byte[]> pool;
        final List<Map.Entry<Integer, byte[]>> ops;
        final List<byte[]> absentProbes;

        History(List<byte[]> pool, List<Map.Entry<Integer, byte[]>> ops, List<byte[]> absentProbes) {
            this.pool = pool;
            this.ops = ops;
            this.absentProbes = absentProbes;
        }

        @Override
        public String toString() {
            return "History{pool=" + pool.size() + ", ops=" + ops.size() + "}";
        }
    }

    @Provide
    Arbitrary<History> histories() {
        Arbitrary<byte[]> key = Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(12);
        return key.list().ofMinSize(1).ofMaxSize(120).uniqueElements(Bytes::toHex).flatMap(pool -> {
            Arbitrary<Map.Entry<Integer, byte[]>> op = Combinators.combine(
                    Arbitraries.integers().between(0, pool.size() - 1),
                    Arbitraries.integers().between(0, 9),
                    Arbitraries.bytes().array(byte[].class).ofMaxSize(8)
            ).as((i, kind, v) -> new AbstractMap.SimpleEntry<>(i, kind < 7 ? v : null));
            Arbitrary<List<byte[]>> probes = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                    .map(s -> ("absent:" + s).getBytes()).list().ofMinSize(1).ofMaxSize(10);
            return Combinators.combine(op.list().ofMinSize(0).ofMaxSize(250), probes)
                    .as((ops, absent) -> new History(pool, ops, absent));
        });
    }

    private static final class Built {
        final MpfTrie trie;
        final ReferenceMpf ref;
        final Map<String, byte[]> model = new LinkedHashMap<>();

        Built(HashFunction hf) {
            this.trie = new MpfTrie(new TestNodeStore(), hf);
            this.ref = new ReferenceMpf(hf);
        }
    }

    private static Built build(History h, HashFunction hf) {
        Built b = new Built(hf);
        for (Map.Entry<Integer, byte[]> op : h.ops) {
            byte[] k = h.pool.get(op.getKey());
            if (op.getValue() == null) {
                b.trie.delete(k);
                b.ref.delete(k);
                b.model.remove(Bytes.toHex(k));
            } else {
                b.trie.put(k, op.getValue());
                b.ref.put(k, op.getValue());
                b.model.put(Bytes.toHex(k), op.getValue());
            }
        }
        return b;
    }

    @Property(tries = 300)
    void a1_a2_rootsAndProofsMatchReference(@ForAll("histories") History h) throws Exception {
        Built b = build(h, HF);
        assertEquals(hexOrNull(b.ref.root()), hexOrNull(b.trie.getRootHash()), "A1 root");
        for (byte[] k : probes(h)) {
            JsonNode actual = OM.readTree(ProofFormatter.toJson(b.trie.getProofWire(k).orElseThrow()));
            assertEquals(b.ref.proof(k, false), actual, "A2 proof for " + Bytes.toHex(k));
        }
    }

    @Property(tries = 300)
    void a3_a4_aikenTransitions(@ForAll("histories") History h) {
        Built b = build(h, HF);
        byte[] root = b.trie.getRootHash();
        for (byte[] k : probes(h)) {
            byte[] wire = b.trie.getProofWire(k).orElseThrow();
            byte[] value = b.model.get(Bytes.toHex(k));
            if (value != null) {
                assertTrue(ProofVerifier.verify(root, k, value, true, wire, HF, CS), "A3 has");
                b.ref.delete(k);
                assertTrue(ProofVerifier.verify(b.ref.root(), k, null, false, wire, HF, CS), "A3 delete");
                b.ref.put(k, value);
                assertFalse(ProofVerifier.verify(root, k, null, false, wire, HF, CS), "A4 present key is not missing");
                assertFalse(ProofVerifier.verify(root, k, concat(value, new byte[]{1}), true, wire, HF, CS), "A4 other value");
            } else {
                assertTrue(ProofVerifier.verify(root, k, null, false, wire, HF, CS), "A3 miss");
                byte[] v = "inserted".getBytes();
                b.ref.put(k, v);
                assertTrue(ProofVerifier.verify(b.ref.root(), k, v, true, wire, HF, CS), "A3 insert");
                b.ref.delete(k);
                assertFalse(ProofVerifier.verify(root, k, v, true, wire, HF, CS), "A4 absent key is not present");
            }
        }
    }

    @Property(tries = 200)
    void a5_legacyBranchOnlyProofsStillVerifyHas(@ForAll("histories") History h) throws Exception {
        Built b = build(h, HF);
        for (Map.Entry<String, byte[]> e : b.model.entrySet()) {
            byte[] k = Bytes.fromHex(e.getKey());
            byte[] legacy = LegacyProofs.toWire(b.ref.proof(k, true));
            assertTrue(ProofVerifier.verify(b.trie.getRootHash(), k, e.getValue(), true, legacy, HF, CS),
                    "legacy proof for " + e.getKey());
        }
    }

    @Property(tries = 100)
    void a6_rootsMatchReferenceForOtherHashFunctions(@ForAll("histories") History h,
                                                    @ForAll @IntRange(min = 0, max = 2) int hashIndex) {
        HashFunction hf = hashIndex == 0 ? HF : hashIndex == 1 ? MpfArbitraries.SHA256 : MpfArbitraries.SHA3_256;
        Built b = build(h, hf);
        assertEquals(hexOrNull(b.ref.root()), hexOrNull(b.trie.getRootHash()));
        MpfCommitmentScheme cs = new MpfCommitmentScheme(hf);
        for (byte[] k : probes(h)) {
            byte[] wire = b.trie.getProofWire(k).orElseThrow();
            byte[] value = b.model.get(Bytes.toHex(k));
            assertTrue(ProofVerifier.verify(b.trie.getRootHash(), k, value, value != null, wire, hf, cs));
        }
    }

    private static List<byte[]> probes(History h) {
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < h.pool.size(); i += Math.max(1, h.pool.size() / 15)) out.add(h.pool.get(i));
        out.addAll(h.absentProbes);
        return out;
    }

    private static String hexOrNull(byte[] b) {
        return b == null ? null : Bytes.toHex(b);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
