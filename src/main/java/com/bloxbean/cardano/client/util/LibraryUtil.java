package com.bloxbean.cardano.client.util;

import com.sun.jna.Platform;

public class LibraryUtil {
    public static String getCardanoWrapperLib() {
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
}
