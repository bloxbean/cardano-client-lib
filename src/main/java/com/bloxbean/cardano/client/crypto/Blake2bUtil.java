package com.bloxbean.cardano.client.crypto;

import org.bouncycastle.crypto.digests.Blake2bDigest;

public class Blake2bUtil {

    public static byte[] blake2bHash160(byte[] in) {
        final Blake2bDigest hash = new Blake2bDigest(null, 20, null, null);
        hash.update(in, 0, in.length);
        final byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }

    public static byte[] blake2bHash224(byte[] in) {
        final Blake2bDigest hash = new Blake2bDigest(null, 28, null, null);
        hash.update(in, 0, in.length);
        final byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }

    public static byte[] blake2bHash256(byte[] in) {
        final Blake2bDigest hash = new Blake2bDigest(null, 32, null, null);
        hash.update(in, 0, in.length);
        final byte[] out = new byte[hash.getDigestSize()];
        hash.doFinal(out, 0);
        return out;
    }
}
