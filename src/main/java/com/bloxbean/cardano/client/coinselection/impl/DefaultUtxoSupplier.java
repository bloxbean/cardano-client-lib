package com.bloxbean.cardano.client.coinselection.impl;

import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.coinselection.UtxoSupplier;

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
        }catch(ApiException e){
            throw new ApiRuntimeException(e);
        }
    }
}
