package com.bloxbean.cardano.client.crypto.vrf.cardano;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class CardanoVrfInputTest {

    @Test
    void mkInputVrf_producesBlake2bOfSlotAndNonce() {
        long slot = 100;
        byte[] epochNonce = new byte[32];
        epochNonce[31] = 0x42; // a non-zero nonce

        byte[] result = CardanoVrfInput.mkInputVrf(slot, epochNonce);

        // Manually compute expected: Blake2b_256(slot_8BE || nonce)
        byte[] input = new byte[40];
        ByteBuffer.wrap(input).putLong(slot);
        System.arraycopy(epochNonce, 0, input, 8, 32);
        byte[] expected = Blake2bUtil.blake2bHash256(input);

        assertArrayEquals(expected, result);
        assertEquals(32, result.length);
    }

    @Test
    void mkInputVrf_differentSlots_produceDifferentOutputs() {
        byte[] nonce = new byte[32];
        nonce[0] = (byte) 0xFF;

        byte[] result1 = CardanoVrfInput.mkInputVrf(1000, nonce);
        byte[] result2 = CardanoVrfInput.mkInputVrf(1001, nonce);

        assertFalse(java.util.Arrays.equals(result1, result2));
    }

    @Test
    void mkInputVrf_differentNonces_produceDifferentOutputs() {
        byte[] nonce1 = new byte[32];
        byte[] nonce2 = new byte[32];
        nonce2[0] = 1;

        byte[] result1 = CardanoVrfInput.mkInputVrf(500, nonce1);
        byte[] result2 = CardanoVrfInput.mkInputVrf(500, nonce2);

        assertFalse(java.util.Arrays.equals(result1, result2));
    }

    @Test
    void mkSeedLeader_and_mkSeedNonce_produceDifferentSeeds() {
        byte[] epochNonce = new byte[32];
        epochNonce[15] = (byte) 0xAB;

        byte[] leaderSeed = CardanoVrfInput.mkSeedLeader(42, epochNonce);
        byte[] nonceSeed = CardanoVrfInput.mkSeedNonce(42, epochNonce);

        assertEquals(32, leaderSeed.length);
        assertEquals(32, nonceSeed.length);
        assertFalse(java.util.Arrays.equals(leaderSeed, nonceSeed),
                "Leader and nonce seeds must differ for same slot/nonce");
    }

    @Test
    void mkSeedLeader_matchesXorConstruction() {
        long slot = 12345;
        byte[] epochNonce = new byte[32];
        for (int i = 0; i < 32; i++) epochNonce[i] = (byte) i;

        byte[] result = CardanoVrfInput.mkSeedLeader(slot, epochNonce);

        // Manual computation
        byte[] slotNonce = new byte[40];
        ByteBuffer.wrap(slotNonce).putLong(slot);
        System.arraycopy(epochNonce, 0, slotNonce, 8, 32);
        byte[] hashA = Blake2bUtil.blake2bHash256(slotNonce);
        byte[] hashB = Blake2bUtil.blake2bHash256(new byte[]{0, 0, 0, 0, 0, 0, 0, 1});

        byte[] expected = new byte[32];
        for (int i = 0; i < 32; i++) expected[i] = (byte) (hashA[i] ^ hashB[i]);

        assertArrayEquals(expected, result);
    }

    @Test
    void mkSeedNonce_matchesXorConstruction() {
        long slot = 99999;
        byte[] epochNonce = new byte[32];
        epochNonce[0] = (byte) 0xDE;
        epochNonce[1] = (byte) 0xAD;

        byte[] result = CardanoVrfInput.mkSeedNonce(slot, epochNonce);

        // Manual computation
        byte[] slotNonce = new byte[40];
        ByteBuffer.wrap(slotNonce).putLong(slot);
        System.arraycopy(epochNonce, 0, slotNonce, 8, 32);
        byte[] hashA = Blake2bUtil.blake2bHash256(slotNonce);
        byte[] hashB = Blake2bUtil.blake2bHash256(new byte[8]); // all zeros

        byte[] expected = new byte[32];
        for (int i = 0; i < 32; i++) expected[i] = (byte) (hashA[i] ^ hashB[i]);

        assertArrayEquals(expected, result);
    }

    @Test
    void mkInputVrf_rejectsNullNonce() {
        assertThrows(IllegalArgumentException.class, () -> CardanoVrfInput.mkInputVrf(0, null));
    }

    @Test
    void mkInputVrf_rejectsWrongSizeNonce() {
        assertThrows(IllegalArgumentException.class, () -> CardanoVrfInput.mkInputVrf(0, new byte[16]));
    }

    @Test
    void mkSeedLeader_rejectsNullNonce() {
        assertThrows(IllegalArgumentException.class, () -> CardanoVrfInput.mkSeedLeader(0, null));
    }

    @Test
    void mkSeedNonce_rejectsWrongSizeNonce() {
        assertThrows(IllegalArgumentException.class, () -> CardanoVrfInput.mkSeedNonce(0, new byte[31]));
    }

    @Test
    void mkInputVrf_slot0_zeroNonce() {
        // Edge case: slot 0 with all-zero nonce
        byte[] zeroNonce = new byte[32];
        byte[] result = CardanoVrfInput.mkInputVrf(0, zeroNonce);

        // Should still produce a valid 32-byte hash
        assertEquals(32, result.length);
        assertNotNull(result);
    }

    @Test
    void mkInputVrf_maxSlot() {
        // Large slot number
        byte[] nonce = new byte[32];
        nonce[0] = 1;
        byte[] result = CardanoVrfInput.mkInputVrf(Long.MAX_VALUE, nonce);
        assertEquals(32, result.length);
    }
}
