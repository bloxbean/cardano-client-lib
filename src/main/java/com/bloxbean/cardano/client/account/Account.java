package com.bloxbean.cardano.client.account;

import com.bloxbean.cardano.client.jna.CardanoJNA;
import com.bloxbean.cardano.client.util.Network;
import com.bloxbean.cardano.client.util.Networks;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    private String mnemonic;
    private String baseAddress;
    private Network network;

    /**
     * Create a new random mainnet account.
     */
    public Account() {
        this.network = Networks.mainnet();
        generateNew();
    }

    /**
     * Create a new random account for the network
     * @param network
     */
    public Account(Network network) {
        this.network = network;
        generateNew();
    }

    /**
     * Create a mainnet account from a mnemonic
     * @param mnemonic
     */
    public Account(String mnemonic) {
        this.network = Networks.mainnet();
        this.mnemonic = mnemonic;
    }

    /**
     * Create a account for the network from a mnemonic
     * @param network
     * @param mnemonic
     */
    public Account(Network network, String mnemonic) {
        this.network = network;
        this.mnemonic = mnemonic;
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
     * @param index
     * @return baseAddress at index
     */
    public String baseAddress(int index) {
        Network.ByReference refNetwork = new Network.ByReference();
        refNetwork.network_id = network.network_id;
        refNetwork.protocol_magic = network.protocol_magic;

        return CardanoJNA.INSTANCE.get_address_by_network(mnemonic, index, refNetwork);
    }

    private void generateNew() {
        String mnemonic = CardanoJNA.INSTANCE.generate_mnemonic();
        this.mnemonic = mnemonic;
    }
}
