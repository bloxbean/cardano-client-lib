package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.common.model.Network;
import com.sun.jna.Pointer;

public class CardanoJNAUtil {

    public static String getBaseAddress(String phrase, int index, boolean isTestnet) {
        Pointer pointer = CardanoJNA.INSTANCE.getBaseAddress(phrase, index, isTestnet);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    public static String getBaseAddressByNetwork(String phrase, int index, Network.ByReference network) {
        Pointer pointer = CardanoJNA.INSTANCE.getBaseAddressByNetwork(phrase, index, network);
        String result = pointer.getString(0);
        return result;
    }

    public static String getEnterpriseAddress(String phrase, int index, boolean isTestnet) {
        Pointer pointer = CardanoJNA.INSTANCE.getEnterpriseAddress(phrase, index, isTestnet);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    public static String getEnterpriseAddressByNetwork(String phrase, int index, Network.ByReference network) {
        Pointer pointer = CardanoJNA.INSTANCE.getEnterpriseAddressByNetwork(phrase, index, network);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }
    public static String generateMnemonic() {
        Pointer pointer = CardanoJNA.INSTANCE.generateMnemonic();
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Return private key in bech32
     * @param phrase
     * @param index
     * @return
     */
    public static String getPrivateKeyFromMnemonic(String phrase, int index) {
        Pointer pointer = CardanoJNA.INSTANCE.getPrivateKeyFromMnemonic(phrase, index);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Returns hex encoded string
     */
    public static String bech32AddressToBytes(String bech32Address) {
        Pointer pointer = CardanoJNA.INSTANCE.bech32AddressToBytes(bech32Address);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Return bech32 address
     * @param addressBytesInHex
     * @return
     */
    public static String hexBytesToBech32Address(String addressBytesInHex) {
        Pointer pointer = CardanoJNA.INSTANCE.hexBytesToBech32Address(addressBytesInHex);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Return signed transaction bytes in hex
     * @param rawTxnInHex
     * @param privateKey
     * @return
     */
    public static String sign(String rawTxnInHex, String privateKey) {
        Pointer pointer = CardanoJNA.INSTANCE.sign(rawTxnInHex, privateKey);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Return signed transaction bytes in hex
     * @param rawTxnInHex
     * @param privateKey
     * @return
     */
    public static String signWithSecretKey(String rawTxnInHex, String privateKey) {
        Pointer pointer = CardanoJNA.INSTANCE.signWithSecretKey(rawTxnInHex, privateKey);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Validate if CBOR is valid for the transaction. Used only in Tests
     * @param rawTxnInHex
     * @return
     */
    public static boolean validateTransactionCBOR(String rawTxnInHex) {
        return CardanoJNA.INSTANCE.validateTransactionCBOR(rawTxnInHex);
    }

}
