package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFNetworkService;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetworkServiceIT extends BaseITTest {

    @Test
    public void testGetNetworkInfo() throws ApiException, JsonProcessingException {
        NetworkInfoService networkInfoService = new BFNetworkService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<Genesis> result = networkInfoService.getNetworkInfo();

        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
    }

}
