package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;

import java.util.List;

public interface WalletInterface {

//    private String mnemonic = "";
//    public HDWalletInterface(Network network, String mnemonic);
//    public HDWalletInterface(Network network);

    // sum up all amounts within this wallet
    // scanning strategy is as described in specification.md
    public List<Amount> getWalletBalance();

    /**
     * Returns the account for the given index
     * @param index
     * @return
     */
    public Account getAccount(int index);

    /**
     * Creates a new Account for the first empty index.
     * @return
     */
    public Account newAccount();

    /**
     * Returns the stake address
     * @return
     */
    public String getStakeAddress();

    /**
     * returns the master private key
     * @return
     */
    public byte[] getMasterPrivateKey();

    /**
     * Returns the master public key
     * @return
     */
    public String getMasterPublicKey();

    /**
     * returns the master adress from where other addresses can be derived
     * @return
     */
    public String getMasterAddress(); // prio 2

}
