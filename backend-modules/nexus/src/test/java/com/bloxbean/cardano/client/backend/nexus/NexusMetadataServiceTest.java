package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.metadata.model.LabelMetadataCbor;
import adlabs.nexus.client.backend.api.metadata.model.LabelMetadataJson;
import adlabs.nexus.client.backend.api.metadata.model.MetadataLabel;
import adlabs.nexus.client.backend.api.metadata.model.TxMetadataCbor;
import adlabs.nexus.client.backend.api.metadata.model.TxMetadataJson;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusMetadataServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getJSONMetadataByTxnHash_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        JsonNode json = objectMapper.readTree("{\"msg\":\"hello\"}");
        TxMetadataJson m = TxMetadataJson.builder().label("674").json(json).build();
        when(sdkSvc.getTxMetadata(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(m)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataJSONContent>> r = svc.getJSONMetadataByTxnHash("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        MetadataJSONContent content = r.getValue().get(0);
        assertThat(content.getTxHash()).isEqualTo("txh1");
        assertThat(content.getLabel()).isEqualTo("674");
        assertThat(content.getJsonMetadata()).isEqualTo(json);
    }

    @Test
    void getJSONMetadataByTxnHash_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getTxMetadata(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataJSONContent>> r = svc.getJSONMetadataByTxnHash("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getJSONMetadataByTxnHash_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getTxMetadata(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.getJSONMetadataByTxnHash("txh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getCBORMetadataByTxnHash_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        TxMetadataCbor m = TxMetadataCbor.builder().label("674").cbor("a165636f6e6e65").build();
        when(sdkSvc.getTxMetadataCbor(eq(adlabs.nexus.client.util.Network.MAINNET), eq("txh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(m)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataCBORContent>> r = svc.getCBORMetadataByTxnHash("txh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        MetadataCBORContent content = r.getValue().get(0);
        assertThat(content.getTxHash()).isEqualTo("txh1");
        assertThat(content.getLabel()).isEqualTo("674");
        assertThat(content.getCborMetadata()).isEqualTo("a165636f6e6e65");
    }

    @Test
    void getCBORMetadataByTxnHash_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getTxMetadataCbor(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataCBORContent>> r = svc.getCBORMetadataByTxnHash("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getMetadataLabels_mapsAndAdaptsCountAndPageArgOrder() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        MetadataLabel label = MetadataLabel.builder().label("674").cip10("cip10-msg").count(42L).build();
        // bloxbean arg order is (count, page); SDK is (page, pageSize) - verify count -> pageSize, page -> page.
        when(sdkSvc.getMetadataLabels(eq(adlabs.nexus.client.util.Network.MAINNET), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(label)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel>> r =
                svc.getMetadataLabels(10, 2, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel content = r.getValue().get(0);
        assertThat(content.getLabel()).isEqualTo("674");
        assertThat(content.getCip10()).isEqualTo("cip10-msg");
        assertThat(content.getCount()).isEqualTo(42);
    }

    @Test
    void getMetadataLabels_nullCount_staysNull() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        MetadataLabel label = MetadataLabel.builder().label("674").cip10(null).count(null).build();
        when(sdkSvc.getMetadataLabels(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(label)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel>> r =
                svc.getMetadataLabels(10, 1, OrderEnum.asc);

        assertThat(r.getValue().get(0).getCount()).isNull();
    }

    @Test
    void getMetadataLabels_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getMetadataLabels(any(), any(Integer.class), any(Integer.class))).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(500, "boom"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel>> r =
                svc.getMetadataLabels(10, 1, OrderEnum.asc);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(500);
    }

    @Test
    void getJSONMetadataByLabel_mapsLabelToStringAndPageArgOrder() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        JsonNode json = objectMapper.readTree("{\"msg\":\"hi\"}");
        LabelMetadataJson m = LabelMetadataJson.builder().txHash("txh1").json(json).build();
        when(sdkSvc.getMetadataByLabel(eq(adlabs.nexus.client.util.Network.MAINNET), eq("674"), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(m)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataJSONContent>> r = svc.getJSONMetadataByLabel(BigInteger.valueOf(674), 10, 2, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        MetadataJSONContent content = r.getValue().get(0);
        assertThat(content.getTxHash()).isEqualTo("txh1");
        assertThat(content.getLabel()).isEqualTo("674");
        assertThat(content.getJsonMetadata()).isEqualTo(json);
    }

    @Test
    void getJSONMetadataByLabel_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getMetadataByLabel(any(), any(), any(Integer.class), any(Integer.class))).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataJSONContent>> r = svc.getJSONMetadataByLabel(BigInteger.valueOf(674), 10, 1, OrderEnum.asc);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getCBORMetadataByLabel_mapsLabelToStringAndPageArgOrder() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        LabelMetadataCbor m = LabelMetadataCbor.builder().txHash("txh1").cbor("a165636f6e6e65").build();
        when(sdkSvc.getMetadataCborByLabel(eq(adlabs.nexus.client.util.Network.MAINNET), eq("674"), eq(2), eq(10)))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, List.of(m)));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataCBORContent>> r = svc.getCBORMetadataByLabel(BigInteger.valueOf(674), 10, 2, OrderEnum.desc);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).hasSize(1);
        MetadataCBORContent content = r.getValue().get(0);
        assertThat(content.getTxHash()).isEqualTo("txh1");
        assertThat(content.getLabel()).isEqualTo("674");
        assertThat(content.getCborMetadata()).isEqualTo("a165636f6e6e65");
    }

    @Test
    void getCBORMetadataByLabel_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getMetadataCborByLabel(any(), any(), any(Integer.class), any(Integer.class))).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<List<MetadataCBORContent>> r = svc.getCBORMetadataByLabel(BigInteger.valueOf(674), 10, 1, OrderEnum.asc);

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getCBORMetadataByLabel_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.metadata.MetadataService.class);
        when(sdkSvc.getMetadataCborByLabel(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusMetadataService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.getCBORMetadataByLabel(BigInteger.valueOf(674), 10, 1, OrderEnum.asc))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
