package com.bloxbean.cardano.client.coinselection;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Amount;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implement this interface to provide custom UtxoSelection Strategy
 */
public interface UtxoSelectionStrategy {

    int DEFAULT_COIN_SELECTION_LIMIT = 20;

    default List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> utxosToExclude) throws ApiException {
        return this.selectUtxos(address, unit, amount, null, utxosToExclude);
    }
    default List<Utxo> selectUtxos(String address, String unit, BigInteger amount, String datumHash, Set<Utxo> utxosToExclude) throws ApiException{
        Set<Utxo> selected = select(address, new Amount(unit, amount), datumHash, utxosToExclude);
        return selected != null ? new ArrayList<>(selected) : Collections.emptyList();
    }
    default Set<Utxo> select(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude){
        return select(address, outputAmount, datumHash, utxosToExclude, DEFAULT_COIN_SELECTION_LIMIT);
    }
    default Set<Utxo> select(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit){
        return select(address, Collections.singletonList(outputAmount), datumHash, utxosToExclude, maxUtxoSelectionLimit);
    }
    default Set<Utxo> select(String address, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude){
        return this.select(address, outputAmounts, datumHash, utxosToExclude, DEFAULT_COIN_SELECTION_LIMIT);
    }
    Set<Utxo> select(String address, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit);

    UtxoSelectionStrategy fallback();

    void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash);
}
