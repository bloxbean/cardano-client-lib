package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.UtxoSupplier;

import java.util.Collections;
import java.util.List;

public class DefaultUtxoSupplier implements UtxoSupplier {
    private final UtxoService utxoService;

    public DefaultUtxoSupplier(UtxoService utxoService){
        this.utxoService = utxoService;
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        try{
            var result = utxoService.getUtxos(address, nrOfItems != null ? nrOfItems : UtxoSupplier.DEFAULT_NR_OF_ITEMS_TO_FETCH, page != null ? page + 1 : 1, order);
            return result != null && result.getValue() != null ? result.getValue() : Collections.emptyList();
        } catch (ApiException e) {
            throw new ApiRuntimeException(e);
        }
    }
}
