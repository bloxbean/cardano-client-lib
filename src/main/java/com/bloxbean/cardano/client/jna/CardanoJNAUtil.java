package com.bloxbean.cardano.client.jna;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
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

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
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
     * Returns private key in bech32
     * @param phrase
     * @param index
     * @return
     */
    public static String getPrivateKeyFromMnemonic(String phrase, int index) {
        Pointer pointer = CardanoJNA.INSTANCE.getPrivateKeyFromMnemonic(phrase, index);
        String result = pointer.getString(0);

        try {
            if (result == null || result.isEmpty()) {
                throw new AddressRuntimeException("Unable to get private key from mnemonic phrase");
            } else {
                return result;
            }
        } finally {
            CardanoJNA.INSTANCE.dropCharPointer(pointer);
        }
    }

    /**
     * Returns private key raw bytes
     * @param phrase
     * @param index
     * @return
     */
    public static byte[] getPrivateKeyBytesFromMnemonic(String phrase, int index) {
        Pointer pointer = CardanoJNA.INSTANCE.getPrivateKeyBytesFromMnemonic(phrase, index);
        try {
            String result = pointer.getString(0);

            if (result == null || result.isEmpty()) {
                throw new AddressRuntimeException("Unable to get private key bytes from mnemonic phrase");
            } else {
                return HexUtil.decodeHexString(result);
            }
        }finally {
            CardanoJNA.INSTANCE.dropCharPointer(pointer);
        }
    }

    /**
     * Returns public key raw bytes
     * @param phrase
     * @param index
     * @return
     */
    public static byte[] getPublicKeyBytesFromMnemonic(String phrase, int index) {
        Pointer pointer = CardanoJNA.INSTANCE.getPublicKeyBytesFromMnemonic(phrase, index);
        try {
            String result = pointer.getString(0);

            if (result == null || result.isEmpty()) {
                throw new AddressRuntimeException("Unable to get public key bytes from mnemonic phrase");
            } else {
                return HexUtil.decodeHexString(result);
            }
        }finally {
            CardanoJNA.INSTANCE.dropCharPointer(pointer);
        }
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
     * Return hex encoded string (bytes) for a base58 (byron) address
     * @param base58Address
     * @return
     */
    public static String base58AddressToBytes(String base58Address) {
        Pointer pointer = CardanoJNA.INSTANCE.base58AddressToBytes(base58Address);
        String result = pointer.getString(0);

        CardanoJNA.INSTANCE.dropCharPointer(pointer);
        return result;
    }

    /**
     * Return base58(byron) address
     * @param addressBytesInHex
     * @return
     */
    public static String hexBytesToBase58Address(String addressBytesInHex) {
        Pointer pointer = CardanoJNA.INSTANCE.hexBytesToBase58Address(addressBytesInHex);
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
