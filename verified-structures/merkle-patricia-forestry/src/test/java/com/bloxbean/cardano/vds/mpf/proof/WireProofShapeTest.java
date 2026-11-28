package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shape checks {@link WireProof} applies to proof steps beyond Aiken's verifier: the Fork neighbor prefix length,
 * and the terminal Leaf/Fork step of an exclusion proof at an odd cursor.
 */
class WireProofShapeTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final MpfCommitmentScheme MPF = new MpfCommitmentScheme(HASH);
    private static final byte[] VALUE_HASH = Blake2b256.digest(utf8("value"));

    @Test
    void terminalLeafAtOddCursorNeedsARawKeyByteAbove15() {
        byte[] key = keyWithNibble1(n -> true);
        byte[] path = Blake2b256.digest(key);

        byte[] nibbleBytes = leafKeyAtCursor1(path);
        for (int i = 1; i < 32; i++) nibbleBytes[i] = (byte) (i % 16);
        assertThrows(IllegalArgumentException.class, () -> excluding(key, path, new WireProof.LeafStep(0, nibbleBytes, VALUE_HASH)));

        byte[] oneHighByte = nibbleBytes.clone();
        oneHighByte[31] = 0x10;
        assertDoesNotThrow(() -> excluding(key, path, new WireProof.LeafStep(0, oneHighByte, VALUE_HASH)));
    }

    @Test
    void terminalLeafAtEvenCursorIsNotAffected() {
        byte[] key = utf8("even");
        byte[] path = Blake2b256.digest(key);
        byte[] other = new byte[32];
        other[0] = (byte) ((((path[0] >> 4) & 0x0F) ^ 1) << 4);   // differs at nibble 0, other bytes all <= 15
        WireProof proof = new WireProof(List.of(new WireProof.LeafStep(0, other, VALUE_HASH)));
        assertDoesNotThrow(() -> proof.computeRoot(key, null, false, HASH, MPF));
    }

    @Test
    void terminalForkAtOddCursorRejectsLeafLengthPrefixStartingWithZero() {
        byte[] key = keyWithNibble1(n -> n != 0);
        byte[] path = Blake2b256.digest(key);

        // rebuilt prefix = [nibble] ++ prefix: 1 + 32 = 2 + (63 - 1) / 2
        assertThrows(IllegalArgumentException.class, () -> excluding(key, path, new WireProof.ForkStep(0, 0, new byte[32], VALUE_HASH)));
        assertDoesNotThrow(() -> excluding(key, path, new WireProof.ForkStep(0, 0, new byte[31], VALUE_HASH)));
        assertDoesNotThrow(() -> excluding(key, path, new WireProof.ForkStep(0, 0, new byte[33], VALUE_HASH)));
        int nonZero = nibble(path, 1) == 1 ? 2 : 1;
        assertDoesNotThrow(() -> excluding(key, path, new WireProof.ForkStep(0, nonZero, new byte[32], VALUE_HASH)));
    }

    @Test
    void forkPrefixMustEndBeforeTheLastNibble() {
        byte[] key = utf8("fork");
        byte[] path = Blake2b256.digest(key);
        int nibble = nibble(path, 0) ^ 1;

        // Fork at nibble 0: its neighbor starts at nibble 1, so the prefix may hold at most 62 nibbles.
        WireProof ok = new WireProof(List.of(new WireProof.ForkStep(0, nibble, new byte[62], VALUE_HASH)));
        WireProof tooLong = new WireProof(List.of(new WireProof.ForkStep(0, nibble, new byte[63], VALUE_HASH)));

        assertDoesNotThrow(() -> ok.computeRoot(key, null, false, HASH, MPF));
        assertDoesNotThrow(() -> ok.computeRoot(key, utf8("v"), true, HASH, MPF));
        assertThrows(IllegalArgumentException.class, () -> tooLong.computeRoot(key, null, false, HASH, MPF));
        assertThrows(IllegalArgumentException.class, () -> tooLong.computeRoot(key, utf8("v"), true, HASH, MPF));
    }

    // ------------------------------------------------------------------ helpers

    /** Exclusion over [Leaf diverging at nibble 0, terminal] so the terminal step sits at cursor 1. */
    private static byte[] excluding(byte[] key, byte[] path, WireProof.Step terminal) {
        byte[] first = Blake2b256.digest(utf8("first"));
        first[0] = (byte) ((((path[0] >> 4) & 0x0F) ^ 1) << 4 | (first[0] & 0x0F));
        WireProof proof = new WireProof(List.of(new WireProof.LeafStep(0, first, VALUE_HASH), terminal));
        return proof.computeRoot(key, null, false, HASH, MPF);
    }

    /** A Leaf key that shares nibble 0 with path and differs at nibble 1. */
    private static byte[] leafKeyAtCursor1(byte[] path) {
        byte[] k = new byte[32];
        k[0] = (byte) ((path[0] & 0xF0) | ((path[0] & 0x0F) ^ 1));
        return k;
    }

    private static byte[] keyWithNibble1(java.util.function.IntPredicate accept) {
        for (int i = 0; ; i++) {
            byte[] k = utf8("key-" + i);
            if (accept.test(nibble(Blake2b256.digest(k), 1))) return k;
        }
    }

    private static int nibble(byte[] path, int index) {
        int b = path[index / 2] & 0xFF;
        return index % 2 == 0 ? b >> 4 : b & 0x0F;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
