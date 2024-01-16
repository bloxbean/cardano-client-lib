package com.bloxbean.cardano.client.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class CRC8Test {

    @Test
    public void testCRC8Encoding() {
        int crc8CheckSum = CRC8.applyCRC8(222);
        assertEquals(crc8CheckSum, 0x14);
    }
    
}
