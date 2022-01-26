package com.bloxbean.cardano.client.coinselection;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Implement this interface to provide custom UtxoSelection Strategy
 */
public interface UtxoSelectionStrategy {

    /**
     * Selected utxos based on a strategy
     * @param address Address to select utxos for
     * @param unit Unit
     * @param amount Amount
     * @param excludeUtxos Utxos to ignore
     * @return List of Utxos
     */
    List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException;

    /**
     * Selected utxos based on a strategy
     * @param address Address to select utxos for
     * @param unit Unit
     * @param amount Amount
     * @param datumHash DatumHash to compare
     * @param excludeUtxos Utxos to ignore
     * @return List of Utxos
     * @throws ApiException
     */
    List<Utxo> selectUtxos(String address, String unit, BigInteger amount, String datumHash, Set<Utxo> excludeUtxos) throws ApiException;

    /**
     *
     * @return True if utxos with datum hash need to ignored, otherwise false
     */
    default boolean ignoreUtxosWithDatumHash() {
        return true;
    }

    /**
     * Set if utxos with datum hash need to be ignored or not
     * @param ignoreUtxosWithDatumHash
     */
    default void setIgnoreUtxosWithDatumHash(boolean ignoreUtxosWithDatumHash) {

    }

}
