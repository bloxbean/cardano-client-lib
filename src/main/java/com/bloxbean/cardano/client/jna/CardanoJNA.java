package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.util.LibraryUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.sun.jna.Library;
import com.sun.jna.Native;

public interface CardanoJNA extends Library {
    CardanoJNA INSTANCE = (CardanoJNA)
            Native.load(LibraryUtil.getCardanoWrapperLib(),
                    CardanoJNA.class);

    public String getBaseAddress(String phrase, int index, boolean isTestnet);
    public String getBaseAddressByNetwork(String phrase, int index, Network.ByReference network);
    public String getEnterpriseAddress(String phrase, int index, boolean isTestnet);
    public String getEnterpriseAddressByNetwork(String phrase, int index, Network.ByReference network);
    public String generateMnemonic();

    /**
     * Return private key in bech32
     * @param phrase
     * @param index
     * @return
     */
    public String getPrivateKeyFromMnemonic(String phrase, int index);

    /**
     * Returns hex encoded string
     */
    public String bech32AddressToBytes(String bech32Address);

    /**
     * Return signed transaction bytes in base64 encoding
     * @param rawTxnInHex
     * @param privateKey
     * @return
     */
    public String signPaymentTransaction(String rawTxnInHex, String privateKey);
}
