package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.coinselection.impl.DefaultUtxoSelectionStrategyImpl;
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
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class HDWallet {

    @Setter
    @Getter
    private int account = 0;
    @Getter
    private Network network;
    @Getter
    private String mnemonic;
    private int startIndex = 0;
    @Setter
    private UtxoSupplier utxoSupplier;
    @Setter
    HDWalletUtxoSelectionStrategy utxoSelectionStrategy;

    public HDWallet(UtxoSupplier utxoSupplier) {
        this(Networks.mainnet(), utxoSupplier);
    }

    public HDWallet(Network network, UtxoSupplier utxoSupplier) {
        this(network, Words.TWENTY_FOUR, utxoSupplier);
    }

    public HDWallet(Network network, Words noOfWords, UtxoSupplier utxoSupplier) {
        this(network, noOfWords, 0, utxoSupplier);
    }

    public HDWallet(Network network, Words noOfWords, int account, UtxoSupplier utxoSupplier) {
        this.network = network;
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        this.account = account;
        this.utxoSupplier = utxoSupplier;
        this.utxoSelectionStrategy = new DefaultHDWalletUtxoSelectionStrategyImpl(utxoSupplier);

    }

    public HDWallet(String mnemonic, UtxoSupplier utxoSupplier) {
        this(Networks.mainnet(), mnemonic, utxoSupplier);
    }

    public HDWallet(Network network, String mnemonic, UtxoSupplier utxoSupplier) {
        this(network,mnemonic, 0, utxoSupplier);
    }

    public HDWallet(Network network, String mnemonic, int account, UtxoSupplier utxoSupplier) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.account = account;
        MnemonicUtil.validateMnemonic(this.mnemonic);
        this.utxoSupplier = utxoSupplier;
        this.utxoSelectionStrategy = new DefaultHDWalletUtxoSelectionStrategyImpl(utxoSupplier);
    }

    public Address getEntAddress(int index) {
        return getEntAddress(this.account, index);
    }

    private Address getEntAddress(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);

        return new Account(this.network, this.mnemonic, derivationPath).getEnterpriseAddress();
    }

    public Address getBaseAddress(int index) {
        return getBaseAddress(this.account, index);
    }

    public Address getBaseAddress(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createExternalAddressDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);
        return new Account(this.network, this.mnemonic, derivationPath).getBaseAddress();
    }

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
     * Scanning all accounts takes to much time for now
     * @return
     */
    public List<Utxo> getUtxos() {
        return getUtxos(this.account);
    }

    @SneakyThrows
    public List<Utxo> getUtxos(int account) {
        List<Utxo> utxos = new ArrayList<>();
        int index = this.startIndex;
        int noUtxoFound = 0;
        while(noUtxoFound < 20) {
            List<Utxo> utxoFromIndex = getUtxos(account, index);
            utxos.addAll(utxoFromIndex);
            noUtxoFound = utxoFromIndex.isEmpty()? noUtxoFound + 1 : 0;

            index++; // increasing search index
        }
        return utxos;
    }

    @SneakyThrows
    public List<Utxo> getUtxos(int account, int index) {
        Address address = getBaseAddress(account, index);
        return utxoSupplier.getAll(address.getAddress());
    }

    public Account getSigner(int account, int index) {
        DerivationPath derivationPath = DerivationPath.createDRepKeyDerivationPathForAccount(account);
        derivationPath.getIndex().setValue(index);
        return new Account(this.network, this.mnemonic, derivationPath);
    }

    public Account getSigner(int index) {
//        return getSigner(this.account, index);
        return new Account(this.network, this.mnemonic, index);
    }

    // Pretty stupid bruteforce approach for iteration 1
    public Transaction sign(Transaction outputTxn) {
        List<Account> signers = getSignersForInputs(outputTxn.getBody().getInputs());
        if(signers.isEmpty())
            throw new RuntimeException("No signers found!");
        Transaction signed = outputTxn;
        for (Account signer : signers) {
            signed = signer.sign(signed);
        }
        return signed;
    }


    private List<Account> getSignersForInputs(List<TransactionInput> inputs) {
        // searching for address to sign
        List<Account> signers = new ArrayList<>();
        List<TransactionInput> remaining = new ArrayList<>(inputs);
        int index = 0;
        int emptyCounter = 0;
        while (!remaining.isEmpty() || emptyCounter >= 20) {
            List<Utxo> utxos = getUtxos(this.account, index);
            if(utxos.isEmpty()) {
                emptyCounter++;
            } else {
                emptyCounter = 0;
            }
            for (Utxo utxo : utxos) {
                for (TransactionInput input : inputs) {
                    if(utxo.getTxHash().equals(input.getTransactionId())) {
                        signers.add(getSigner(index));
                        remaining.remove(input);
                    }
                }
                if(remaining.isEmpty())
                    break;
            }
            index++;
        }
        return signers;
    }
    public List<Utxo> getUtxosForOutputs(List<Amount> outputs) {
        List<Utxo> allUtxosFromWallet = getUtxos();
        return new ArrayList(utxoSelectionStrategy.select(allUtxosFromWallet, outputs, null, null, null, Integer.MAX_VALUE));
    }
}
