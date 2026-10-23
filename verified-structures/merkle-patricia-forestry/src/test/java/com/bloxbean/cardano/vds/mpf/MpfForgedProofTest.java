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
 * Non-membership proofs that "prove" a present key is missing.
 *
 * <p>Aiken's {@code miss} (merkle-patricia-forestry 2.1.0) does not check that a terminal {@code Leaf} or
 * {@code Fork} neighbor differs from the queried key, so it accepts these forged proofs (checked with
 * {@code aiken check}). Aiken's {@code insert} rejects them, because {@code do_fork} asserts the nibbles differ.
 * CCL's verifier applies that check to non-membership proofs too, so it rejects them.</p>
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

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
