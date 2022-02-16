package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.BFAssetService;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AssetServiceIT extends BaseITTest {

    @Test
    void getAsset() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<Asset> result = service.getAsset("7d14d344fd85ece5a874b931af0813b57f7496aa61ba1ab7d9097646526566726573684e46545465737431");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
        assertNotNull(result.getValue().getOnchainMetadata());
    }

    @Test
    void getAsset1() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<Asset> result = service.getAsset("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses_DESC() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<List<AssetAddress>> result = service.getAssetAddresses("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233", 100, 1, OrderEnum.desc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses_ASC() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<List<AssetAddress>> result = service.getAssetAddresses("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getPolicyAssets_DESC() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<List<PolicyAsset>> result = service.getPolicyAssets("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96", 100, 1, OrderEnum.desc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getPolicyAssets_ASC() throws ApiException {
        AssetService service = new BFAssetService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        Result<List<PolicyAsset>> result = service.getPolicyAssets("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96", 100, 1, OrderEnum.asc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }
}
