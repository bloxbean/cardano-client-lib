package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.supplier.ogmios.OgmiosProtocolParamSupplier;

public class OgmiosEpochService implements EpochService  {
    private final OgmiosProtocolParamSupplier ogmiosProtocolParamSupplier;

    public OgmiosEpochService(String baseUrl) {
        this.ogmiosProtocolParamSupplier = new OgmiosProtocolParamSupplier(baseUrl);
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() throws ApiException {
        ProtocolParams protocolParams = ogmiosProtocolParamSupplier.getProtocolParams();

        Result<ProtocolParams> result;
        if(protocolParams != null) {
            return Result.success("success")
                    .withValue(protocolParams)
                    .code(200);
        } else {
            result = Result.error("Error fetching protocol params");
        }
        return result;
    }
}
