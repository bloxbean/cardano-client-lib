package com.bloxbean.cardano.client.crypto;

import org.bouncycastle.crypto.digests.Blake2bDigest;

/**
 * CCL's class with one defect for the sensitivity test: a hash asked for by Yaci's header reader comes back with its
 * first bit flipped, so a side with this class first differs from the real one in exactly {@code blockHash}.
 */
public class Blake2bUtil {
    private static final StackWalker WALKER = StackWalker.getInstance();

    public static byte[] blake2bHash160(byte[] in) {
        return hash(in, 20);
    }

    public static byte[] blake2bHash224(byte[] in) {
        return hash(in, 28);
    }

    public static byte[] blake2bHash256(byte[] in) {
        byte[] out = hash(in, 32);
        boolean fromHeaderReader = WALKER.walk(frames -> frames.skip(1).findFirst()).orElseThrow().getClassName()
                .endsWith(".BlockHeaderSerializer");
        if (fromHeaderReader)
            out[0] ^= (byte) 0x80;
        return out;
    }

    private static byte[] hash(byte[] in, int size) {
        Blake2bDigest hash = new Blake2bDigest(null, size, null, null);
        hash.update(in, 0, in.length);
        byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }
}
