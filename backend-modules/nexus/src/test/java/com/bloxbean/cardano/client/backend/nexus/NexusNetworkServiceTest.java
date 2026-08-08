package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Genesis;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusNetworkServiceTest {

    @Test
    void getNetworkInfo_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.network.NetworkService.class);
        var sdkInfo = adlabs.nexus.client.backend.api.network.model.NetworkInfo.builder()
                .networkMagic(764824073)
                .systemStart(1506203091L)
                .epochLength(432000)
                .slotLength(1.0)
                .slotsPerKesPeriod(129600)
                .maxKesEvolutions(62)
                .securityParam(2160)
                .activeSlotsCoefficient(new BigDecimal("0.05"))
                .updateQuorum(5)
                .maxLovelaceSupply("45000000000000000")
                .build();
        when(sdkSvc.getNetworkInfo(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkInfo));

        var svc = new NexusNetworkService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Genesis> r = svc.getNetworkInfo();

        assertThat(r.isSuccessful()).isTrue();
        Genesis genesis = r.getValue();
        assertThat(genesis.getNetworkMagic()).isEqualTo(764824073);
        assertThat(genesis.getSystemStart()).isEqualTo(1506203091);
        assertThat(genesis.getEpochLength()).isEqualTo(432000);
        assertThat(genesis.getSlotLength()).isEqualTo(1);
        assertThat(genesis.getSlotsPerKesPeriod()).isEqualTo(129600);
        assertThat(genesis.getMaxKesEvolutions()).isEqualTo(62);
        assertThat(genesis.getSecurityParam()).isEqualTo(2160);
        assertThat(genesis.getActiveSlotsCoefficient()).isEqualByComparingTo("0.05");
        assertThat(genesis.getUpdateQuorum()).isEqualTo(5);
        assertThat(genesis.getMaxLovelaceSupply()).isEqualTo("45000000000000000");
    }

    @Test
    void getNetworkInfo_nullSystemStartAndSlotLength_noNpe() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.network.NetworkService.class);
        var sdkInfo = adlabs.nexus.client.backend.api.network.model.NetworkInfo.builder()
                .networkMagic(764824073)
                .build();
        when(sdkSvc.getNetworkInfo(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkInfo));

        var svc = new NexusNetworkService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Genesis> r = svc.getNetworkInfo();

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getSystemStart()).isNull();
        assertThat(r.getValue().getSlotLength()).isNull();
    }

    @Test
    void getNetworkInfo_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.network.NetworkService.class);
        when(sdkSvc.getNetworkInfo(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(503, "down"));

        var svc = new NexusNetworkService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Genesis> r = svc.getNetworkInfo();

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(503);
    }

    @Test
    void getNetworkInfo_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.network.NetworkService.class);
        when(sdkSvc.getNetworkInfo(any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusNetworkService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(svc::getNetworkInfo)
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
