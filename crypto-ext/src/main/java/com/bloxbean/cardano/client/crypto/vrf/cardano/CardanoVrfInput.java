package com.bloxbean.cardano.client.crypto.vrf.cardano;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;

import java.nio.ByteBuffer;

/**
 * Constructs VRF alpha inputs per Cardano protocol spec.
 * <p>
 * Praos (Babbage+) uses a single VRF evaluation with {@link #mkInputVrf(long, byte[])}.
 * TPraos (Shelley through early Babbage) uses two domain-separated VRF evaluations
 * via {@link #mkSeedLeader(long, byte[])} and {@link #mkSeedNonce(long, byte[])}.
 * <p>
 * Reference: {@code mkSeed} from {@code cardano-ledger/libs/cardano-protocol-tpraos/.../BHeader.hs}
 */
public class CardanoVrfInput {

    private static final byte[] DOMAIN_LEADER;
    private static final byte[] DOMAIN_NONCE;

    static {
        // Domain separation constants for TPraos: 8-byte big-endian integers
        // Leader: 0x0000000000000001
        // Nonce:  0x0000000000000000
        DOMAIN_LEADER = new byte[]{0, 0, 0, 0, 0, 0, 0, 1};
        DOMAIN_NONCE = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
    }

    private CardanoVrfInput() {
    }

    /**
     * Construct the VRF input for Praos (Babbage+).
     * <p>
     * Computes {@code Blake2b_256(slot_8bytes_BE || epochNonce_32bytes)}.
     *
     * @param slot       the slot number
     * @param epochNonce the epoch nonce (32 bytes)
     * @return 32-byte VRF alpha input
     */
    public static byte[] mkInputVrf(long slot, byte[] epochNonce) {
        if (epochNonce == null || epochNonce.length != 32) {
            throw new IllegalArgumentException("epochNonce must be 32 bytes");
        }

        byte[] input = new byte[8 + 32];
        ByteBuffer.wrap(input).putLong(slot);
        System.arraycopy(epochNonce, 0, input, 8, 32);
        return Blake2bUtil.blake2bHash256(input);
    }

    /**
     * Construct the VRF seed for TPraos leader value evaluation.
     * <p>
     * Computes {@code Blake2b_256(slot || epochNonce) XOR Blake2b_256(0x0000000000000001)}.
     *
     * @param slot       the slot number
     * @param epochNonce the epoch nonce (32 bytes)
     * @return 32-byte VRF alpha input for leader evaluation
     */
    public static byte[] mkSeedLeader(long slot, byte[] epochNonce) {
        return mkSeed(slot, epochNonce, DOMAIN_LEADER);
    }

    /**
     * Construct the VRF seed for TPraos nonce value evaluation.
     * <p>
     * Computes {@code Blake2b_256(slot || epochNonce) XOR Blake2b_256(0x0000000000000000)}.
     *
     * @param slot       the slot number
     * @param epochNonce the epoch nonce (32 bytes)
     * @return 32-byte VRF alpha input for nonce evaluation
     */
    public static byte[] mkSeedNonce(long slot, byte[] epochNonce) {
        return mkSeed(slot, epochNonce, DOMAIN_NONCE);
    }

    /**
     * Core TPraos seed construction: {@code Blake2b_256(slot || epochNonce) XOR Blake2b_256(domain)}.
     */
    private static byte[] mkSeed(long slot, byte[] epochNonce, byte[] domain) {
        if (epochNonce == null || epochNonce.length != 32) {
            throw new IllegalArgumentException("epochNonce must be 32 bytes");
        }

        byte[] slotNonce = new byte[8 + 32];
        ByteBuffer.wrap(slotNonce).putLong(slot);
        System.arraycopy(epochNonce, 0, slotNonce, 8, 32);

        byte[] hashA = Blake2bUtil.blake2bHash256(slotNonce);
        byte[] hashB = Blake2bUtil.blake2bHash256(domain);

        byte[] result = new byte[32];
        for (int i = 0; i < 32; i++) {
            result[i] = (byte) (hashA[i] ^ hashB[i]);
        }
        return result;
    }
}
