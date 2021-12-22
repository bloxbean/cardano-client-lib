package com.bloxbean.cardano.client.backend.gql.adapter;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class AddHeadersInterceptor implements Interceptor {
    private Map<String, String> headers;
    public AddHeadersInterceptor(Map<String, String> headers) {
        this.headers = headers;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request.Builder reqBuilder = chain.request().newBuilder();
        if(headers != null && headers.size() > 0) {
            for(String key: headers.keySet()) {
                reqBuilder.addHeader(key, headers.get(key));
            }
        }
        Request request = reqBuilder.build();
        return chain.proceed(request);
    }
}
