package com.bloxbean.cardano.hdwallet;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.common.MnemonicUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicCode;
import com.bloxbean.cardano.client.crypto.bip39.MnemonicException;
import com.bloxbean.cardano.client.crypto.bip39.Words;
import com.bloxbean.cardano.client.crypto.cip1852.DerivationPath;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class HDWallet {

    @Setter
    @Getter
    private int account = 0;
    @Getter
    private Network network;
    @Getter
    private String mnemonic;
    private int startIndex = 0;

    public HDWallet() {
        this(Networks.mainnet());
    }

    public HDWallet(Network network) {
        this(network, Words.TWENTY_FOUR);
    }

    public HDWallet(Network network, Words noOfWords) {
        this(network, noOfWords, 0);
    }

    public HDWallet(Network network, Words noOfWords, int account) {
        this.network = network;
        this.mnemonic = MnemonicUtil.generateNew(noOfWords);
        this.account = account;
    }

    public HDWallet(String mnemonic) {
        this(Networks.mainnet(), mnemonic);
    }

    public HDWallet(Network network, String mnemonic) {
        this(network,mnemonic, 0);
    }

    public HDWallet(Network network, String mnemonic, int account) {
        this.network = network;
        this.mnemonic = mnemonic;
        this.account = account;
        MnemonicUtil.validateMnemonic(this.mnemonic);
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
     * @param backendService
     * @return
     */
    public List<Utxo> getUtxos(BackendService backendService) {
        return getUtxos(this.account, backendService);
    }

    @SneakyThrows
    public List<Utxo> getUtxos(int account, BackendService backendService) {
        List<Utxo> utxos = new ArrayList<>();
        int index = this.startIndex;
        int noUtxoFound = 0;
        while(noUtxoFound < 20) {
            List<Utxo> utxoFromIndex = getUtxos(account, index, backendService);
            utxos.addAll(utxoFromIndex);
            noUtxoFound = utxoFromIndex.isEmpty()? noUtxoFound + 1 : 0;

            index++; // increasing search index
        }
        return utxos;
    }

    @SneakyThrows
    public List<Utxo> getUtxos(int account, int index, BackendService backendService) {
        List<Utxo> utxos = new ArrayList<>();
        Address address = getBaseAddress(account, index);
        boolean fetchNextPage = false;
        int page = 1;
        final int MAX_PAGE_SIZE = 100;
        do {
            Result<List<Utxo>> utxos1 = backendService.getUtxoService().getUtxos(address.getAddress(), MAX_PAGE_SIZE, page);
            if(utxos1.isSuccessful()) {
                List<Utxo> value = utxos1.getValue();
                utxos.addAll(value);
                if(value.size() == MAX_PAGE_SIZE) { // need to fetch next page
                    fetchNextPage = true;
                    page++;
                } else {
                    fetchNextPage = false;
                }
            }
        } while (fetchNextPage);
        return utxos;
    }


}
