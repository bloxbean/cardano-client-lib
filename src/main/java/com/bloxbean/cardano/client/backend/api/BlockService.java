package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;

import java.math.BigInteger;

public interface BlockService {

    /**
     * Get the Latest Block
     *
     * @return Get latest block
     */
    Result<Block> getLastestBlock() throws ApiException;

    /**
     * Get Block by Block Hash
     *
     * @param blockHash
     * @return Get block details by block hash
     */
    Result<Block> getBlockByHash(String blockHash) throws ApiException;

    /**
     * Get Block by Block Number
     *
     * @param blockNumber
     * @return Get block by block number
     */
    Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException;
}
