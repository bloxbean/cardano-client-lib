package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This is a wrapper {@link UtxoSelectionStrategy} implementation to exclude a list of Utxos from the selection process.
 * This is useful when you want to exclude a list of Utxos from the selection process. The actual selection is delegated to the
 * underlying {@link UtxoSelectionStrategy} implementation.
 */
public class ExcludeUtxoSelectionStrategy implements UtxoSelectionStrategy {
    private UtxoSelectionStrategy utxoSelectionStrategy;
    private Set<Utxo> excludeList;

    public ExcludeUtxoSelectionStrategy(UtxoSelectionStrategy utxoSelectionStrategy, Set<TransactionInput> inputsToExclude) {
        this.utxoSelectionStrategy = utxoSelectionStrategy;
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
    public Set<Utxo> select(AddressIterator addrIter, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude,
                            int maxUtxoSelectionLimit) {
        Set<Utxo> finalUtxoToExclude;
        if (utxosToExclude != null) {
            finalUtxoToExclude = new HashSet<>(utxosToExclude);
            finalUtxoToExclude.addAll(this.excludeList);
        } else {
            finalUtxoToExclude = this.excludeList;
        }
        return utxoSelectionStrategy.select(addrIter, outputAmounts, datumHash, inlineDatum, finalUtxoToExclude, maxUtxoSelectionLimit);
    }

    @Override
    public UtxoSelectionStrategy fallback() {
        return utxoSelectionStrategy.fallback();
    }

    @Override
    public void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {
        utxoSelectionStrategy.setIgnoreUtxosWithDatumHash(ignoreUtxosWithDatumHash);
    }
}
