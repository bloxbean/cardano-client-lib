package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BFAssetServiceIT extends BFBaseTest {

    @Test
    void getAsset() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        Result<Asset> result = service.getAsset("7d14d344fd85ece5a874b931af0813b57f7496aa61ba1ab7d9097646526566726573684e46545465737431");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
        assertNotNull(result.getValue().getOnchainMetadata());
    }

    @Test
    void getAsset1() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, projectId);
        Result<Asset> result = service.getAsset("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
    }
}
