package com.bloxbean.cardano.client.plutus.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.plutus.api.ScriptUtxoSelection;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

//TODO -- Unit tests pending
public class DefaultScriptUtxoSelection implements ScriptUtxoSelection {
    private UtxoService utxoService;

    public DefaultScriptUtxoSelection(UtxoService utxoService) {
        this.utxoService = utxoService;
    }

    @Override
    public Utxo findFirst(String scriptAddress, Predicate<Utxo> predicate) throws ApiException {
        return findFirst(scriptAddress, predicate, Collections.EMPTY_SET);
    }

    @Override
    public Utxo findFirst(String scriptAddress, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        boolean canContinue = true;
        int i = 1;

        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(scriptAddress, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = (fetchData);
                if(data == null || data.isEmpty())
                    canContinue = false;

                Optional<Utxo> option = data.stream().filter(predicate).findFirst();
                if (option.isPresent())
                    return option.get();

            } else {
                canContinue = false;
                throw new ApiException(String.format("Unable to get enough Utxos for address : %s, reason: %s", scriptAddress, result.getResponse()));
            }
        }

        return null;
    }

    @Override
    public List<Utxo> findAll(String scriptAddress, Predicate<Utxo> predicate) throws ApiException {
        return findAll(scriptAddress, predicate);
    }

    @Override
    public List<Utxo> findAll(String scriptAddress, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        boolean canContinue = true;
        int i = 1;

        List<Utxo> utxoList = new ArrayList<>();
        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(scriptAddress, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = (fetchData);
                if(data == null || data.isEmpty())
                    canContinue = false;

                List<Utxo> filterUtxos = data.stream().filter(predicate).collect(Collectors.toList());
                utxoList.addAll(filterUtxos);

            } else {
                canContinue = false;
                throw new ApiException(String.format("Unable to get enough Utxos for address : %s, reason: %s", scriptAddress, result.getResponse()));
            }
        }

        return null;
    }

    private OrderEnum getUtxoFetchOrder() {
        return OrderEnum.asc;
    }

    private int getUtxoFetchSize() {
        return 100;
    }

}
