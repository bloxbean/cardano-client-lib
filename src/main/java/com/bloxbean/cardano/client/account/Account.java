package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.AddressRuntimeException;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    @JsonIgnore
    private String mnemonic;
    private String baseAddress;
    private String enterpriseAddress;
    private Network network;

    @JsonIgnore
    private DerivationPath derivationPath;

    /**
     * Create a new random mainnet account.
     */
    public Account() {
        this(Networks.mainnet(), 0);
    }

    /**
     * Create a new random mainnet account at index
     * @param index
     */
    public Account(int index) {
        this(Networks.mainnet(), index);
    }

    /**
     * Create a new random account for the network
     * @param network
     */
    public Account(Network network) {
        this(network, 0);
    }

    /**
     * Create a new random account for the network at index
     * @param network
     * @param index
     */
    public Account(Network network, int index) {
        this(network, DerivationPath.createShelleyDerivationPath(index));
    }

    /**
     * Create a new random account for the network and derivation path
     * @param network
     * @param derivationPath
     */
    public Account(Network network, DerivationPath derivationPath) {
        this.network = network;
        this.derivationPath = derivationPath;
        generateNew();
    }

    /**
     * Create a mainnet account from a mnemonic
     * @param mnemonic
     */
    public Account(String mnemonic) {
        this(Networks.mainnet(), mnemonic, 0);
    }

    /**
     * Create a mainnet account from a mnemonic at index
     * @param mnemonic
     */
    public Account(String mnemonic, int index) {
        this(Networks.mainnet(), mnemonic, index);
    }

    /**
     * Create a account for the network from a mnemonic
     * @param network
     * @param mnemonic
     */
    public Account(Network network, String mnemonic) {
        this(network, mnemonic, 0);
    }

    /**
     * Create an account for the network, mnemonic at given index
     * @param network
     * @param mnemonic
     * @param index
     */
    public Account(Network network, String mnemonic, int index) {
        this(network, mnemonic, DerivationPath.createShelleyDerivationPath(index));
    }

    /**
     * Crate an account for the network from mnemonic at index
     * @param network
     * @param mnemonic
     * @param derivationPath
     */
    public Account(Network network, String mnemonic, DerivationPath derivationPath) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.derivationPath = derivationPath;
        validateMnemonic();
        baseAddress();
    }

    /**
     *
     * @return string a 24 word mnemonic
     */
    public String mnemonic() {
        return mnemonic;
    }

    /**
     *
     * @return baseAddress at index
     */
    public String baseAddress() {
        if(this.baseAddress == null || this.baseAddress.trim().length() == 0) {
            Network.ByReference refNetwork = new Network.ByReference();
            refNetwork.network_id = network.network_id;
            refNetwork.protocol_magic = network.protocol_magic;

            this.baseAddress = CardanoJNAUtil.getBaseAddressByNetwork(mnemonic, derivationPath.getIndex().getValue(), refNetwork);
        }

        return this.baseAddress;
    }

    /**
     *
     * @return enterpriseAddress at index
     */
    public String enterpriseAddress() {
        if(this.enterpriseAddress == null || this.enterpriseAddress.trim().length() == 0) {
            Network.ByReference refNetwork = new Network.ByReference();
            refNetwork.network_id = network.network_id;
            refNetwork.protocol_magic = network.protocol_magic;

            this.enterpriseAddress = CardanoJNAUtil.getEnterpriseAddressByNetwork(mnemonic, derivationPath.getIndex().getValue(), refNetwork);
        }

        return this.enterpriseAddress;
    }

    @JsonIgnore
    public String getBech32PrivateKey() {
        return CardanoJNAUtil.getPrivateKeyFromMnemonic(mnemonic, derivationPath.getIndex().getValue());
    }

    @JsonIgnore
    public byte[] privateKeyBytes() {
        HdKeyPair hdKeyPair = getHdKeyPair();
        return hdKeyPair.getPrivateKey().getKeyData();
    }

    @JsonIgnore
    public byte[] publicKeyBytes() {
        return getHdKeyPair().getPublicKey().getKeyData();
    }

    @JsonIgnore
    public HdKeyPair hdKeyPair() {
        return getHdKeyPair();
    }

    /**
     *
     * @param txnHex
     * @return
     * @throws CborSerializationException
     */
    @Deprecated
    public String sign(String txnHex) throws CborSerializationException {
        if(txnHex == null || txnHex.length() == 0)
            throw new CborSerializationException("Invalid transaction hash");

        try {
            Transaction transaction = Transaction.deserialize(HexUtil.decodeHexString(txnHex));
            transaction = sign(transaction);
            return transaction.serializeToHex();
        } catch (CborDeserializationException e) {
            throw new CborSerializationException("Error in Cbor deserialization", e);
        }
    }

    public Transaction sign(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getHdKeyPair());
    }

    public static byte[] toBytes(String address) throws AddressExcepion {
        if(address == null)
            return null;

        String hexStr = null;
        if(address.startsWith("addr")) { //Shelley address
            hexStr = CardanoJNAUtil.bech32AddressToBytes(address);
        } else { //Try for byron address
            hexStr = CardanoJNAUtil.base58AddressToBytes(address);
        }

        if(hexStr == null || hexStr.length() == 0)
            throw new AddressExcepion("Address to bytes failed");

        try {
            return HexUtil.decodeHexString(hexStr);
        } catch (Exception e) {
            throw new AddressExcepion("Address to bytes failed", e);
        }
    }

    public static String bytesToBase58Address(byte[] bytes) throws AddressExcepion { //byron address
        String address = CardanoJNAUtil.hexBytesToBase58Address(HexUtil.encodeHexString(bytes));

        if(address == null || address.isEmpty())
            throw new AddressExcepion("Bytes cannot be converted to base58 address");

        return address;
    }

    public static String bytesToBech32(byte[] bytes) throws AddressExcepion {
        String bech32Address = CardanoJNAUtil.hexBytesToBech32Address(HexUtil.encodeHexString(bytes));
        if(bech32Address == null || bech32Address.isEmpty())
            throw new AddressExcepion("Bytes cannot be converted to bech32 address");

        return bech32Address;
    }

    private void generateNew() {
        String mnemonic = null;

        if (Configuration.INSTANCE.isUseNativeLibForAccountGen()) {
            mnemonic = CardanoJNAUtil.generateMnemonic();
        } else {
            try {
                mnemonic = MnemonicCode.INSTANCE.createMnemonic(Words.TWENTY_FOUR).stream().collect(Collectors.joining(" "));
            } catch (MnemonicException.MnemonicLengthException e) {
                throw new RuntimeException("Mnemonic generation failed", e);
            }
        }
        this.mnemonic = mnemonic;
        baseAddress();
    }

    private void validateMnemonic() {
        if (Configuration.INSTANCE.isUseNativeLibForAccountGen()) {
            CardanoJNAUtil.getPrivateKeyFromMnemonic(mnemonic, derivationPath.getIndex().getValue());
        } else {
            if (mnemonic == null) {
                throw new AddressRuntimeException("Mnemonic cannot be null");
            }

            mnemonic = mnemonic.replaceAll("\\s+", " ");
            String[] words = mnemonic.split("\\s+");

            try {
                MnemonicCode.INSTANCE.check(Arrays.asList(words));
            } catch (MnemonicException e) {
                throw new AddressRuntimeException("Invalid mnemonic phrase", e);
            }
        }
    }

    private HdKeyPair getHdKeyPair() {
        HdKeyPair hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, derivationPath);
        return hdKeyPair;
    }

    @Override
    public String toString() {
        try {
            return baseAddress();
        } catch (Exception e) {
            return null;
        }
    }
}
