package com.bloxbean.cardano.client.account;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.jna.CardanoJNAUtil;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    @JsonIgnore
    private String mnemonic;
    private String baseAddress;
    private String enterpriseAddress;
    private Network network;
    private int index;
    @JsonIgnore
    private String privateKey; //hex value

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
        this.network = network;
        this.index = index;
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
     * Crate an account for the network from mnemonic at index
     * @param network
     * @param mnemonic
     * @param index
     */
    public Account(Network network, String mnemonic, int index) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.index = index;
        getPrivateKey();
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

            this.baseAddress = CardanoJNAUtil.getBaseAddressByNetwork(mnemonic, index, refNetwork);
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

            this.enterpriseAddress = CardanoJNAUtil.getEnterpriseAddressByNetwork(mnemonic, index, refNetwork);
        }

        return this.enterpriseAddress;
    }

    @JsonIgnore
    public String getBech32PrivateKey() {
        return privateKey;
    }

    /**
     * Sign a raw transaction with this account's private key
     * @param transaction
     * @return
     * @throws CborException
     * @throws TransactionSerializationException
     */
    public String sign(Transaction transaction) throws CborException, TransactionSerializationException, AddressExcepion {
        String txnHex = transaction.serializeToHex();

        if(txnHex == null || txnHex.length() == 0)
            throw new TransactionSerializationException("Transaction could not be serialized");

        return CardanoJNAUtil.sign(txnHex, privateKey);
    }

    /**
     *
     * @param txnHex
     * @return
     * @throws TransactionSerializationException
     */
    public String sign(String txnHex) throws TransactionSerializationException {
        if(txnHex == null || txnHex.length() == 0)
            throw new TransactionSerializationException("Invalid transaction hash");

        return CardanoJNAUtil.sign(txnHex, privateKey);
    }

    public static byte[] toBytes(String address) throws AddressExcepion {
        String hexStr = CardanoJNAUtil.bech32AddressToBytes(address);
        if(hexStr == null || hexStr.length() == 0)
            throw new AddressExcepion("Address to bytes failed");

        try {
            return HexUtil.decodeHexString(hexStr);
        } catch (Exception e) {
            throw new AddressExcepion("Address to bytes failed", e);
        }
    }

    private void generateNew() {
        String mnemonic = CardanoJNAUtil.generateMnemonic();
        this.mnemonic = mnemonic;
        getPrivateKey();
        baseAddress();
    }

    private void getPrivateKey() {
        this.privateKey = CardanoJNAUtil.getPrivateKeyFromMnemonic(mnemonic, index);
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
