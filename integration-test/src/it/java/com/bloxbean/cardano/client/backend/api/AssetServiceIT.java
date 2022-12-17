package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AssetServiceIT extends BaseITTest {

    @Test
    void getAsset() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<Asset> result = service.getAsset("fbaec8dd4d4405a4a42aec11ce5a0160c01e488f3918b082ccbab705b2fc4b2e41d6f8b04048e9748d1c2a376c81d6b10e6d0b299403ffec6b22a126");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
        assertNotNull(result.getValue().getOnchainMetadata());
    }

    @Test
    void getAsset1() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<Asset> result = service.getAsset("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef4954546f6b656e");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetToken() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<Asset> result = service.getAsset("cdc891fb6e0bbef48e335447a496a97c36a1064dc908a2aa94bee0cf546573744e46542d32");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses_DESC() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<AssetAddress>> result = service.getAssetAddresses("fb2b3a629a09014e28d0a54fc06499af12127c79b0bc1c39478da1dd7449534b59", 100, 1, OrderEnum.desc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses_ASC() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<AssetAddress>> result = service.getAssetAddresses("fb2b3a629a09014e28d0a54fc06499af12127c79b0bc1c39478da1dd7449534b59", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAllAssetAddresses() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<AssetAddress>> result = service.getAllAssetAddresses("fb2b3a629a09014e28d0a54fc06499af12127c79b0bc1c39478da1dd7449534b59");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
        //TODO Find Asset with more than 100 Addresses
    }

    @Test
    void getPolicyAssets_DESC() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<PolicyAsset>> result = service.getPolicyAssets("0df4e527fb4ed572c6aca78a0e641701c70715261810fa6ee98db9ef", 100, 1, OrderEnum.desc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getPolicyAssets_ASC() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<PolicyAsset>> result = service.getPolicyAssets("b3723bcb8a451492c839fbcd322de2403a6c53d0e74006de39cb6ff0", 100, 1, OrderEnum.asc);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAllPolicyAssets() throws ApiException {
        AssetService service = getBackendService().getAssetService();
        Result<List<PolicyAsset>> result = service.getAllPolicyAssets("d611714cf0a96bfc6e0eeb9e8b6b04a1f3653cf9290dae604e4757e8");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertTrue(result.getValue().size() > 100);
        assertNotNull(result.getValue());
    }
}
