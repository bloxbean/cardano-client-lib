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
     * Returns private key in bech32
     * @param phrase
     * @param index
     * @return
     */
    public Pointer getPrivateKeyFromMnemonic(String phrase, int index);

    /**
     * Returns private key bytes in hex
     * @param phrase
     * @param index
     * @return
     */
    public Pointer getPrivateKeyBytesFromMnemonic(String phrase, int index);

    /**
     * Returns public key bytes in hex
     * @param phrase
     * @param index
     * @return
     */
    public Pointer getPublicKeyBytesFromMnemonic(String phrase, int index);

    /**
     * Returns hex encoded string
     */
    public Pointer bech32AddressToBytes(String bech32Address);

    /**
     * Return bech32 address
     * @param addressBytesInHex
     * @return
     */
    public Pointer hexBytesToBech32Address(String addressBytesInHex);

    /**
     * Return hex encoded string (bytes) for a base58 (byron) address
     * @param base58Address
     * @return
     */
    public Pointer base58AddressToBytes(String base58Address);

    /**
     * Return base58(byron) address
     * @param addressBytesInHex
     * @return
     */
    public Pointer hexBytesToBase58Address(String addressBytesInHex);

    /**
     * Return signed transaction bytes in hex
     * @param rawTxnInHex
     * @param privateKey
     * @return
     */
    public Pointer sign(String rawTxnInHex, String privateKey);

    /**
     * Return signed transaction bytes in hex
     * @param rawTxnInHex
     * @param secretKeyHex
     * @return
     */
    public Pointer signWithSecretKey(String rawTxnInHex, String secretKeyHex);

    public boolean validateTransactionCBOR(String rawTxnInHex);

    /**
     * Sign a message with a private key
     * @param msg
     * @param privateKeyHex
     * @return Signature
     */
    public Pointer signMsg(String msg, String privateKeyHex);

    public void dropCharPointer(Pointer pointer);

    public void printPointer(Pointer pointer);
}
