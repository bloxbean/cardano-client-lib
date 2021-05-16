package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;

public interface NetworkInfoService {
    /**
     *
     * @return Genesis Info
     * @throws ApiException
     */
    public Result<Genesis> getNetworkInfo() throws ApiException;
}
