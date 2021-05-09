package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.util.LibraryUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = (CardanoJNA)
            Native.load(LibraryUtil.getCardanoWrapperLib(),
                    CardanoJNA.class);

    public Pointer getBaseAddress(String phrase, int index, boolean isTestnet);
    public Pointer getBaseAddressByNetwork(String phrase, int index, Network.ByReference network);
    public Pointer getEnterpriseAddress(String phrase, int index, boolean isTestnet);
    public Pointer getEnterpriseAddressByNetwork(String phrase, int index, Network.ByReference network);
    public Pointer generateMnemonic();

    /**
     * Return private key in bech32
     * @param phrase
     * @param index
     * @return
     */
    public Pointer getPrivateKeyFromMnemonic(String phrase, int index);

    /**
     * Returns hex encoded string
     */
    public Pointer bech32AddressToBytes(String bech32Address);

    /**
     * Return signed transaction bytes in base64 encoding
     * @param rawTxnInHex
     * @param privateKey
     * @return
     */
    public Pointer sign(String rawTxnInHex, String privateKey);

    public void dropCharPointer(Pointer pointer);

    public void printPointer(Pointer pointer);
}
