package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.*;
import com.bloxbean.cardano.client.backend.gql.adapter.AddHeadersInterceptor;
import okhttp3.OkHttpClient;

import java.time.Duration;
import java.util.Map;

import static com.bloxbean.cardano.client.backend.gql.util.HttpClientConstants.GQL_CALL_TIMEOUT_SEC;
import static com.bloxbean.cardano.client.backend.gql.util.HttpClientConstants.GQL_READ_TIMEOUT_SEC;

public class GqlBackendService implements BackendService {
    private String gqlUrl;
    private Map<String, String> headers;
    private OkHttpClient okHttpClient;

    public GqlBackendService(String gqlUrl) {
        this.gqlUrl = gqlUrl;
    }

    public GqlBackendService(String gqlUrl, Map<String, String> headers) {
        this.gqlUrl = gqlUrl;
        this.headers = headers;

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.callTimeout( Duration.ofSeconds(GQL_CALL_TIMEOUT_SEC));
        okHttpClientBuilder.readTimeout(Duration.ofSeconds(GQL_READ_TIMEOUT_SEC));
        if(headers != null && headers.size() > 0) {
            okHttpClientBuilder.addInterceptor(new AddHeadersInterceptor(headers));
        }

        this.okHttpClient = okHttpClientBuilder.build();
    }

    public GqlBackendService(String gqlUrl, OkHttpClient client) {
        this.gqlUrl = gqlUrl;
        this.okHttpClient = client;
    }

    @Override
    public AssetService getAssetService() {
        if(okHttpClient != null) {
            return new GqlAssetService(this.gqlUrl, okHttpClient);
        } else {
            return new GqlAssetService(this.gqlUrl);
        }
    }

    @Override
    public BlockService getBlockService() {
        if(okHttpClient != null) {
            return new GqlBlockService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlBlockService(this.gqlUrl);
        }
    }

    @Override
    public NetworkInfoService getNetworkInfoService() {
        if(okHttpClient != null) {
            return new GqlNetworkInfoService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlNetworkInfoService(this.gqlUrl);
        }
    }

    @Override
    public TransactionService getTransactionService() {
        if(okHttpClient != null) {
            return new GqlTransactionService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlTransactionService(this.gqlUrl);
        }
    }

    @Override
    public UtxoService getUtxoService() {
        if(okHttpClient != null) {
            return new GqlUtxoService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlUtxoService(this.gqlUrl);
        }
    }

    @Override
    public AddressService getAddressService() {
        if(okHttpClient != null) {
            return new GqlAddressService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlAddressService(this.gqlUrl);
        }
    }

    @Override
    public EpochService getEpochService() {
        if(okHttpClient != null) {
            return new GqlEpochService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlEpochService(this.gqlUrl);
        }
    }

    @Override
    public MetadataService getMetadataService() {
        if(okHttpClient != null) {
            return new GqlMetadataService(this.gqlUrl, this.okHttpClient);
        } else {
            return new GqlMetadataService(this.gqlUrl);
        }
    }

    public void shutdown() {
        if(okHttpClient == null)
            return;

        try {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
            okHttpClient.cache().close();
        } catch (Exception e) {

        }
    }
}
