package com.bloxbean.cardano.client.plutus.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytesTest {

    @Test
    void concat() {
        byte[] b1 = new byte[] {1,2,3};
        byte[] b2 = new byte[] {4,5,6, 7, 8, 9};
        byte[] b3 = new byte[] {10, 11};

        byte[] b = Bytes.concat(b1, b2, b3);

        assertEquals(11, b.length);
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8,9,10,11}, b);
    }
}
