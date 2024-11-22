package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AddressService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.AddressesApi;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
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
    public Result<AddressDetails> getAddressDetails(String address) throws ApiException {
        Call<AddressDetails> call = addressApi.getAddressDetails(getProjectId(), address);
        try {
            Response<AddressDetails> response = call.execute();
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

    public Result<List<AddressTransactionContent>> getAllTransactions(String address, OrderEnum order, Integer fromBlockHeight, Integer toBlockHeight) throws ApiException {
        List<AddressTransactionContent> addressTransactionContents = new ArrayList<>();
        int page = 1;
        Result<List<AddressTransactionContent>> addressTransactionContentsResult = getTransactions(address, 100, page, order, String.valueOf(fromBlockHeight), String.valueOf(toBlockHeight));
        while (addressTransactionContentsResult.isSuccessful()) {
            addressTransactionContents.addAll(addressTransactionContentsResult.getValue());
            if (addressTransactionContentsResult.getValue().size() != 100) {
                break;
            } else {
                page++;
                addressTransactionContentsResult = getTransactions(address, 100, page, order, String.valueOf(fromBlockHeight), String.valueOf(toBlockHeight));
            }
        }
        if (!addressTransactionContentsResult.isSuccessful()) {
            return addressTransactionContentsResult;
        } else {
            addressTransactionContents.sort((o1, o2) ->
                    order == OrderEnum.asc ?
                            Long.compare(o1.getBlockHeight(), o2.getBlockHeight()) :
                            Long.compare(o2.getBlockHeight(), o1.getBlockHeight()));
            return Result.success(addressTransactionContentsResult.toString()).withValue(addressTransactionContents).code(addressTransactionContentsResult.code());
        }
    }
}
