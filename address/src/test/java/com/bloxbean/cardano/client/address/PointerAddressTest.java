package com.bloxbean.cardano.client.address;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointerAddressTest {

    @Test
    void getPointer() {
        PointerAddress pointerAddress = new PointerAddress("addr1gx2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer5ph3wczvf2w8lunk");
        assertEquals(new Pointer(24157, 177, 42), pointerAddress.getPointer());
    }

    @Test
    void getPointer2() {
        PointerAddress pointerAddress = new PointerAddress("addr_test1gz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzerspqgpsqe70et");
        assertEquals(new Pointer(1, 2, 3), pointerAddress.getPointer());
    }

    @Test
    void getPointer3() {
        PointerAddress pointerAddress = new PointerAddress("addr128phkx6acpnf78fuvxn0mkew3l0fd058hzquvz7w36x4gtupnz75xxcrtw79hu");
        assertEquals(new Pointer(2498243, 27, 3), pointerAddress.getPointer());
    }
}
