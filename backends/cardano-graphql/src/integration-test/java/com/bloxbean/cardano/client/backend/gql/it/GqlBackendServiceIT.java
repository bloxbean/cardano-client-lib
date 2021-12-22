package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.GqlBackendService;
import com.bloxbean.cardano.client.backend.gql.adapter.AddHeadersInterceptor;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.bloxbean.cardano.client.backend.gql.util.HttpClientConstants.GQL_CALL_TIMEOUT_SEC;
import static com.bloxbean.cardano.client.backend.gql.util.HttpClientConstants.GQL_READ_TIMEOUT_SEC;
import static org.junit.jupiter.api.Assertions.*;

public class GqlBackendServiceIT {

    @Test
    public void testCreateBackendServiceWithUrl() throws ApiException {
        BackendService backendService = new GqlBackendService(Constant.GQL_URL);
        NetworkInfoService networkInfoService = backendService.getNetworkInfoService();

        getNetworkInfoAndCompare(networkInfoService);
    }

    @Test
    public void testCreateBackendServiceWithUrlAndHeaders() throws ApiException {
        Map<String, String> headers = new HashMap<>();
        headers.put("AuthKey", "Some Auth key");
        headers.put("CustomHeader", "Some header");

        BackendService backendService = new GqlBackendService(Constant.GQL_URL, headers);
        NetworkInfoService networkInfoService = backendService.getNetworkInfoService();

        getNetworkInfoAndCompare(networkInfoService);
    }

    @Test
    public void testCreateBackendServiceWithUrlAndCustomOkHttpClient() throws ApiException {
        Map<String, String> headers = new HashMap<>();
        headers.put("AuthKey", "Some Auth key");
        headers.put("CustomHeader", "Some header");

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.callTimeout( Duration.ofSeconds(GQL_CALL_TIMEOUT_SEC));
        okHttpClientBuilder.readTimeout(Duration.ofSeconds(GQL_READ_TIMEOUT_SEC));
        if(headers != null && headers.size() > 0) {
            okHttpClientBuilder.addInterceptor(new AddHeadersInterceptor(headers));
        }

        BackendService backendService = new GqlBackendService(Constant.GQL_URL, okHttpClientBuilder.build());

        NetworkInfoService networkInfoService = backendService.getNetworkInfoService();

        getNetworkInfoAndCompare(networkInfoService);
    }

    @Test
    public void testCreateBackendServiceWithUrlAndCustomOkHttpClientTimeoutError() throws ApiException {
        Map<String, String> headers = new HashMap<>();
        headers.put("AuthKey", "Some Auth key");
        headers.put("CustomHeader", "Some header");

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder();
        okHttpClientBuilder.callTimeout( Duration.ofMillis(50));
        okHttpClientBuilder.readTimeout(Duration.ofMillis(50));
        if(headers != null && headers.size() > 0) {
            okHttpClientBuilder.addInterceptor(new AddHeadersInterceptor(headers));
        }

        assertThrows(Exception.class, () -> {
            BackendService backendService = new GqlBackendService(Constant.GQL_URL, okHttpClientBuilder.build());
            NetworkInfoService networkInfoService = backendService.getNetworkInfoService();
            getNetworkInfoAndCompare(networkInfoService);
        });
    }

    public void getNetworkInfoAndCompare(NetworkInfoService networkInfoService) throws ApiException {
        Result<Genesis> gensisResult = networkInfoService.getNetworkInfo();

        Genesis genesis = gensisResult.getValue();
        assertNotNull(genesis);
        assertEquals(genesis.getActiveSlotsCoefficient().doubleValue(), 0.05);
        assertEquals(genesis.getEpochLength(), 432000);
        assertEquals(genesis.getMaxKesEvolutions(), 62);
        assertEquals(genesis.getMaxLovelaceSupply(), "45000000000000000");
        assertEquals(genesis.getNetworkMagic(), 1097911063);
        assertEquals(genesis.getSecurityParam(), 2160);
        assertEquals(genesis.getSlotLength(), 1);
        assertEquals(genesis.getSlotsPerKesPeriod(), 129600);
    }
}
