package com.bloxbean.cardano.client.it.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFAssetService;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BFAssetServiceIT extends BFBaseTest {

    @Test
    void getAsset() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        Result<Asset> result = service.getAsset("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96736174636f696e");

        System.out.println(result.getValue());
        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
    }
}
