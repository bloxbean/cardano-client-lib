package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.util.LibraryUtil;
import com.bloxbean.cardano.client.util.NativeUtils;

import java.io.File;
import java.io.IOException;

public class CardanoNative {

    public static native String getBaseAddressFromMnemonic(String input, int index, boolean isTestnet);

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        try {
            String libPath = System.getProperty("cardano.lib.path");
            if(libPath != null && libPath.trim().length() != 0) {
                System.load(libPath + File.separator + LibraryUtil.getCardanoWrapperLib());
            } else {
                try {
                    NativeUtils.loadLibraryFromJar("/" + LibraryUtil.getCardanoWrapperLib());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (Error e) {
            e.printStackTrace();
        }
    }

    // The rest is just regular ol' Java!
    public static void main(String[] args) {
        //String mnemonic1 = "test walk nut penalty hip pave soap entry language right filter choice";

        String phrase = "coconut you order found animal inform tent anxiety pepper aisle web horse source indicate eyebrow viable lawsuit speak dragon scheme among animal slogan exchange";

        String output = CardanoNative.getBaseAddressFromMnemonic(phrase, 0, false);
        System.out.println(output);
    }

}
