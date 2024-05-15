package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.PoolInfo;

public interface PoolService {

    /**
     * Get Specific stake pool information
     *
     * @return Pool Information
     */
    Result<PoolInfo> getPoolInfo(String poolId) throws ApiException;
}
