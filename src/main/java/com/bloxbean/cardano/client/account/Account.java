package com.bloxbean.cardano.client.account;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionSerializationException;
import com.bloxbean.cardano.client.jna.CardanoJNA;
import com.bloxbean.cardano.client.transaction.model.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    private String mnemonic;
    private String baseAddress;
    private String enterpriseAddress;
    private Network network;
    private int index;
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

            this.baseAddress = CardanoJNA.INSTANCE.getBaseAddressByNetwork(mnemonic, index, refNetwork);
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

            this.enterpriseAddress = CardanoJNA.INSTANCE.getEnterpriseAddressByNetwork(mnemonic, index, refNetwork);
        }

        return this.enterpriseAddress;
    }

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
    public String sign(Transaction transaction) throws CborException, TransactionSerializationException {
        String txnHex = transaction.serializeToHex();

        if(txnHex == null || txnHex.length() == 0)
            throw new TransactionSerializationException("Transaction could not be serialized");

        return CardanoJNA.INSTANCE.signPaymentTransaction(txnHex, privateKey);
    }

    public static byte[] toBytes(String address) throws AddressExcepion {
        String hexStr = CardanoJNA.INSTANCE.bech32AddressToBytes(address);
        if(hexStr == null || hexStr.length() == 0)
            throw new AddressExcepion("Address to bytes failed");

        try {
            return HexUtil.decodeHexString(hexStr);
        } catch (Exception e) {
            throw new AddressExcepion("Address to bytes failed", e);
        }
    }

    private void generateNew() {
        String mnemonic = CardanoJNA.INSTANCE.generateMnemonic();
        this.mnemonic = mnemonic;
        getPrivateKey();
    }

    private void getPrivateKey() {
        this.privateKey = CardanoJNA.INSTANCE.getPrivateKeyFromMnemonic(mnemonic, index);
    }

}
