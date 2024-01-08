package com.bloxbean.cardano.client.util;

public class ByteUtil {
    
    public static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static int byteArrayToInt(byte[] input) {
        int value = 0;
        for(byte b : input) {
            value = (value << 8) + (b & 0xFF);
        }
        return value;
    }
}
