package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KoiosAssetServiceIT extends KoiosBaseTest {

    private AssetService assetService;

    @BeforeEach
    public void setup() {
        assetService = backendService.getAssetService();
    }

    @Test
    void getAsset() throws ApiException {
        Result<Asset> result = assetService.getAsset("80de4ee0ffde8ba05726707f2adba0e65963eff5aaba164af358e71b53746162696c697479506f6f6c5f54657374");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
        assertNotNull(result.getValue().getOnchainMetadata());
    }

    @Test
    void getAsset1() throws ApiException {
        Result<Asset> result = assetService.getAsset("80de4ee0ffde8ba05726707f2adba0e65963eff5aaba164af358e71b53746162696c697479506f6f6c5f54657374");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetToken() throws ApiException {
        Result<Asset> result = assetService.getAsset("80de4ee0ffde8ba05726707f2adba0e65963eff5aaba164af358e71b53746162696c697479506f6f6c5f54657374");

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getAssetAddresses() throws ApiException {
        Result<List<AssetAddress>> result = assetService.getAssetAddresses("80de4ee0ffde8ba05726707f2adba0e65963eff5aaba164af358e71b53746162696c697479506f6f6c5f54657374", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }

    @Test
    void getPolicyAssets() throws ApiException {
        Result<List<PolicyAsset>> result = assetService.getPolicyAssets("80de4ee0ffde8ba05726707f2adba0e65963eff5aaba164af358e71b", 100, 1);

        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }
}
