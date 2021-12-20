package com.bloxbean.cardano.client.jna;

import com.sun.jna.Pointer;

public class CardanoJNAUtil {

    /**
     * Sign a message with expanded secret key (ed25519 expanded key)
     * @param msgInHex
     * @param expandedPrivateKeyHex
     * @param publicKeyHex
     * @return
     */
    public static String signExtended(String msgInHex, String expandedPrivateKeyHex, String publicKeyHex) {
        Pointer pointer = CardanoJNA.INSTANCE.signExtended(msgInHex, expandedPrivateKeyHex, publicKeyHex);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

}
