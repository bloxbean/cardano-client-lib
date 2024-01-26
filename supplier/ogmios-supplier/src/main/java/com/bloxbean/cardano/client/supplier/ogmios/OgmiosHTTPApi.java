package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.supplier.ogmios.dto.BaseRequestDto;
import com.bloxbean.cardano.client.supplier.ogmios.dto.EvaluateTransactionResponeDto;
import com.bloxbean.cardano.client.supplier.ogmios.dto.ProtocolParametersDto;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.util.List;
import java.util.Map;

public interface OgmiosHTTPApi {

    @POST("/")
    Call<BaseRequestDto<ProtocolParametersDto>> getProtocolParameters(@Body BaseRequestDto request);

    @POST("/")
    Call<BaseRequestDto<Map<String, Map<String, String>>>> submitTransaction(@Body BaseRequestDto request);

    @POST("/")
    Call<BaseRequestDto<List<EvaluateTransactionResponeDto>>> evaluateTransaction(@Body BaseRequestDto request);

}
