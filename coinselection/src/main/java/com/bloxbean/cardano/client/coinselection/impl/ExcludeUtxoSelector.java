package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelector;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * UtxoSelector implementation to exclude utxos from selection. This is useful when you want to exclude a list of Utxos from the selection process.
 * The actual selection is delegated to the underlying {@link UtxoSelector} implementation.
 */
public class ExcludeUtxoSelector implements UtxoSelector {
    private UtxoSelector utxoSelector;
    private Set<Utxo> excludeList;

    public ExcludeUtxoSelector(UtxoSelector utxoSelector, Set<TransactionInput> inputsToExclude) {
        Objects.requireNonNull(utxoSelector, "UtxoSelector is required");
        this.utxoSelector = utxoSelector;
        if (inputsToExclude != null && !inputsToExclude.isEmpty()) {
            excludeList = inputsToExclude.stream().map(input -> Utxo.builder()
                    .txHash(input.getTransactionId())
                    .outputIndex(input.getIndex())
                    .build()).collect(Collectors.toSet());
        } else {
            excludeList = Collections.emptySet();
        }
    }

    @Override
    public Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate) throws ApiException {
        return utxoSelector.findFirst(address, predicate, getFinalExcludeList(Collections.emptySet()));
    }

    @Override
    public Optional<Utxo> findFirst(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        return utxoSelector.findFirst(address, predicate, getFinalExcludeList(excludeUtxos));
    }

    @Override
    public List<Utxo> findAll(String address, Predicate<Utxo> predicate) throws ApiException {
        return utxoSelector.findAll(address, predicate, getFinalExcludeList(Collections.emptySet()));
    }

    @Override
    public List<Utxo> findAll(String address, Predicate<Utxo> predicate, Set<Utxo> excludeUtxos) throws ApiException {
        return utxoSelector.findAll(address, predicate, getFinalExcludeList(excludeUtxos));
    }

    private Set<Utxo> getFinalExcludeList(Set<Utxo> excludeUtxos) {
        Set<Utxo> finalUtxoToExclude;
        if (excludeUtxos != null && !excludeUtxos.isEmpty()) {
            finalUtxoToExclude = new HashSet<>(excludeUtxos);
            finalUtxoToExclude.addAll(this.excludeList);
        } else {
            finalUtxoToExclude = this.excludeList;
        }
        return finalUtxoToExclude;
    }
}
