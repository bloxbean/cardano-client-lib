package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.util.LibraryUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = (CardanoJNA)
            Native.load(LibraryUtil.getCardanoWrapperLib(),
                    CardanoJNA.class);

    public Pointer signExtended(String msg, String expandedPrivateKeyInHex, String publicKeyHex);

    public void dropCharPointer(Pointer pointer);

    public void printPointer(Pointer pointer);
}
