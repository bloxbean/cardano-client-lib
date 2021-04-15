package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.util.LibraryUtil;
import com.bloxbean.cardano.client.util.Network;
import com.sun.jna.Library;
import com.sun.jna.Native;

public interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = (CardanoJNA)
            Native.load(LibraryUtil.getCardanoWrapperLib(),
                    CardanoJNA.class);

    public String get_address(String phrase, int index, boolean isTestnet);
    public String get_address_by_network(String phrase, int index, Network.ByReference network);
    public String generate_mnemonic();
}
