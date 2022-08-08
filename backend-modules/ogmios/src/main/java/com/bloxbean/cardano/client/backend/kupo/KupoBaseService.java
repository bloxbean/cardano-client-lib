package com.bloxbean.cardano.client.backend.kupo;

import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class KupoBaseService {

    private final String kupoBaseUrl;

    public KupoBaseService(String kupoBaseUrl) {
        this.kupoBaseUrl = kupoBaseUrl;

        if (log.isDebugEnabled()) {
            log.debug("Kupo URL : " + kupoBaseUrl);
        }
    }

    protected Retrofit getRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(getKupoBaseUrl())
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
    }

    public String getKupoBaseUrl() {
        return kupoBaseUrl;
    }

}
