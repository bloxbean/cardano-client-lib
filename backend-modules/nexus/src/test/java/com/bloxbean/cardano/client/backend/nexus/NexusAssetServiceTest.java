package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.asset.model.AssetDetailedInformation;
import adlabs.nexus.client.backend.api.asset.model.AssetMetadata;
import adlabs.nexus.client.backend.api.asset.model.PaymentAddress;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NexusAssetServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;
    private static final String POLICY_ID = "abababababababababababababababababababababababababababab";
    private static final String ASSET_NAME = "546f6b656e";
    private static final String UNIT = POLICY_ID + ASSET_NAME;

    @Test
    void getAsset_maps() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        AssetDetailedInformation info = AssetDetailedInformation.builder()
                .policyId(POLICY_ID)
                .assetName(ASSET_NAME)
                .fingerprint("asset1xyz")
                .quantity("1000")
                .initialMintTxHash("txhash1")
                .mintOrBurnCount(2)
                .onchainMetadata("{\"name\":\"onchain\"}")
                .metadata(AssetMetadata.builder().name("Token").decimals(6).build())
                .build();
        when(sdkAssetSvc.getAssetDetailedInformation(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<Asset> r = svc.getAsset(UNIT);

        assertThat(r.isSuccessful()).isTrue();
        Asset asset = r.getValue();
        assertThat(asset.getAsset()).isEqualTo(UNIT);
        assertThat(asset.getPolicyId()).isEqualTo(POLICY_ID);
        assertThat(asset.getAssetName()).isEqualTo(ASSET_NAME);
        assertThat(asset.getFingerprint()).isEqualTo("asset1xyz");
        assertThat(asset.getQuantity()).isEqualTo("1000");
        assertThat(asset.getInitialMintTxHash()).isEqualTo("txhash1");
        assertThat(asset.getMintOrBurnCount()).isEqualTo(2);
        assertThat(asset.getOnchainMetadata().get("name").asText()).isEqualTo("onchain");
        assertThat(asset.getMetadata().get("name").asText()).isEqualTo("Token");
        assertThat(asset.getMetadata().get("decimals").asInt()).isEqualTo(6);
    }

    @Test
    void getAsset_noAssetName_splitsWithEmptyAssetName() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        AssetDetailedInformation info = AssetDetailedInformation.builder()
                .policyId(POLICY_ID)
                .assetName("")
                .quantity("1")
                .build();
        when(sdkAssetSvc.getAssetDetailedInformation(eq(NET), eq(POLICY_ID), eq("")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<Asset> r = svc.getAsset(POLICY_ID);

        assertThat(r.isSuccessful()).isTrue();
        verify(sdkAssetSvc, times(1)).getAssetDetailedInformation(NET, POLICY_ID, "");
        assertThat(r.getValue().getPolicyId()).isEqualTo(POLICY_ID);
        assertThat(r.getValue().getAssetName()).isEqualTo("");
    }

    @Test
    void getAsset_nullOnchainMetadataAndMetadata_leftNull() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        AssetDetailedInformation info = AssetDetailedInformation.builder()
                .policyId(POLICY_ID)
                .assetName(ASSET_NAME)
                .quantity("1")
                .build();
        when(sdkAssetSvc.getAssetDetailedInformation(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<Asset> r = svc.getAsset(UNIT);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getOnchainMetadata()).isNull();
        assertThat(r.getValue().getMetadata()).isNull();
    }

    @Test
    void getAsset_policyIdOnlyUnit_boundaryGuard_verifiesEmptyAssetNameAndReturnsMappedAsset() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        AssetDetailedInformation info = AssetDetailedInformation.builder()
                .policyId(POLICY_ID)
                .assetName("")
                .fingerprint("asset1noname")
                .quantity("42")
                .build();
        when(sdkAssetSvc.getAssetDetailedInformation(eq(NET), eq(POLICY_ID), eq("")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        // A unit that is exactly 56 hex chars (policyId only, no asset name suffix) exercises
        // the substring boundary guard rather than throwing StringIndexOutOfBoundsException.
        Result<Asset> r = svc.getAsset(POLICY_ID);

        verify(sdkAssetSvc, times(1)).getAssetDetailedInformation(NET, POLICY_ID, "");
        assertThat(r.isSuccessful()).isTrue();
        Asset asset = r.getValue();
        assertThat(asset.getAsset()).isEqualTo(POLICY_ID);
        assertThat(asset.getPolicyId()).isEqualTo(POLICY_ID);
        assertThat(asset.getAssetName()).isEqualTo("");
        assertThat(asset.getFingerprint()).isEqualTo("asset1noname");
        assertThat(asset.getQuantity()).isEqualTo("42");
    }

    @Test
    void getAsset_malformedOnchainMetadata_leftNull_noExceptionPropagates() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        AssetDetailedInformation info = AssetDetailedInformation.builder()
                .policyId(POLICY_ID)
                .assetName(ASSET_NAME)
                .quantity("1")
                .onchainMetadata("{not valid json")
                .build();
        when(sdkAssetSvc.getAssetDetailedInformation(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, info));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<Asset> r = svc.getAsset(UNIT);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getOnchainMetadata()).isNull();
    }

    @Test
    void getAsset_error_propagates() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getAssetDetailedInformation(any(), any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<Asset> r = svc.getAsset(UNIT);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getAsset_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getAssetDetailedInformation(any(), any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusAssetService(sdkAssetSvc, NET);

        assertThatThrownBy(() -> svc.getAsset(UNIT))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    // ---- getAllAssetAddresses / getAssetAddresses ----

    private static List<PaymentAddress> threePaymentAddressRows() {
        return List.of(
                PaymentAddress.builder().paymentAddress("addr1aaa").stakeAddress("stake1aaa").build(),
                PaymentAddress.builder().paymentAddress("addr1bbb").stakeAddress("stake1bbb").build(),
                PaymentAddress.builder().paymentAddress("addr1ccc").stakeAddress("stake1ccc").build()
        );
    }

    @Test
    void getAllAssetAddresses_splitsUnitAndMapsFullList_quantityNull() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getNftAddress(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threePaymentAddressRows()));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<List<AssetAddress>> r = svc.getAllAssetAddresses(UNIT);

        verify(sdkAssetSvc, times(1)).getNftAddress(NET, POLICY_ID, ASSET_NAME);
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).extracting(AssetAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb", "addr1ccc");
        assertThat(r.getValue()).allSatisfy(a -> assertThat(a.getQuantity()).isNull());
    }

    @Test
    void getAssetAddresses_paginatesPage1AndPage2() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getNftAddress(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threePaymentAddressRows()));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<List<AssetAddress>> page1 = svc.getAssetAddresses(UNIT, 2, 1);
        Result<List<AssetAddress>> page2 = svc.getAssetAddresses(UNIT, 2, 2);

        assertThat(page1.getValue()).extracting(AssetAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb");
        assertThat(page2.getValue()).extracting(AssetAddress::getAddress)
                .containsExactly("addr1ccc");
    }

    @Test
    void getAssetAddresses_withOrder_delegatesSamePagination() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getNftAddress(eq(NET), eq(POLICY_ID), eq(ASSET_NAME)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threePaymentAddressRows()));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<List<AssetAddress>> r = svc.getAssetAddresses(UNIT, 2, 1, OrderEnum.desc);

        assertThat(r.getValue()).extracting(AssetAddress::getAddress)
                .containsExactly("addr1aaa", "addr1bbb");
    }

    @Test
    void getAllAssetAddresses_policyIdOnlyUnit_splitsWithEmptyAssetName() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getNftAddress(eq(NET), eq(POLICY_ID), eq("")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, threePaymentAddressRows()));

        var svc = new NexusAssetService(sdkAssetSvc, NET);
        Result<List<AssetAddress>> r = svc.getAllAssetAddresses(POLICY_ID);

        verify(sdkAssetSvc, times(1)).getNftAddress(NET, POLICY_ID, "");
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(3);
    }

    @Test
    void getAllAssetAddresses_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        when(sdkAssetSvc.getNftAddress(any(), any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("addresses boom"));

        var svc = new NexusAssetService(sdkAssetSvc, NET);

        assertThatThrownBy(() -> svc.getAllAssetAddresses(UNIT))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("addresses boom");
    }

    @Test
    void getPolicyAssets_unsupported() {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        var svc = new NexusAssetService(sdkAssetSvc, NET);

        assertThatThrownBy(() -> svc.getPolicyAssets(POLICY_ID, 10, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getTransactions_unsupported() {
        var sdkAssetSvc = mock(adlabs.nexus.client.backend.api.asset.AssetService.class);
        var svc = new NexusAssetService(sdkAssetSvc, NET);

        assertThatThrownBy(() -> svc.getTransactions(UNIT, 10, 1, com.bloxbean.cardano.client.api.common.OrderEnum.asc))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
