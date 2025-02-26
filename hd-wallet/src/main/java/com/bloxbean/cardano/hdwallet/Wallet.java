package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.WalletUtxo;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Optional;
import java.util.Set;

/**
 * An interface representing a Wallet for managing addresses, accounts,
 * keys, and transactions. It provides methods to retrieve addresses, manage accounts,
 * sign transactions, and configure wallet settings.
 */
public interface Wallet {

    /**
     * Retrieves the enterprise address associated with the specified index.
     *
     * @param index the index of the enterprise address to be retrieved
     * @return the enterprise address corresponding to the given index
     */
    Address getEntAddress(int index);

    /**
     * Retrieves the base address associated with the specified index.
     *
     * @param index the index of the base address to be retrieved
     * @return the base address corresponding to the given index
     */
    Address getBaseAddress(int index);

    /**
     * Retrieves the base address as string associated with the specified index.
     *
     * @param index the index of the base address string to be retrieved
     * @return the base address string corresponding to the given index
     */
    String getBaseAddressString(int index);

    /**
     * Retrieves the base address associated with the specified account and index.
     *
     * @param account the account number for which the base address is to be retrieved
     * @param index the index of the base address to be retrieved
     * @return the base address corresponding to the specified account and index
     */
    Address getBaseAddress(int account, int index);

    /**
     * Retrieves the stake address associated with the Wallet instance.
     *
     * @return the stake address as a String
     */
    String getStakeAddress();

    /**
     * Returns the Account object for the index and current account. Account can be changed via the setter.
     *
     * @param index the address index of the account to be retrieved
     * @return the account corresponding to the given index
     */
    Account getAccountAtIndex(int index);

    /**
     * Returns the Account object for the index and account.
     *
     * @param account the account number for which the account is to be retrieved
     * @param index the index of the account to be retrieved
     * @return the account corresponding to the specified account number and index
     */
    Account getAccount(int account, int index);

    /**
     * Sets the account number for this Wallet instance.
     *
     * @param account the account number to be set
     */
    void setAccountNo(int account);

    /**
     * Retrieves the account number associated with this Wallet instance.
     *
     * @return the account number as an integer
     */
    int getAccountNo();

    /**
     * Retrieves the root HD key pair associated with this Wallet instance.
     *
     * @return an {@code Optional} containing the root HD key pair if available, or an empty {@code Optional} if not set
     */
    Optional<HdKeyPair> getRootKeyPair();

    /**
     * Retrieves the root private key associated with this Wallet instance.
     *
     * @return an {@code Optional} containing the root private key as a byte array if available,
     *         or an empty {@code Optional} if the root private key is not set.
     */
    Optional<byte[]> getRootPvtKey();

    /**
     * Retrieves the mnemonic phrase associated with the Wallet instance.
     *
     * @return the mnemonic phrase as a String
     */
    String getMnemonic();

    /**
     * Signs a transaction using the wallet's private keys and the provided set of unspent transaction outputs (UTXOs).
     * This method generates the necessary signatures to authorize the transaction.
     *
     * @param txToSign the transaction object to be signed
     * @param utxos the set of UTXOs used in the transaction
     * @return the signed transaction object
     */
    Transaction sign(Transaction txToSign, Set<WalletUtxo> utxos);

    /**
     * Signs the provided transaction using the wallet's stake key.
     *
     * @param transaction the transaction to be signed
     * @return the transaction signed with the wallet's stake key
     */
    Transaction signWithStakeKey(Transaction transaction);

    /**
     * Retrieves the network associated with this Wallet instance.
     *
     * @return the {@code Network} object representing the network configuration for the Wallet
     */
    Network getNetwork();

    /**
     * Checks if the wallet is configured to search unspent transaction outputs (UTXOs)
     * using an address VKH (verification key hash).
     *
     * @return {@code true} if search by address VKH is enabled; {@code false} otherwise
     */
    boolean isSearchUtxoByAddrVkh();

    /**
     * Configures whether the wallet should search for unspent transaction outputs (UTXOs)
     * using an address verification key hash (VKH).
     *
     * @param searchUtxoByAddrVkh a boolean value indicating whether to enable or disable
     *                            the search for UTXOs by address VKH. Set to {@code true}
     *                            to enable the search, or {@code false} to disable it.
     */
    void setSearchUtxoByAddrVkh(boolean searchUtxoByAddrVkh);

    /**
     * Retrieves the array of indexes to be scanned by the wallet.
     *
     * @return an integer array containing the indexes to scan
     */
    int[] getIndexesToScan();

    /**
     * Configures the indexes that need to be scanned.
     *
     * @param indexesToScan an array of integers representing the specific indexes to scan
     */
    void setIndexesToScan(int[] indexesToScan);

    /**
     * Retrieves the gap limit value.
     *
     * @return An integer representing the gap limit.
     */
    int getGapLimit();

    /**
     * Sets the gap limit value used for generating or validating keychains.
     * The gap limit defines the maximum number of unused keys or addresses
     * that can be scanned or generated ahead without invalidating the current state.
     *
     * @param gapLimit the maximum number of unused keys or addresses allowed.
     */
    void setGapLimit(int gapLimit);

    //-- static methods to create DefaultWallet

    /**
     * Creates a new instance of the {@link DefaultWallet}.
     *
     * @return a new Wallet instance of type DefaultWallet
     */
    static Wallet create() {
        return new DefaultWallet();
    }

    /**
     * Creates a new Wallet instance for the specified network.
     *
     * @param network the network to be used for the Wallet, e.g., a mainnet or testnet instance
     * @return a new Wallet instance of type DefaultWallet configured for the specified network
     */
    static Wallet create(Network network) {
        return new DefaultWallet(network);
    }

    /**
     * Creates a new Wallet instance using the specified network and mnemonic length.
     *
     * @param network the network to be used, e.g., mainnet or testnet
     * @param noOfWords the number of words representing the wallet's mnemonic strength,
     *                  defined by the Words enum (e.g., TWELVE, FIFTEEN, TWENTY_FOUR)
     * @return a new Wallet instance of type DefaultWallet configured with the specified network and mnemonic strength
     */
    static Wallet create(Network network, Words noOfWords) {
        return new DefaultWallet(network, noOfWords, 0);
    }

    /**
     * Creates a new Wallet instance for the specified network, mnemonic strength, and account number.
     *
     * @param network the network to be used for the Wallet, e.g., mainnet or testnet
     * @param noOfWords the number of words representing the wallet's mnemonic strength,
     *                  defined by the Words enum (e.g., TWELVE, FIFTEEN, TWENTY_FOUR)
     * @param account the account number to be used for wallet derivation
     * @return a new Wallet instance of type DefaultWallet configured for the specified network, mnemonic strength, and account number
     */
    static Wallet create(Network network, Words noOfWords, int account) {
        return new DefaultWallet(network, noOfWords, account);
    }

    /**
     * Creates a Wallet instance from the given mnemonic for mainnet.
     * The account is set to zero.
     *
     * @param mnemonic the mnemonic phrase
     * @return a new Wallet instance of type DefaultWallet created from the provided mnemonic
     */
    static Wallet createFromMnemonic(String mnemonic) {
        return createFromMnemonic(Networks.mainnet(), mnemonic, 0);
    }

    /**
     * Creates a Wallet instance using the specified network and mnemonic phrase.
     * The account is set to zero.
     *
     * @param network the network to be used, e.g., Networks.mainnet(), Networks.testnet()
     * @param mnemonic the mnemonic phrase
     * @return a new Wallet instance of type DefaultWallet created from the provided mnemonic
     */
    static Wallet createFromMnemonic(Network network, String mnemonic) {
        return createFromMnemonic(network, mnemonic, 0);
    }

    /**
     * Creates a Wallet instance using the specified network, mnemonic phrase, and account number.
     *
     * @param network the network to be used, e.g., Networks.mainnet(), Networks.testnet()
     * @param mnemonic the mnemonic phrase
     * @param account the account no to be used for wallet derivation
     * @return a new Wallet instance of type DefaultWallet created from the provided mnemonic
     */
    static Wallet createFromMnemonic(Network network, String mnemonic, int account) {
        return new DefaultWallet(network, mnemonic, null, null, account);
    }

    /**
     * Creates a Wallet instance using the specified network and root key.
     * The account is set to zero by default.
     *
     * @param network the network to be used, e.g., Networks.mainnet(), Networks.testnet()
     * @param rootKey the root key used for wallet initialization
     * @return a new Wallet instance of type DefaultWallet created from the provided root key
     */
    static Wallet createFromRootKey(Network network, byte[] rootKey) {
        return createFromRootKey(network, rootKey, 0);
    }

    /**
     * Creates a Wallet instance using the specified network, root key, and account number.
     *
     * @param network the network to be used, e.g., Networks.mainnet(), Networks.testnet()
     * @param rootKey the root key used for wallet initialization
     * @param account the account number to be used for wallet derivation
     * @return a new Wallet instance of type DefaultWallet created from the provided root key
     */
    static Wallet createFromRootKey(Network network, byte[] rootKey, int account) {
        return new DefaultWallet(network, null, rootKey, null, account);
    }

    /**
     * Creates a Wallet instance using the specified network and account level key.
     *
     * @param network the network to be used, e.g., Networks.mainnet(), Networks.testnet()
     * @param accountKey the account key used for wallet initialization
     * @return a new Wallet instance of type DefaultWallet created from the provided account key
     */
    static Wallet createFromAccountKey(Network network, byte[] accountKey) {
        return new DefaultWallet(network, null, null, accountKey, 0);
    }
}
