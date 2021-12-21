package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.AddressesApi;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import com.bloxbean.cardano.client.backend.model.Result;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

public class BFAddressService extends BFBaseService implements AddressService {

    private AddressesApi addressApi;

    public BFAddressService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.addressApi = getRetrofit().create(AddressesApi.class);
    }

    @Override
    public Result<AddressContent> getAddressInfo(String address) throws ApiException {
        Call<AddressContent> call = addressApi.getAddressInfo(getProjectId(), address);

        try {
            Response<AddressContent> response = call.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error getting addressInfo", e);
        }
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page) throws ApiException {
        return getTransactions(address, count, page, OrderEnum.asc);
    }

    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order) throws ApiException {
        return getTransactions(address, count, page, order, null, null);
    }

    @Override
    public Result<List<AddressTransactionContent>> getTransactions(String address, int count, int page, OrderEnum order, String from, String to) throws ApiException {
        Call<List<AddressTransactionContent>> call = addressApi.getTransactions(getProjectId(), address, count, page, order.toString(), from, to);

        try {
            Response<List<AddressTransactionContent>> response = call.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error getting transactions for the address", e);
        }
    }
}
