package com.bloxbean.cardano.vds.mpf;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Forged proofs that CCL's verifier must reject.
 *
 * <p>Aiken's {@code aiken-lang/merkle-patricia-forestry} 2.1.0 does not check the shape of proof steps (nibble range,
 * prefix nibbles, digest lengths) nor that a terminal {@code Leaf}/{@code Fork} neighbor differs from the queried key.
 * Checked with {@code aiken check}: {@code miss} accepts a forged proof for a present key (and, with an out-of-range
 * Fork nibble, for any key), and {@code insert} accepts a forged Fork proof that re-inserts a key that is already
 * present. CCL rejects all of these: it requires nibbles and prefix bytes in 0..15, digest-length fields, and a
 * neighbor under a different nibble than the queried path.</p>
 */
class MpfForgedProofTest {

    @Test
    void terminalLeafNamingTheQueriedKeyIsRejected() throws Exception {
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        byte[] key = utf8("only");
        byte[] value = utf8("one");
        trie.put(key, value);

        Array leaf = new Array();
        leaf.add(new UnsignedInteger(0));
        leaf.add(new ByteString(Blake2b256.digest(key)));
        leaf.add(new ByteString(Blake2b256.digest(value)));
        leaf.setTag(new Tag(123));
        Array proof = new Array();
        proof.add(leaf);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new CborEncoder(bytes).encode(proof);

        assertTrue(trie.verifyProofWire(trie.getRootHash(), key, value, true, trie.getProofWire(key).orElseThrow()));
        assertFalse(trie.verifyProofWire(trie.getRootHash(), key, null, false, bytes.toByteArray()));
    }

    @Test
    void terminalForkCoveringTheQueriedKeyIsRejected() {
        // Two keys whose paths share the first nibble: the root is a branch with a one-nibble prefix.
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        byte[] a = null, b = null;
        for (int i = 0; b == null; i++) {
            byte[] k = utf8("k-" + i);
            if (a == null) {
                a = k;
            } else {
                int na = Blake2b256.digest(a)[0] & 0xFF, nk = Blake2b256.digest(k)[0] & 0xFF;
                if ((na >> 4) == (nk >> 4) && (na & 0x0F) != (nk & 0x0F)) b = k;
            }
        }
        trie.put(a, utf8("A"));
        trie.put(b, utf8("B"));
        int firstNibble = (Blake2b256.digest(a)[0] & 0xF0) >> 4;

        // A key that diverges at the first nibble gets a genuine terminal Fork proof.
        byte[] absent = null;
        for (int i = 0; absent == null; i++) {
            byte[] k = utf8("absent-" + i);
            if (((Blake2b256.digest(k)[0] & 0xF0) >> 4) != firstNibble) absent = k;
        }
        byte[] forkProof = trie.getProofWire(absent).orElseThrow();
        assertTrue(trie.verifyProofWire(trie.getRootHash(), absent, null, false, forkProof));

        // Reusing it for a present key is a forgery.
        assertFalse(trie.verifyProofWire(trie.getRootHash(), a, null, false, forkProof));
        assertFalse(trie.verifyProofWire(trie.getRootHash(), b, null, false, forkProof));
    }

    @Test
    void outOfRangeForkNibbleIsRejected() throws Exception {
        // Aiken 2.1.0 accepts this for miss on any key and for insert of the present key "only".
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        trie.put(utf8("only"), utf8("one"));
        Array neighbor = new Array();
        neighbor.add(new UnsignedInteger(255));
        neighbor.add(new ByteString(Blake2b256.digest(utf8("only"))));
        neighbor.add(new ByteString(Blake2b256.digest(utf8("one"))));
        neighbor.setTag(new Tag(121));
        Array fork = new Array();
        fork.add(new UnsignedInteger(0));
        fork.add(neighbor);
        fork.setTag(new Tag(122));
        Array proof = new Array();
        proof.add(fork);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new CborEncoder(bytes).encode(proof);

        for (String key : new String[]{"only", "anything"}) {
            assertFalse(trie.verifyProofWire(trie.getRootHash(), utf8(key), null, false, bytes.toByteArray()), key);
        }
    }

    @Test
    void hugeArraysAreRejectedWithoutAllocatingPerByte() {
        byte[] payload = new byte[10_000_002];
        payload[0] = (byte) 0x9F;
        payload[payload.length - 1] = (byte) 0xFF;
        assertFalse(com.bloxbean.cardano.vds.mpf.proof.ProofVerifier.verify(new byte[32], utf8("k"), null, false, payload));
    }

    @Test
    void branchWithAValueHashFieldIsRejectedUnderTheMpfScheme() throws Exception {
        // Aiken's Branch has two fields; a third (branch value hash) is only meaningful for other commitment schemes.
        Array branch = new Array();
        branch.add(new UnsignedInteger(0));
        branch.add(new ByteString(new byte[128]));
        branch.add(new ByteString(new byte[32]));
        branch.setTag(new Tag(121));
        Array proof = new Array();
        proof.add(branch);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new CborEncoder(bytes).encode(proof);

        byte[] rootWithThirdField = com.bloxbean.cardano.vds.core.util.Bytes.fromHex(
                "2ff33d3631191a59485eac326144668c7b912139846a113f39274353b8059c61");
        assertFalse(com.bloxbean.cardano.vds.mpf.proof.ProofVerifier.verify(rootWithThirdField, utf8("k"), utf8("v"),
                true, bytes.toByteArray()));
    }

    @Test
    void deeplyNestedTagsAreRejectedWithoutStackOverflow() {
        byte[] payload = new byte[30_002];
        payload[0] = (byte) 0x81;
        java.util.Arrays.fill(payload, 1, 30_001, (byte) 0xC0);
        payload[30_001] = (byte) 0x80;
        assertFalse(com.bloxbean.cardano.vds.mpf.proof.ProofVerifier.verify(new byte[32], utf8("k"), utf8("v"), true, payload));
        assertFalse(com.bloxbean.cardano.vds.mpf.proof.ProofVerifier.verify(new byte[32], utf8("k"), null, false, payload));
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
