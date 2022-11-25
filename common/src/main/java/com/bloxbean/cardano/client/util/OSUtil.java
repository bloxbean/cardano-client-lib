package com.bloxbean.cardano.client.util;

public class OSUtil {
    private static boolean android = false;

    public static void setAndroid(boolean flag) {
        android = flag;
    }

    public static boolean isAndroid() {
        //If isAndroid is set programmatically
        if (android)
            return true;

        String javaVendor = System.getProperty("java.vm.vendor");

        if (javaVendor != null && "The Android Project".equalsIgnoreCase(javaVendor)) {
            return true;
        } else {
            return false;
        }
    }
}
