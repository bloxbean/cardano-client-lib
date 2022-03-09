package com.bloxbean.cardano.client.util;

import com.bloxbean.cardano.client.config.Configuration;

public class OSUtil {

    public static boolean isAndroid() {
        //If isAndroid is set programmatically
        if (Configuration.INSTANCE.isAndroid())
            return true;

        String javaVendor = System.getProperty("java.vm.vendor");

        if (javaVendor != null && "The Android Project".equalsIgnoreCase(javaVendor)) {
            return true;
        } else {
            return false;
        }
    }
}
