package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement this interface to provide list of {@link Utxo} at an address
 */
public interface UtxoSupplier {
    int DEFAULT_NR_OF_ITEMS_TO_FETCH = 100;

    List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order);

    default List<Utxo> getAll(String address){
        var pageToFetch = 0;
        var result = new ArrayList<Utxo>();
        // call fetch until result is empty or < nr of items
        while(true){
            var pageResult = getPage(address, DEFAULT_NR_OF_ITEMS_TO_FETCH, pageToFetch, OrderEnum.asc);
            if(pageResult != null){
                result.addAll(pageResult);
            }
            if(pageResult == null || pageResult.isEmpty()){
                break;
            }
            pageToFetch += 1;
        }
        return result;
    }
}
