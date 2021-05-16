package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.model.Result;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

public class BFBaseService {
    private String baseUrl;
    private String projectId;

    public BFBaseService(String baseUrl, String projectId) {
        this.baseUrl = baseUrl;
        this.projectId = projectId;
    }

    protected Retrofit getRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(getBaseUrl())
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getProjectId() {
        return projectId;
    }

    protected  <T> Result<T> processResponse(Response<T> response) throws IOException {

            if(response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

    }

}
