package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.Utxo;

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
    public Result<List<Utxo>> getUtxos(String address, int count, int page) throws ApiException;

    /**
     *
     * @param address
     * @param count
     * @param page
     * @param order  asc or desc. Default is "asc"
     * @return
     * @throws ApiException
     */
    public Result<List<Utxo>> getUtxos(String address, int count, int page, OrderEnum order) throws ApiException;
}
