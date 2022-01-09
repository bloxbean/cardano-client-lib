package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.model.Result;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

@Slf4j
public class BFBaseService {

    private final String baseUrl;
    private final String projectId;

    public BFBaseService(String baseUrl, String projectId) {
        this.baseUrl = baseUrl;
        this.projectId = projectId;

        if (log.isDebugEnabled()) {
            log.debug("Blockfrost URL : " + baseUrl);
        }
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

    protected <T> Result<T> processResponse(Response<T> response) throws IOException {
        if (response.isSuccessful())
            return Result.success(response.toString()).withValue(response.body()).code(response.code());
        else
            return Result.error(response.errorBody().string()).code(response.code());
    }
}
