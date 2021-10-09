package com.bloxbean.cardano.client.util;

import com.sun.jna.Platform;

public class LibraryUtil {
    public static final String CARDANO_CLIENT_NATIVE_LIB_NAME = "cardano_client_native_lib_name";

    public static String getCardanoWrapperLib() {
        String customNativeLib = getCustomNativeLibName();
        if(customNativeLib != null && !customNativeLib.isEmpty())
            return customNativeLib;

        String libName = "libcardano_jni_wrapper";

        if (Platform.isMac()) {
            libName += ".dylib";
        } else if (Platform.isAndroid()) {
            libName = "cardano_jni_wrapper";
        } else if (Platform.isLinux() || Platform.isFreeBSD() || Platform.isAIX()) {
            libName += ".so";
        } else if (Platform.isWindows() || Platform.isWindowsCE()) {
            libName = "cardano_jni_wrapper.dll";
        }

        return libName;
    }

    private static String getCustomNativeLibName() {
        String nativeLibName = System.getProperty(CARDANO_CLIENT_NATIVE_LIB_NAME);
        if(nativeLibName == null || nativeLibName.isEmpty()) {
            nativeLibName = System.getenv(CARDANO_CLIENT_NATIVE_LIB_NAME);
        }

        return nativeLibName;
    }
}
