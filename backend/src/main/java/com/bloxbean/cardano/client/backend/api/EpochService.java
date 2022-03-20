package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;

public interface EpochService {

    /**
     * Get latest epoch
     * @return
     * @throws ApiException
     */
    Result<EpochContent> getLatestEpoch() throws ApiException;

    /**
     * Get epoch content by number
     * @param epoch
     * @return
     * @throws ApiException
     */
    Result<EpochContent> getEpoch(Integer epoch) throws ApiException;

    /**
     * Get protocol parameters at epoch
     * @param epoch
     * @return
     * @throws ApiException
     */
    Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException;

    /**
     * Get current protocol parameters
     * @return
     * @throws ApiException
     */
    Result<ProtocolParams> getProtocolParameters() throws ApiException;
}
