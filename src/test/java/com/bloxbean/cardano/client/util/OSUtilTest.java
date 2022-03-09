package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.config.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OSUtilTest {

    @Test
    void isAndroid() {
        Configuration.INSTANCE.setAndroid(true);
        boolean isAndroid = OSUtil.isAndroid();
        Configuration.INSTANCE.setAndroid(false);

        assertTrue(isAndroid);
    }

    @Test
    void isAndroid_whenNotSetInConfig() {
        boolean isAndroid = OSUtil.isAndroid();

        assertFalse(isAndroid);
    }

    @Test
    void isAndroid_whenSetInJavaVMVendor() {
        String actualVendor = System.getProperty("java.vm.vendor");
        System.setProperty("java.vm.vendor", "The Android Project");
        boolean isAndroid = OSUtil.isAndroid();
        System.setProperty("java.vm.vendor", actualVendor);
        assertTrue(isAndroid);
    }
}
