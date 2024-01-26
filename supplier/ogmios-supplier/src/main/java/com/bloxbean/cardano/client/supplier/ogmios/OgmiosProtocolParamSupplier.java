package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.supplier.ogmios.dto.BaseRequestDto;
import com.bloxbean.cardano.client.supplier.ogmios.dto.ProtocolParametersDto;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

@Slf4j
public class OgmiosProtocolParamSupplier extends OgmiosBaseService implements ProtocolParamsSupplier {

    private final static String QUERY_PROTOCOL_PARAMS_METHOD = "queryLedgerState/protocolParameters";
    private final OgmiosHTTPApi ogmiosHTTPApi;

    public OgmiosProtocolParamSupplier(String baseUrl) {
        super(baseUrl);
        ogmiosHTTPApi = getRetrofit().create(OgmiosHTTPApi.class);
    }

    @Override
    public ProtocolParams getProtocolParams() {
        BaseRequestDto request = new BaseRequestDto(QUERY_PROTOCOL_PARAMS_METHOD);

        Call<BaseRequestDto<ProtocolParametersDto>> call = ogmiosHTTPApi.getProtocolParameters(request);
        try {
            Response<BaseRequestDto<ProtocolParametersDto>> response = call.execute();
            if (response.isSuccessful() && response.body().getResult() != null)
                return response.body().getResult().toProtocolParams();
            else
                return null;

        } catch (IOException e) {
            log.error("Error getting protocol params", e);
            return null;
        }
    }
}
