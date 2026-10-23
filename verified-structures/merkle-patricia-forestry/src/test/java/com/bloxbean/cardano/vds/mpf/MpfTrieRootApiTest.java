package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Root handling of {@link MpfTrie}: empty root, defensive copies, all-zero root, strict {@code open}. */
class MpfTrieRootApiTest {

    private static final byte[] ZERO = new byte[32];

    @Test
    void emptyTrieRoot() {
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        assertNull(trie.getRootHash());
        assertArrayEquals(ZERO, trie.getRootHashOrEmpty());
    }

    @Test
    void returnedRootIsACopy() {
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        trie.put(b("alice"), b("100"));
        byte[] root = trie.getRootHash();
        byte[] original = root.clone();
        root[0] ^= (byte) 0xFF;
        assertArrayEquals(original, trie.getRootHash());
        assertArrayEquals(b("100"), trie.get(b("alice")));
        byte[] orEmpty = trie.getRootHashOrEmpty();
        orEmpty[0] ^= (byte) 0xFF;
        assertArrayEquals(original, trie.getRootHash());
    }

    @Test
    void rootPassedInIsCopied() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);
        trie.put(b("alice"), b("100"));
        byte[] root = trie.getRootHash();
        byte[] original = root.clone();

        MpfTrie viaCtor = new MpfTrie(store, root);
        MpfTrie viaSetter = new MpfTrie(store);
        viaSetter.setRootHash(root);
        root[0] ^= (byte) 0xFF;
        assertArrayEquals(original, viaCtor.getRootHash());
        assertArrayEquals(original, viaSetter.getRootHash());
        assertArrayEquals(b("100"), viaCtor.get(b("alice")));
        assertArrayEquals(b("100"), viaSetter.get(b("alice")));
    }

    @Test
    void allZeroRootIsTheEmptyTrie() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store, ZERO.clone());
        assertNull(trie.get(b("alice")));
        assertArrayEquals(ZERO, trie.getRootHash(), "unchanged trie keeps the root it was opened with");
        assertArrayEquals(ZERO, trie.getRootHashOrEmpty());
        assertTrue(trie.getAllEntries().isEmpty());

        byte[] missProof = trie.getProofWire(b("alice")).orElseThrow();
        assertTrue(trie.verifyProofWire(ZERO, b("alice"), null, false, missProof));
        assertTrue(trie.verifyProofWire(null, b("alice"), null, false, missProof));

        trie.put(b("alice"), b("100"));
        MpfTrie reference = new MpfTrie(new TestNodeStore());
        reference.put(b("alice"), b("100"));
        assertArrayEquals(reference.getRootHash(), trie.getRootHash());

        trie.delete(b("alice"));
        assertNull(trie.getRootHash());
        assertArrayEquals(ZERO, trie.getRootHashOrEmpty());
    }

    @Test
    void openFailsFastOnUnknownRoot() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);
        trie.put(b("alice"), b("100"));

        assertArrayEquals(b("100"), MpfTrie.open(store, trie.getRootHash()).get(b("alice")));
        assertNull(MpfTrie.open(store, null).getRootHash());
        assertArrayEquals(ZERO, MpfTrie.open(store, ZERO).getRootHashOrEmpty());

        byte[] unknown = trie.getRootHash();
        unknown[31] ^= 1;
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> MpfTrie.open(store, unknown));
        assertTrue(e.getMessage().contains("not in the node store"));

        // The lenient constructor keeps its behaviour.
        MpfTrie lenient = new MpfTrie(store, unknown);
        assertNull(lenient.get(b("alice")));
        assertFalse(Arrays.equals(unknown, new byte[32]));
    }

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
