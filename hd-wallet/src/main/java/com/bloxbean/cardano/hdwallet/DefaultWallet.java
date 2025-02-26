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
import com.bloxbean.cardano.client.api.model.WalletUtxo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Wallet class represents wallet with functionalities to manage accounts, addresses.
 */
public class DefaultWallet implements Wallet {

    @Getter
    private int accountNo = 0;
    @Getter
    private final Network network;

    @Getter
    @JsonIgnore
    private String mnemonic;

    @JsonIgnore
    private byte[] rootKey; //Pvt key at root level m/

    @JsonIgnore
    private byte[] accountKey; //Pvt key at account level m/1852'/1815'/x

    private String stakeAddress;
    private Map<Integer, Account> cache;
    private HdKeyPair rootKeyPair;
    private HdKeyPair stakeKeys;

    @Getter
    @Setter
    private boolean searchUtxoByAddrVkh;

    @Getter
    @Setter
    private int[] indexesToScan; //If set, only scan these indexes and avoid gap limit during address scanning

    @Getter
    @Setter
    private int gapLimit = 20; //No of unused addresses to scan.

    public DefaultWallet() {
        this(Networks.mainnet());
    }

    public DefaultWallet(Network network) {
        this(network, Words.TWENTY_FOUR);
    }

    public DefaultWallet(Network network, Words noOfWords) {
        this(network, noOfWords, 0);
    }

    public DefaultWallet(Network network, Words noOfWords, int account) {
        this.network = network;
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        this.accountNo = account;
        cache = new HashMap<>();
    }

    /**
     * Create a Wallet object from given mnemonic or rootKey or accountKey
     * Only one of these value should be set : mnemonic or rootKey or accountKey
     * @param network network
     * @param mnemonic mnemonic
     * @param rootKey root key
     * @param accountKey account level key
     * @param account account number
     */
    protected DefaultWallet(Network network, String mnemonic, byte[] rootKey, byte[] accountKey, int account) {
        //check if more than one value set and throw exception
        if ((mnemonic != null && !mnemonic.isEmpty() ? 1 : 0) +
                (rootKey != null && rootKey.length > 0 ? 1 : 0) +
                (accountKey != null && accountKey.length > 0 ? 1 : 0) > 1) {
            throw new WalletException("Only one of mnemonic, rootKey, or accountKey should be set.");
        }

        this.network = network;
        this.cache = new HashMap<>();

        if (mnemonic != null && !mnemonic.isEmpty()) {
            this.mnemonic = mnemonic;
            this.accountNo = account;
            MnemonicUtil.validateMnemonic(this.mnemonic);
        } else if (rootKey != null && rootKey.length > 0) {
            this.accountNo = account;

            if (rootKey.length == 96)
                this.rootKey = rootKey;
            else
                throw new WalletException("Invalid length (Root Key): " + rootKey.length);
        } else if (accountKey != null && accountKey.length > 0) {
            this.accountNo = account;

            if (accountKey.length == 96)
                this.accountKey = accountKey;
            else
                throw new WalletException("Invalid length (Account Private Key): " + accountKey.length);
        }

    }

    /**
     * Get Enterprise address for current account. Account can be changed via the setter.
     * @param index address index
     * @return Address object with enterprise address
     */
    @Override
    public Address getEntAddress(int index) {
        return getEntAddress(this.accountNo, index);
    }

    /**
     * Get Enterprise address for derivation path m/1852'/1815'/{account}'/0/{index}
     * @param account account no
     * @param index address index
     * @return Address object with Enterprise address
     */
    private Address getEntAddress(int account, int index) {
        return getAccount(account, index).getEnterpriseAddress();
    }

    /**
     * Get Baseaddress for current account. Account can be changed via the setter.
     * @param index address index
     * @return Address object for Base address
     */
    @Override
    public Address getBaseAddress(int index) {
        return getBaseAddress(this.accountNo, index);
    }

    /**
     * Get Baseaddress for current account as String. Account can be changed via the setter.
     * @param index address index
     * @return Base address as string
     */
    @Override
    public String getBaseAddressString(int index) {
        return getBaseAddress(index).getAddress();
    }

    /**
     * Get Baseaddress for derivationpath m/1852'/1815'/{account}'/0/{index}
     * @param account account number
     * @param index address index
     * @return Address object for Base address
     */
    @Override
    public Address getBaseAddress(int account, int index) {
        return getAccount(account,index).getBaseAddress();
    }

    /**
     * Returns the Account object for the index and current account. Account can be changed via the setter.
     * @param index address index
     * @return Account object
     */
    @Override
    public Account getAccountAtIndex(int index) {
        return getAccount(this.accountNo, index);
    }

    /**
     * Returns the Account object for the index and account.
     * @param account account number
     * @param index address index
     * @return Account object
     */
    @Override
    public Account getAccount(int account, int index) {
        if(account != this.accountNo) {
            return deriveAccount(account, index);
        } else {
            if(cache.containsKey(index)) {
                return cache.get(index);
            } else {
                Account acc = deriveAccount(account, index);
                if (acc != null)
                    cache.put(index, acc);

                return acc;
            }
        }
    }

    private Account deriveAccount(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        if (mnemonic != null && !mnemonic.isEmpty()) {
            return Account.createFromMnemonic(this.network, this.mnemonic, derivationPath);
        } else if (rootKey != null && rootKey.length > 0) {
            return Account.createFromRootKey(this.network, this.rootKey, derivationPath);
        } else if (accountKey != null && accountKey.length > 0) {
            return Account.createFromAccountKey(this.network, this.accountKey, derivationPath);
        }else {
            throw new WalletException("Can't create Account. At least one of 'mnemonic', 'accountKey', or 'rootKey' must be set.");
        }
    }

    /**
     * Setting the current account for derivation path.
     * Setting the account will reset the cache.
     * @param account account number which will be set in the wallet
     */
    @Override
    public void setAccountNo(int account) {
        this.accountNo = account;
        // invalidating cache since it is only held for one account
        cache = new HashMap<>();
    }

    @Override
    public int getAccountNo() {
        return this.accountNo;
    }

    /**
     * Returns the RootkeyPair
     * @return Root key as HdKeyPair if non-empty else empty optional
     */
    @JsonIgnore
    @Override
    public Optional<HdKeyPair> getRootKeyPair() {
        if(rootKeyPair == null) {
            if (mnemonic != null && !mnemonic.isEmpty()) {
                HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
                try {
                    byte[] entropy = MnemonicCode.INSTANCE.toEntropy(this.mnemonic);
                    rootKeyPair = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);
                } catch (MnemonicException.MnemonicLengthException | MnemonicException.MnemonicWordException |
                         MnemonicException.MnemonicChecksumException e) {
                    throw new WalletException("Unable to derive root key pair", e);
                }
            } else if (rootKey != null && rootKey.length > 0) {
                HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
                rootKeyPair = hdKeyGenerator.getKeyPairFromSecretKey(rootKey, HdKeyGenerator.MASTER_PATH);
            }
        }

        return Optional.ofNullable(rootKeyPair);
    }

    @Override
    public Optional<byte[]> getRootPvtKey() {
        return getRootKeyPair()
                .map(rkp -> rkp.getPrivateKey().getBytes());
    }

    /**
     * Finds needed signers within wallet and signs the transaction with each one
     * @param txToSign transaction
     * @return signed Transaction
     */
    @Override
    public Transaction sign(Transaction txToSign, Set<WalletUtxo> utxos) {
        Map<String, Account> accountMap = utxos.stream()
                .map(WalletUtxo::getDerivationPath)
                .filter(Objects::nonNull)
                .map(derivationPath -> getAccount(
                        derivationPath.getAccount().getValue(),
                        derivationPath.getIndex().getValue()))
                .collect(Collectors.toMap(
                        Account::baseAddress,
                        Function.identity(),
                        (existing, replacement) -> existing)); // Handle duplicates if necessary

        var accounts = accountMap.values();

        if(accounts.isEmpty())
            throw new WalletException("No signers found!");

        for (Account signerAcc : accounts)
            txToSign = signerAcc.sign(txToSign);

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
     * @return Stake address as string
     */
    @Override
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
     * @param transaction transaction object to sign
     * @return Signed transaction object
     */
    @Override
    public Transaction signWithStakeKey(Transaction transaction) {
        return TransactionSigner.INSTANCE.sign(transaction, getStakeKeyPair());
    }

    private HdKeyPair getStakeKeyPair() {
        if(stakeKeys == null) {
            DerivationPath stakeDerivationPath = DerivationPath.createStakeAddressDerivationPathForAccount(this.accountNo);
            stakeKeys = new CIP1852().getKeyPairFromMnemonic(mnemonic, stakeDerivationPath);
        }
        return stakeKeys;
    }

}
