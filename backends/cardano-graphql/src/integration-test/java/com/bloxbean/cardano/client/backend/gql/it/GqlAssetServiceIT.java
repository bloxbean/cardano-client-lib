package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.GqlAssetService;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GqlAssetServiceIT extends GqlBaseTest {

    @Test
    void getAsset() throws ApiException {
        Result<Asset> result = backendService.getAssetService().getAsset("7180cf30d4f4db3037bd815f89f0b348a10e31e11f0a40e6993c8189594f5550");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertTrue(result.code() == 200);
        assertNotNull(result.getValue());
        assertEquals("7180cf30d4f4db3037bd815f89f0b348a10e31e11f0a40e6993c8189", result.getValue().getPolicyId());
        assertEquals("594f5550", result.getValue().getAssetName());
        assertEquals("3000", result.getValue().getQuantity());
        assertEquals("asset1ls5765fpzkaejqjr0gwr0s8t93ldux6ldd66f2", result.getValue().getFingerprint());
        assertEquals("41ae57ed49f69aa0c2e2fb0b6e63a8e0620f85e8f955bc4975bd2f4e9f7e124b", result.getValue().getInitialMintTxHash());
    }
}
