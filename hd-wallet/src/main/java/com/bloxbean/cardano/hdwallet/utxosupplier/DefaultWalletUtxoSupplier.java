package com.bloxbean.cardano.hdwallet.utxosupplier;

import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultWalletUtxoSupplier implements WalletUtxoSupplier {

    private final UtxoService utxoService;
    @Setter
    private Wallet wallet;
    private static final int INDEX_SEARCH_RANGE = 20; // according to specifications

    public DefaultWalletUtxoSupplier(UtxoService utxoService, Wallet wallet) {
        this.utxoService = utxoService;
        this.wallet = wallet;
    }

    public DefaultWalletUtxoSupplier(UtxoService utxoService) {
        this.utxoService = utxoService;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        return getAll(address); // todo get Page of utxo over multipe addresses - find a good way to aktually do something with page, nrOfItems and order
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        try {
            var result = utxoService.getTxOutput(txHash, outputIndex);
            return result != null && result.getValue() != null ? Optional.of(result.getValue()) : Optional.empty();
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }

    @Override
    public List<Utxo> getAll(String address) {
        checkIfWalletIsSet();
        return getAll(wallet);
    }

    @Override
    public List<Utxo> getAll(Wallet wallet) {
        int index = 0;
        int noUtxoFound = 0;
        List<Utxo> utxos = new ArrayList<>();
        while(noUtxoFound < INDEX_SEARCH_RANGE) {
            List<Utxo> utxoFromIndex = getUtxosForAccountAndIndex(wallet, wallet.getAccount(), index);
            utxos.addAll(utxoFromIndex);
            noUtxoFound = utxoFromIndex.isEmpty() ? noUtxoFound + 1 : 0;

            index++; // increasing search index
        }
        return utxos;
    }

    @Override
    public List<Utxo> getUtxosForAccountAndIndex(Wallet wallet, int account, int index) {
        String address = wallet.getBaseAddress(account, index).getAddress();
        List<Utxo> utxos = new ArrayList<>();
        int page = 1;
        while(true) {
            Result<List<Utxo>> result = null;
            try {
                result = utxoService.getUtxos(address, UtxoSupplier.DEFAULT_NR_OF_ITEMS_TO_FETCH, page, OrderEnum.asc);
            } catch (ApiException e) {
                throw new ApiRuntimeException(e);
            }
            List<Utxo> utxoPage = result != null && result.getValue() != null ? result.getValue() : Collections.emptyList();
            utxos.addAll(utxoPage);
            if(utxoPage.size() < 100)
                break;
            page++;
        }
        return utxos;
    }

    @Override
    public List<Utxo> getUtxosForAccountAndIndex(int account, int index) {
        checkIfWalletIsSet();
        return getUtxosForAccountAndIndex(this.wallet, account, index);
    }

    private void checkIfWalletIsSet() {
        if(this.wallet == null)
            throw new RuntimeException("Wallet has to be provided!");
    }
}
