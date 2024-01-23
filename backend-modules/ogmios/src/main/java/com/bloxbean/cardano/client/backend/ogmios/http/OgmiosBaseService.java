package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.api.model.Result;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;

@Slf4j
public class OgmiosBaseService {

    private final String baseUrl;

    public OgmiosBaseService(String baseUrl) {
        this.baseUrl = baseUrl;

        if (log.isDebugEnabled()) {
            log.debug("Ogmios URL : " + baseUrl);
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
}