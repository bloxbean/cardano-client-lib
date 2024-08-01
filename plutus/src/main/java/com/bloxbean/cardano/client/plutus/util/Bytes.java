package com.bloxbean.cardano.client.plutus.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Bytes {
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

    public static List<byte[]> getChunks(byte[] b, int maxChunkSize) {
        List<byte[]> result = new ArrayList<>();

        ByteBuffer buffer = ByteBuffer.wrap(b);

        while (buffer.remaining() > 0) {
            int chunkSize = Math.min(buffer.remaining(), maxChunkSize);
            byte[] chunk = new byte[chunkSize];
            buffer.get(chunk);
            result.add(chunk);
        }

        return result;
    }
}
