package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;

public interface EpochService {

    /**
     * Get latest epoch
     * @return
     * @throws ApiException
     */
    public Result<EpochContent> getLatestEpoch() throws ApiException;

    /**
     * Get epoch content by number
     * @param epoch
     * @return
     * @throws ApiException
     */
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException;

    /**
     * Get protocol parameters at epoch
     * @param epoch
     * @return
     * @throws ApiException
     */
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException;

    /**
     * Get current protocol parameters
     * @return
     * @throws ApiException
     */
    public Result<ProtocolParams> getProtocolParameters() throws ApiException;
}
