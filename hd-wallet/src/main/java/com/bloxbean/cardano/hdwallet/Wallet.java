package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.MnemonicUtil;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.CIP1852;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.transaction.TransactionSigner;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.hdwallet.model.WalletUtxo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Wallet class represents wallet with functionalities to manage accounts, addresses.
 */
public class Wallet {

    @Getter
    private int account = 0;
    @Getter
    private final Network network;
    @Getter
    private final String mnemonic;
    private String stakeAddress;
    private Map<Integer, Account> cache;
    private HdKeyPair rootKeys;
    private HdKeyPair stakeKeys;

    public Wallet() {
        this(Networks.mainnet());
    }

    public Wallet(Network network) {
        this(network, Words.TWENTY_FOUR);
    }

    public Wallet(Network network, Words noOfWords) {
        this(network, noOfWords, 0);
    }

    public Wallet(Network network, Words noOfWords, int account) {
        this.network = network;
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        this.account = account;
        cache = new HashMap<>();
    }

    public Wallet(String mnemonic) {
        this(Networks.mainnet(), mnemonic);
    }

    public Wallet(Network network, String mnemonic) {
        this(network,mnemonic, 0);
    }

    public Wallet(Network network, String mnemonic, int account) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.account = account;
        MnemonicUtil.validateMnemonic(this.mnemonic);
        cache = new HashMap<>();
    }

    /**
     * Get Enterpriseaddress for current account. Account can be changed via the setter.
     * @param index
     * @return
     */
    public Address getEntAddress(int index) {
        return getEntAddress(this.account, index);
    }

    /**
     * Get Enterpriseaddress for derivationpath m/1852'/1815'/{account}'/0/{index}
     * @param account
     * @param index
     * @return
     */
    private Address getEntAddress(int account, int index) {
        return getAccountObject(account, index).getEnterpriseAddress();
    }

    /**
     * Get Baseaddress for current account. Account can be changed via the setter.
     * @param index
     * @return
     */
    public Address getBaseAddress(int index) {
        return getBaseAddress(this.account, index);
    }

    /**
     * Get Baseaddress for current account as String. Account can be changed via the setter.
     * @param index
     * @return
     */
    public String getBaseAddressString(int index) {
        return getBaseAddress(index).getAddress();
    }

    /**
     * Get Baseaddress for derivationpath m/1852'/1815'/{account}'/0/{index}
     * @param account
     * @param index
     * @return
     */
    public Address getBaseAddress(int account, int index) {
        return getAccountObject(account,index).getBaseAddress();
    }

    /**
     * Returns the Account object for the index and current account. Account can be changed via the setter.
     * @param index
     * @return
     */
    public Account getAccountObject(int index) {
        return getAccountObject(this.account, index);
    }

    /**
     * Returns the Account object for the index and account.
     * @param account
     * @param index
     * @return
     */
    public Account getAccountObject(int account, int index) {
        if(account != this.account) {
            DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
            derivationPath.getIndex().setValue(index);
            return new Account(this.network, this.mnemonic, derivationPath);
        } else {
            if(cache.containsKey(index)) {
                return cache.get(index);
            } else {
                Account acc = new Account(this.network, this.mnemonic, index);
                cache.put(index, acc);
                return acc;
            }
        }
    }

    /**
     * Setting the current account for derivation path.
     * Setting the account will reset the cache.
     * @param account
     */
    public void setAccount(int account) {
        this.account = account;
        // invalidating cache since it is only held for one account
        cache = new HashMap<>();
    }

    /**
     * Returns the RootkeyPair
     * @return
     */
    public HdKeyPair getHDWalletKeyPair() {
        if(rootKeys == null) {
            HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
            try {
                byte[] entropy = MnemonicCode.INSTANCE.toEntropy(this.mnemonic);
                rootKeys = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);
            } catch (MnemonicException.MnemonicLengthException | MnemonicException.MnemonicWordException |
                     MnemonicException.MnemonicChecksumException e) {
                throw new RuntimeException(e);
            }
        }
        return rootKeys;
    }

    /**
     * Finds needed signers within wallet and signs the transaction with each one
     * @param txToSign
     * @return signed Transaction
     */
    public Transaction sign(Transaction txToSign, Set<WalletUtxo> utxos) {
        Map<String, Account> accountMap = utxos.stream()
                .map(WalletUtxo::getDerivationPath)
                .filter(Objects::nonNull)
                .map(derivationPath -> getAccountObject(
                        derivationPath.getAccount().getValue(),
                        derivationPath.getIndex().getValue()))
                .collect(Collectors.toMap(
                        Account::baseAddress,
                        Function.identity(),
                        (existing, replacement) -> existing)); // Handle duplicates if necessary

        var accounts = accountMap.values();

        if(accounts.isEmpty())
            throw new RuntimeException("No signers found!");

        for (Account account : accounts)
            txToSign = account.sign(txToSign);

        return txToSign;
    }

//
//    /**
//     * Returns a list with signers needed for this transaction
//     *
//     * @param tx
//     * @param utxoSupplier
//     * @return
//     */
//    public List<Account> getSignersForTransaction(Transaction tx, WalletUtxoSupplier utxoSupplier) {
//        return getSignersForInputs(tx.getBody().getInputs(), utxoSupplier);
//    }
//
//    private List<Account> getSignersForInputs(List<TransactionInput> inputs, WalletUtxoSupplier utxoSupplier) {
//        // searching for address to sign
//        List<Account> signers = new ArrayList<>();
//        List<TransactionInput> remaining = new ArrayList<>(inputs);
//
//        int index = 0;
//        int emptyCounter = 0;
//        while (!remaining.isEmpty() || emptyCounter >= INDEX_SEARCH_RANGE) {
//            List<WalletUtxo> utxos = utxoSupplier.getUtxosForAccountAndIndex(this.account, index);
//            emptyCounter = utxos.isEmpty() ? emptyCounter + 1 : 0;
//
//            for (Utxo utxo : utxos) {
//                if(matchUtxoWithInputs(inputs, utxo, signers, index, remaining))
//                    break;
//            }
//            index++;
//        }
//        return signers;
//    }
//
//    private boolean matchUtxoWithInputs(List<TransactionInput> inputs, Utxo utxo, List<Account> signers, int index, List<TransactionInput> remaining) {
//        for (TransactionInput input : inputs) {
//            if(utxo.getTxHash().equals(input.getTransactionId()) && utxo.getOutputIndex() == input.getIndex()) {
//                var account = getAccountObject(index);
//                var accNotFound = signers.stream()
//                        .noneMatch(acc -> account.baseAddress().equals(acc.baseAddress()));
//                if (accNotFound)
//                    signers.add(getAccountObject(index));
//                remaining.remove(input);
//            }
//        }
//        return remaining.isEmpty();
//    }

    /**
     * Returns the stake address of the wallet.
     * @return
     */
    public String getStakeAddress() {
        if (stakeAddress == null || stakeAddress.isEmpty()) {
            HdKeyPair stakeKeyPair = getStakeKeyPair();
            Address address = AddressProvider.getRewardAddress(stakeKeyPair.getPublicKey(), network);
            stakeAddress = address.toBech32();
        }
        return stakeAddress;
    }

    /**
     * Signs the transaction with stake key from wallet.
     * @param transaction
     * @return
     */
    public Transaction signWithStakeKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getStakeKeyPair());
    }

    private HdKeyPair getStakeKeyPair() {
        if(stakeKeys == null) {
            DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPathForAccount(this.account);
//                if (mnemonic == null || mnemonic.trim().length() == 0) {
//                    hdKeyPair = new CIP1852().getKeyPairFromAccountKey(this.accountKey, stakeDerivationPath); // TODO need to implement creation from key
//                } else {
//                    hdKeyPair = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);
//                }
            stakeKeys = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);
        }
        return stakeKeys;
    }


    @JsonIgnore
    public String getBech32PrivateKey() {
        HdKeyPair hdKeyPair = getHDWalletKeyPair();
        return hdKeyPair.getPrivateKey().toBech32();
    }
}
