package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KoiosAssetServiceIT extends KoiosBaseTest {

    private AssetService assetService;

    @BeforeEach
    public void setup() {
        assetService = backendService.getAssetService();
    }

    @Test
    void getAsset() throws ApiException {
        Result<Asset> result = assetService.getAsset("7d14d344fd85ece5a874b931af0813b57f7496aa61ba1ab7d9097646526566726573684e46545465737431");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
        assertNotNull(result.getValue().getOnchainMetadata());
    }

    @Test
    void getAsset1() throws ApiException {
        Result<Asset> result = assetService.getAsset("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetToken() throws ApiException {
        Result<Asset> result = assetService.getAsset("34250edd1e9836f5378702fbf9416b709bc140e04f668cc3552085184154414441636f696e");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses() throws ApiException {
        Result<List<AssetAddress>> result = assetService.getAssetAddresses("5c2171471578441ab237b76531539b2d5bfa4193be4aab0466b817f454657374746f6b656e313233", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getPolicyAssets() throws ApiException {
        Result<List<PolicyAsset>> result = assetService.getPolicyAssets("329728f73683fe04364631c27a7912538c116d802416ca1eaf2d7a96", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }
}
