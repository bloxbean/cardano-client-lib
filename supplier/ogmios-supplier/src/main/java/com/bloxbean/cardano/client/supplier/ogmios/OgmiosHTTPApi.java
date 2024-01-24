package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.supplier.ogmios.dto.BaseRequestDTO;
import com.bloxbean.cardano.client.supplier.ogmios.dto.ProtocolParametersDTO;
import com.bloxbean.cardano.client.supplier.ogmios.dto.ValidateTransactionDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.util.List;
import java.util.Map;

public interface OgmiosHTTPApi {

    @POST("/")
    Call<BaseRequestDTO<ProtocolParametersDTO>> getProtocolParameters(@Body BaseRequestDTO request);

    @POST("/")
    Call<BaseRequestDTO<Map<String, Map<String, String>>>> submitTransaction(@Body BaseRequestDTO request);

    @POST("/")
    Call<BaseRequestDTO<List<ValidateTransactionDTO>>> validateTransaction(@Body BaseRequestDTO request);

}
