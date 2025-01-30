package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.List;

public interface UtxoService {

    /**
     *
     * @param address
     * @param count
     * @param page
     * @return List of Utxos for a address
     * @throws ApiException
     */
    Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException;

    /**
     *
     * @param address
     * @param count
     * @param page
     * @param order  asc or desc. Default is "asc"
     * @return
     * @throws ApiException
     */
    Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Fetch Utxos for a given address and asset
     * @param address Address
     * @param unit Asset unit
     * @param count Number of utxos to fetch
     * @param page Page number
     * @return List of Utxos
     * @throws ApiException If any error occurs
     */
    Result<List<Utxo>> getUtxos(String address, String unit, int count, int page) throws ApiException;

    /**
     * Fetch Utxos for a given address and asset
     * @param address Address
     * @param unit Asset unit
     * @param count Number of utxos to fetch
     * @param page Page number
     * @param order Order of the utxos
     * @return List of Utxos
     * @throws ApiException If any error occurs
     */
    Result<List<Utxo>> getUtxos(String address, String unit, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Fetch Output for a given transaction hash and output index. The output may be spent or unspent.
     * @param txHash Transaction hash
     * @param outputIndex Output index
     * @return Utxo
     * @throws ApiException If any error occurs
     */
    Result<Utxo> getTxOutput(String txHash, int outputIndex) throws ApiException;

    /**
     * Checks if the provided address has been used in any transactions.
     *
     * @param address The address to be checked.
     * @return true if the address has been used, otherwise false.
     * @throws ApiException If any error occurs during the operation.
     */
    default boolean isUsedAddress(String address) throws ApiException {
        throw new UnsupportedOperationException();
    }
}
