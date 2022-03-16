package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;

public class DefaultProtocolParamsSupplier implements ProtocolParamsSupplier {
    private EpochService epochService;

    public DefaultProtocolParamsSupplier(EpochService epochService) {
        this.epochService = epochService;
    }

    @Override
    public ProtocolParams getProtocolParams() {
        try {
            Result<ProtocolParams> result = epochService.getProtocolParameters();
            if (result.isSuccessful())
                return result.getValue();
            else
                throw new ApiRuntimeException("Error fetching protocol params : " + result);
        } catch (ApiException apiException) {
            throw new ApiRuntimeException("Error fetching protocol params", apiException);
        }
    }
}
