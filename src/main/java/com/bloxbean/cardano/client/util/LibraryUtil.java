package com.bloxbean.cardano.client.util;

public class LibraryUtil {

    private static String OS = System.getProperty("os.name").toLowerCase();

    public static String getCardanoWrapperLib() {
        String libName = "libcardano_jni_wrapper";
        if(isMac()) {
            libName += ".dylib";
        } else if(isUnix()) {
            libName += ".so";
        } else if(isWindows()) {
            libName = "cardano_jni_wrapper.dll";
        }

        return libName;
    }

    public static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    public static boolean isMac() {
        return (OS.indexOf("mac") >= 0);
    }

    public static boolean isUnix() {
        return (OS.indexOf("nix") >= 0
                || OS.indexOf("nux") >= 0
                || OS.indexOf("aix") > 0);
    }

    public static boolean isSolaris() {
        return (OS.indexOf("sunos") >= 0);
    }

}
