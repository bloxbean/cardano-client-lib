package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implement this interface to provide list of {@link Utxo} at an address
 */
public interface UtxoSupplier {
    int DEFAULT_NR_OF_ITEMS_TO_FETCH = 100;

    /**
     * Fetches a page of utxo at an address
     * @param address Address to fetch utxo
     * @param nrOfItems Number of items to fetch
     * @param page Page number to fetch
     * @param order Order of the items
     * @return List of {@link Utxo}
     */
    List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order);

    /**
     * Fetches a single output by txHash and outputIndex. This method doesn't check if the output is spent or not.
     * This method can be used to resolve the reference inputs of a transaction.
     * @param txHash Transaction hash
     * @param outputIndex Output index
     * @return {@link Utxo}
     */
    Optional<Utxo> getTxOutput(String txHash, int outputIndex);

    /**
     * Fetches all utxo at an address
     * @param address Address to fetch utxo
     * @return List of {@link Utxo}
     */
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

    /**
     * Checks if the provided address has been used in any transactions.
     *
     * @param address The address to be checked.
     * @return true if the address has been used, otherwise false.
     */
    default boolean isUsedAddress(Address address) {
        return true;
    }
}
