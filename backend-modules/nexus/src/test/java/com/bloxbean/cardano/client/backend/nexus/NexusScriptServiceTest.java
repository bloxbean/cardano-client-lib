package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.script.model.Datum;
import adlabs.nexus.client.backend.api.script.model.ScriptDetail;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusScriptServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getScriptDatum_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        JsonNode json = objectMapper.readTree("{\"constructor\":0,\"fields\":[]}");
        Datum datum = Datum.builder().hash("dh1").cbor("d8799f00ff").json(json).build();
        when(sdkSvc.getDatumByHash(eq(NET), eq("dh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, datum));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<ScriptDatum> r = svc.getScriptDatum("dh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getJsonValue()).isEqualTo(json);
    }

    @Test
    void getScriptDatum_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getDatumByHash(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<ScriptDatum> r = svc.getScriptDatum("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getScriptDatum_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getDatumByHash(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusScriptService(sdkSvc, NET);

        assertThatThrownBy(() -> svc.getScriptDatum("dh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getScriptDatumCbor_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        Datum datum = Datum.builder().hash("dh1").cbor("d8799f00ff").json(null).build();
        when(sdkSvc.getDatumByHash(eq(NET), eq("dh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, datum));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<ScriptDatumCbor> r = svc.getScriptDatumCbor("dh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getCbor()).isEqualTo("d8799f00ff");
    }

    @Test
    void getScriptDatumCbor_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getDatumByHash(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<ScriptDatumCbor> r = svc.getScriptDatumCbor("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getScriptDatumCbor_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getDatumByHash(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusScriptService(sdkSvc, NET);

        assertThatThrownBy(() -> svc.getScriptDatumCbor("dh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getNativeScriptJson_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        JsonNode json = objectMapper.readTree("{\"type\":\"sig\",\"keyHash\":\"abc\"}");
        ScriptDetail detail = ScriptDetail.builder().hash("sh1").type("native").json(json).build();
        when(sdkSvc.getScriptByHash(eq(NET), eq("sh1")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, detail));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<JsonNode> r = svc.getNativeScriptJson("sh1");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEqualTo(json);
    }

    @Test
    void getNativeScriptJson_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getScriptByHash(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<JsonNode> r = svc.getNativeScriptJson("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getNativeScriptJson_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getScriptByHash(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusScriptService(sdkSvc, NET);

        assertThatThrownBy(() -> svc.getNativeScriptJson("sh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getPlutusScriptCbor_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        ScriptDetail detail = ScriptDetail.builder().hash("sh2").type("plutusV2").cbor("590a1b...").build();
        when(sdkSvc.getScriptByHash(eq(NET), eq("sh2")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, detail));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<String> r = svc.getPlutusScriptCbor("sh2");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue()).isEqualTo("590a1b...");
    }

    @Test
    void getPlutusScriptCbor_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getScriptByHash(any(), any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusScriptService(sdkSvc, NET);
        Result<String> r = svc.getPlutusScriptCbor("missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getPlutusScriptCbor_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.script.ScriptService.class);
        when(sdkSvc.getScriptByHash(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusScriptService(sdkSvc, NET);

        assertThatThrownBy(() -> svc.getPlutusScriptCbor("sh1"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
