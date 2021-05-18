package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;

import java.math.BigInteger;

public interface BlockService {

    /**
     *
     * @return Get latest block
     */
    public Result<Block> getLastestBlock() throws ApiException;

    /**
     *
     * @param blockHash
     * @return Get block details by block hash
     */
    public Result<Block> getBlockByHash(String blockHash) throws ApiException;

    /**
     *
     * @param blockNumber
     * @return Get block by block number
     */
    public Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException;
}
