package com.bloxbean.cardano.client.backend.api.helper;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.model.Utxo;

import java.util.List;

/**
 * Implement this interface to provide custom UtxoSelection Strategy
 */
public interface UtxoSelectionStrategy {

    /**
     * Filter and return selected utxos from the list
     * @param utxos
     * @return
     */
    public List<Utxo> filter(List<Utxo> utxos);

    /**
     * Utxo record fetch size from the server per page
     * @return
     */
    default public int getUtxoFetchSize() {
        return 40;
    }

    /**
     * Utxo fetch order
     * @return
     */
    default public OrderEnum getUtxoFetchOrder() {
        return OrderEnum.asc;
    }
}
