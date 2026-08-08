package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.pool.model.Pool;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.PoolInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusPoolServiceTest {

    private static final adlabs.nexus.client.util.Network NET = adlabs.nexus.client.util.Network.MAINNET;

    @Test
    void getPoolInfo_maps() throws Exception {
        var sdkPoolSvc = mock(adlabs.nexus.client.backend.api.pool.PoolService.class);
        Pool pool = Pool.builder()
                .poolIdBech32("pool1abc")
                .poolIdHex("deadbeef")
                .vrfKeyHash("vrf1")
                .blocksMinted(1234L)
                .liveStake(new BigInteger("500000000"))
                .liveSaturationPct(new BigDecimal("0.75"))
                .liveDelegators(42L)
                .activeStake(new BigInteger("400000000"))
                .sigma(new BigDecimal("0.001"))
                .pledgeDeclared(new BigInteger("1000000"))
                .livePledge(new BigInteger("1000001"))
                .marginPct(new BigDecimal("0.02"))
                .fixedCost(new BigInteger("340000000"))
                .rewardAddr("stake1xyz")
                .owners(List.of("stake1owner1", "stake1owner2"))
                .build();
        when(sdkPoolSvc.getPool(eq(NET), eq("pool1abc")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, pool));

        var svc = new NexusPoolService(sdkPoolSvc, NET);
        Result<PoolInfo> r = svc.getPoolInfo("pool1abc");

        assertThat(r.isSuccessful()).isTrue();
        PoolInfo pi = r.getValue();
        assertThat(pi.getPoolId()).isEqualTo("pool1abc");
        assertThat(pi.getHex()).isEqualTo("deadbeef");
        assertThat(pi.getVrfKey()).isEqualTo("vrf1");
        assertThat(pi.getBlocksMinted()).isEqualTo(1234);
        assertThat(pi.getLiveStake()).isEqualTo("500000000");
        assertThat(pi.getLiveSaturation()).isEqualTo(0.75);
        assertThat(pi.getLiveDelegators()).isEqualTo(42);
        assertThat(pi.getActiveStake()).isEqualTo("400000000");
        assertThat(pi.getActiveSize()).isEqualTo(0.001);
        assertThat(pi.getDeclaredPledge()).isEqualTo("1000000");
        assertThat(pi.getLivePledge()).isEqualTo("1000001");
        assertThat(pi.getMarginCost()).isEqualTo(0.02);
        assertThat(pi.getFixedCost()).isEqualTo("340000000");
        assertThat(pi.getRewardAccount()).isEqualTo("stake1xyz");
        assertThat(pi.getOwners()).containsExactly("stake1owner1", "stake1owner2");
    }

    @Test
    void getPoolInfo_nullNumerics_leftNull() throws Exception {
        var sdkPoolSvc = mock(adlabs.nexus.client.backend.api.pool.PoolService.class);
        Pool pool = Pool.builder()
                .poolIdBech32("pool1abc")
                .build();
        when(sdkPoolSvc.getPool(eq(NET), eq("pool1abc")))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.success(200, pool));

        var svc = new NexusPoolService(sdkPoolSvc, NET);
        Result<PoolInfo> r = svc.getPoolInfo("pool1abc");

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getBlocksMinted()).isNull();
        assertThat(r.getValue().getLiveStake()).isNull();
        assertThat(r.getValue().getLiveSaturation()).isNull();
    }

    @Test
    void getPoolInfo_error_propagates() throws Exception {
        var sdkPoolSvc = mock(adlabs.nexus.client.backend.api.pool.PoolService.class);
        when(sdkPoolSvc.getPool(any(), any()))
                .thenReturn(adlabs.nexus.client.backend.api.base.Result.error(404, "not found"));

        var svc = new NexusPoolService(sdkPoolSvc, NET);
        Result<PoolInfo> r = svc.getPoolInfo("pool1missing");

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(404);
    }

    @Test
    void getPoolInfo_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkPoolSvc = mock(adlabs.nexus.client.backend.api.pool.PoolService.class);
        when(sdkPoolSvc.getPool(any(), any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusPoolService(sdkPoolSvc, NET);

        assertThatThrownBy(() -> svc.getPoolInfo("pool1abc"))
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
