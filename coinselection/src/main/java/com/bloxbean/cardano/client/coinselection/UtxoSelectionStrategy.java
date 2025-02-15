package com.bloxbean.cardano.client.coinselection;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.AddressIterator;
import com.bloxbean.cardano.client.api.common.AddressIterators;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.coinselection.config.CoinselectionConfig;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;
import java.util.*;

/**
 * Implement this interface to provide custom UtxoSelection Strategy
 */
public interface UtxoSelectionStrategy {
    //TODO - cleanup

    @Deprecated
    default List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> utxosToExclude) throws ApiException {
        return this.selectUtxos(address, unit, amount, null, utxosToExclude);
    }

    @Deprecated
    default List<Utxo> selectUtxos(String address, String unit, BigInteger amount, String datumHash, Set<Utxo> utxosToExclude) throws ApiException {
        Set<Utxo> selected = select(address, new Amount(unit, amount), datumHash, utxosToExclude);
        return selected != null ? new ArrayList<>(selected) : Collections.emptyList();
    }

    default Set<Utxo> select(String address, Amount outputAmount, Set<Utxo> utxosToExclude) {
        return select(address, Collections.singletonList(outputAmount), utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> select(String address, Amount outputAmount, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(address, Collections.singletonList(outputAmount), utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> select(String address, List<Amount> outputAmounts, Set<Utxo> utxosToExclude) {
        return this.select(address, outputAmounts, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> select(String address, List<Amount> outputAmounts, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return this.select(address, outputAmounts, null, null, utxosToExclude, maxUtxoSelectionLimit);
    }

    @Deprecated
    default Set<Utxo> select(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude) {
        return select(address, outputAmount, datumHash, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    @Deprecated
    default Set<Utxo> select(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(address, Collections.singletonList(outputAmount), datumHash, null, utxosToExclude, maxUtxoSelectionLimit);
    }

    @Deprecated
    default Set<Utxo> select(String address, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude) {
        return this.select(address, outputAmounts, datumHash, null, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> selectByDatumHash(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude) {
        return selectByDatumHash(address, Collections.singletonList(outputAmount), datumHash, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> selectByDatumHash(String address, Amount outputAmount, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return selectByDatumHash(address, Collections.singletonList(outputAmount), datumHash, utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> selectByDatumHash(String address, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude) {
        return this.selectByDatumHash(address, outputAmounts, datumHash, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> selectByDatumHash(String address, List<Amount> outputAmounts, String datumHash, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(address, outputAmounts, datumHash, null, utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> selectByInlineDatum(String address, Amount outputAmount, PlutusData inlineDatum, Set<Utxo> utxosToExclude) {
        return selectByInlineDatum(address, Collections.singletonList(outputAmount), inlineDatum, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> selectByInlineDatum(String address, Amount outputAmount, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return selectByInlineDatum(address, Collections.singletonList(outputAmount), inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> selectByInlineDatum(String address, List<Amount> outputAmounts, PlutusData inlineDatum, Set<Utxo> utxosToExclude) {
        return selectByInlineDatum(address, outputAmounts, inlineDatum, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
    }

    default Set<Utxo> selectByInlineDatum(String address, List<Amount> outputAmounts, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(address, outputAmounts, null, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> select(String address, Amount outputAmount, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(address, Collections.singletonList(outputAmount), datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }

    default Set<Utxo> select(String address, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit) {
        return select(AddressIterators.of(new Address(address)), outputAmounts, datumHash, inlineDatum, utxosToExclude, maxUtxoSelectionLimit);
    }

    default List<Utxo> selectUtxos(AddressIterator addrIter, String unit, BigInteger amount, Set<Utxo> utxosToExclude) throws ApiException {
        Set<Utxo> selected = select(addrIter, List.of(new Amount(unit, amount)), null, null, utxosToExclude, CoinselectionConfig.INSTANCE.getCoinSelectionLimit());
        return selected != null ? new ArrayList<>(selected) : Collections.emptyList();
    }

    Set<Utxo> select(AddressIterator addressIterator, List<Amount> outputAmounts, String datumHash, PlutusData inlineDatum, Set<Utxo> utxosToExclude, int maxUtxoSelectionLimit);

    UtxoSelectionStrategy fallback();

    void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash);
}
