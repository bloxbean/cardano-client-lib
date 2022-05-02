package com.bloxbean.cardano.client.cip.cip8;

public class COSEBaseTest {

    protected byte[] getBytes(int b, int noOf) {
        byte[] result = new byte[noOf];

        for (int i = 0; i < noOf; i++) {
            result[i] = (byte) b;
        }

        return result;
    }

}
