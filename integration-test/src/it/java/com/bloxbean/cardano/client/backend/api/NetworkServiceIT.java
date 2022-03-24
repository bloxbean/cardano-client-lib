package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetworkServiceIT extends BaseITTest {

    @Test
    public void testGetNetworkInfo() throws ApiException, JsonProcessingException {
        NetworkInfoService networkInfoService = getBackendService().getNetworkInfoService();
        Result<Genesis> result = networkInfoService.getNetworkInfo();

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
    }

}
