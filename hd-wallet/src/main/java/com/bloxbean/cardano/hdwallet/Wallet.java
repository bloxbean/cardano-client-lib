package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.common.MnemonicUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class Wallet {

    @Setter
    @Getter
    private int account = 0;
    @Getter
    private Network network;
    @Getter
    private String mnemonic;
    @Setter
    @Getter
    private WalletUtxoSupplier utxoSupplier;
    private static final int INDEX_SEARCH_RANGE = 20;

    public Wallet(WalletUtxoSupplier utxoSupplier) {
        this(Networks.mainnet(), utxoSupplier);
    }

    public Wallet(Network network, WalletUtxoSupplier utxoSupplier) {
        this(network, Words.TWENTY_FOUR, utxoSupplier);
    }

    public Wallet(Network network, Words noOfWords, WalletUtxoSupplier utxoSupplier) {
        this(network, noOfWords, 0, utxoSupplier);
    }

    public Wallet(Network network, Words noOfWords, int account, WalletUtxoSupplier utxoSupplier) {
        this.network = network;
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        this.account = account;
        this.utxoSupplier = utxoSupplier;
    }

    public Wallet(String mnemonic, WalletUtxoSupplier utxoSupplier) {
        this(Networks.mainnet(), mnemonic, utxoSupplier);
    }

    public Wallet(Network network, String mnemonic, WalletUtxoSupplier utxoSupplier) {
        this(network,mnemonic, 0, utxoSupplier);
    }

    public Wallet(Network network, String mnemonic, int account, WalletUtxoSupplier utxoSupplier) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.account = account;
        MnemonicUtil.validateMnemonic(this.mnemonic);
        this.utxoSupplier = utxoSupplier;
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
        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        return new Account(this.network, this.mnemonic, derivationPath).getEnterpriseAddress();
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
     * Get Baseaddress for derivationpath m/1852'/1815'/{account}'/0/{index}
     * @param account
     * @param index
     * @return
     */
    public Address getBaseAddress(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);
        return new Account(this.network, this.mnemonic, derivationPath).getBaseAddress();
    }

    /**
     * Returns the RootkeyPair
     * @return
     */
    public HdKeyPair getHDWalletKeyPair() {
        HdKeyGenerator hdKeyGenerator = new HdKeyGenerator();
        HdKeyPair rootKeys;
        try {
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(this.mnemonic);
            rootKeys = hdKeyGenerator.getRootKeyPairFromEntropy(entropy);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e);
        } catch (MnemonicException.MnemonicWordException e) {
            throw new RuntimeException(e);
        } catch (MnemonicException.MnemonicChecksumException e) {
            throw new RuntimeException(e);
        }
        return rootKeys;
    }

    /**
     * TODO Renaming??
     * @param account
     * @param index
     * @return
     */
    public Account getSigner(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createDRepKeyDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);
        return new Account(this.network, this.mnemonic, derivationPath);
    }

    /**
     * TODO Renaming??
     * @param index
     * @return
     */
    public Account getSigner(int index) {
        return new Account(this.network, this.mnemonic, index);
    }

    /**
     * Finds needed signers within wallet and signs the transaction with each one
     * @param txToSign
     * @return signed Transaction
     */
    public Transaction sign(Transaction txToSign) {
        List<Account> signers = getSignersForTransaction(txToSign);

        if(signers.isEmpty())
            throw new RuntimeException("No signers found!");

        for (Account signer : signers) txToSign = signer.sign(txToSign);

        return txToSign;
    }

    /**
     * Returns a list with signers needed for this transaction
     * @param tx
     * @return
     */
    public List<Account> getSignersForTransaction(Transaction tx) {
        return getSignersForInputs(tx.getBody().getInputs());
    }

    private List<Account> getSignersForInputs(List<TransactionInput> inputs) {
        // searching for address to sign
        List<Account> signers = new ArrayList<>();
        List<TransactionInput> remaining = new ArrayList<>(inputs);

        int index = 0;
        int emptyCounter = 0;
        while (!remaining.isEmpty() || emptyCounter >= INDEX_SEARCH_RANGE) {
            List<Utxo> utxos = utxoSupplier.getUtxosForAccountAndIndex(this, this.account, index);
            emptyCounter = utxos.isEmpty() ? emptyCounter + 1 : 0;

            for (Utxo utxo : utxos) {
                if(matchUtxoWithInputs(inputs, utxo, signers, index, remaining))
                    break;
            }
            index++;
        }
        return signers;
    }

    private boolean matchUtxoWithInputs(List<TransactionInput> inputs, Utxo utxo, List<Account> signers, int index, List<TransactionInput> remaining) {
        for (TransactionInput input : inputs) {
            if(utxo.getTxHash().equals(input.getTransactionId()) && utxo.getOutputIndex() == input.getIndex()) {
                signers.add(getSigner(index));
                remaining.remove(input);
            }
        }
        return remaining.isEmpty();
    }

}
