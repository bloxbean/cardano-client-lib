package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.ogmios.http.dto.BaseRequestDTO;
import com.bloxbean.cardano.client.backend.ogmios.http.dto.ProtocolParametersDTO;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class OgmiosEpochService extends OgmiosBaseService implements EpochService  {
    private final static String QUERY_PROTOCOL_PARAMS_METHOD = "queryLedgerState/protocolParameters";
    private final OgmiosHTTPApi ogmiosHTTPApi;

    public OgmiosEpochService(String baseUrl) {
        super(baseUrl);
        this.ogmiosHTTPApi = getRetrofit().create(OgmiosHTTPApi.class);
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
        BaseRequestDTO request = new BaseRequestDTO(QUERY_PROTOCOL_PARAMS_METHOD);

        Call<BaseRequestDTO<ProtocolParametersDTO>> call = ogmiosHTTPApi.getProtocolParameters(request);
        try {
            Response<BaseRequestDTO<ProtocolParametersDTO>> response = call.execute();
            if (response.isSuccessful() && response.body().getResult() != null) {
                return Result
                        .success(response.toString())
                        .withValue(response.body().getResult().toProtocolParams())
                        .code(response.code());
            }
            else
                return Result
                        .error(response.errorBody().string())
                        .code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
    }
}
