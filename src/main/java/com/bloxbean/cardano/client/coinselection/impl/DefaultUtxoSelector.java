package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Default implementation of UtxoSelector
 */
public class DefaultUtxoSelector implements UtxoSelector {
    private UtxoService utxoService;

    public DefaultUtxoSelector(UtxoService utxoService) {
        this.utxoService = utxoService;
    }

    @Override
    public Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate) throws ApiException {
        return findFirst(address, predicate, Collections.EMPTY_SET);
    }

    @Override
    public Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        boolean canContinue = true;
        int i = 1;

        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = (fetchData);
                if(data == null || data.isEmpty())
                    canContinue = false;
                else {
                    Optional<Utxo> optional = data.stream()
                            .filter(predicate)
                            .filter(utxo -> !excludeUtxos.contains(utxo))
                            .findFirst();

                    if (optional.isPresent() )
                        return optional;
                }

            } else {
                canContinue = false;
            }
        }

        return Optional.empty();
    }

    @Override
    public List<Utxo> findAll(String address, Predicate<Utxo> predicate) throws ApiException {
        return findAll(address, predicate, Collections.EMPTY_SET);
    }

    @Override
    public List<Utxo> findAll(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        boolean canContinue = true;
        int i = 1;

        List<Utxo> utxoList = new ArrayList<>();
        while(canContinue) {
            Result<List<Utxo>> result = utxoService.getUtxos(address, getUtxoFetchSize(),
                    i++, getUtxoFetchOrder());
            if(result.code() == 200) {
                List<Utxo> fetchData = result.getValue();

                List<Utxo> data = (fetchData);
                if(data == null || data.isEmpty())
                    canContinue = false;
                else {
                    List<Utxo> filterUtxos = data.stream().filter(predicate)
                            .filter(utxo -> !excludeUtxos.contains(utxo))
                            .collect(Collectors.toList());
                    utxoList.addAll(filterUtxos);
                }

            } else {
                canContinue = false;
            }
        }

        return utxoList;
    }

    protected OrderEnum getUtxoFetchOrder() {
        return OrderEnum.asc;
    }

    protected int getUtxoFetchSize() {
        return 100;
    }

}
