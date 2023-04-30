package com.bloxbean.cardano.client.plutus.util;

import java.nio.ByteBuffer;

class Bytes {
    public static byte[] concat(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(totalLength);
        for (byte[] array : arrays) {
            byteBuffer.put(array);
        }

        return byteBuffer.array();
    }
}
