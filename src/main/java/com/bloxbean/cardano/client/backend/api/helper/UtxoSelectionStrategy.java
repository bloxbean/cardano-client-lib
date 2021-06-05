package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
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
     * @return
     */
    public List<Utxo> selectUtxos(String address, String unit, BigInteger amount, Set<Utxo> excludeUtxos) throws ApiException;

}
