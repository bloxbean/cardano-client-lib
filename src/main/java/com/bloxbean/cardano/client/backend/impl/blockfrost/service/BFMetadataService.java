package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.MetadataService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.MetadataApi;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

public class BFMetadataService extends BFBaseService implements MetadataService {

    private MetadataApi metadataApi;

    public BFMetadataService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.metadataApi = getRetrofit().create(MetadataApi.class);
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByTxnHash(String txnHash) throws ApiException {
        Call<List<MetadataJSONContent>> call = metadataApi.getJSONMetadataByTxnHash(getProjectId(), txnHash);

        try {
            Response<List<MetadataJSONContent>> response = call.execute();
            if (response.isSuccessful()) {
                List<MetadataJSONContent> value = response.body();
                if(value != null) {
                    value.stream().forEach(v -> v.setTxHash(txnHash));
                }
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            } else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting metadata for the transaction" , e);
        }
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByTxnHash(String txnHash) throws ApiException {
        Call<List<MetadataCBORContent>> call = metadataApi.getCBORMetadataByTxnHash(getProjectId(), txnHash);

        try {
            Response<List<MetadataCBORContent>> response = call.execute();
            if (response.isSuccessful()) {
                List<MetadataCBORContent> value = response.body();
                if(value != null) {
                    value.stream().forEach(v -> v.setTxHash(txnHash));
                }
                return Result.success(response.toString()).withValue(value).code(response.code());
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }

        } catch (IOException e) {
            throw new ApiException("Error getting cbor metadata for the transaction" , e);
        }
    }

    @Override
    public Result<List<MetadataLabel>> getMetadataLabels(int count, int page, OrderEnum order) throws ApiException {
        if(order == null)
            order = OrderEnum.asc;

        Call<List<MetadataLabel>> call = metadataApi.getMetadataLabels(getProjectId(), count, page, order.toString());

        try {
            Response<List<MetadataLabel>> response = call.execute();
            if (response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting metadata labels" , e);
        }
    }

    @Override
    public Result<List<MetadataJSONContent>> getJSONMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        if(order == null)
            order = OrderEnum.asc;

        Call<List<MetadataJSONContent>> call = metadataApi.getJSONMetadataByLabel(getProjectId(), label, count, page, order.toString());

        try {
            Response<List<MetadataJSONContent>> response = call.execute();
            if (response.isSuccessful()) {
                List<MetadataJSONContent> values = response.body();
                if(values != null)
                    values.forEach(v -> v.setLabel(String.valueOf(label)));
                return Result.success(response.toString()).withValue(values).code(response.code());
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }

        } catch (IOException e) {
            throw new ApiException("Error getting metadata by label" , e);
        }
    }

    @Override
    public Result<List<MetadataCBORContent>> getCBORMetadataByLabel(BigInteger label, int count, int page, OrderEnum order) throws ApiException {
        if(order == null)
            order = OrderEnum.asc;

        Call<List<MetadataCBORContent>> call = metadataApi.getCBORMetadatabyLabel(getProjectId(), label, count, page, order.toString());

        try {
            Response<List<MetadataCBORContent>> response = call.execute();
            if (response.isSuccessful()) {
                List<MetadataCBORContent> values = response.body();
                if(values != null)
                    values.forEach(v -> v.setLabel(label.toString()));
                return Result.success(response.toString()).withValue(values).code(response.code());
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }

        } catch (IOException e) {
            throw new ApiException("Error getting cbor metadata by label" , e);
        }
    }
}
