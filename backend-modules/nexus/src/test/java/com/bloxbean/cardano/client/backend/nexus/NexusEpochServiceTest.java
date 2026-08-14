package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusEpochServiceTest {

    @Test
    void getLatestEpoch_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        var sdkEpoch = adlabs.nexus.client.backend.api.epoch.model.Epoch.builder()
                .epoch(450)
                .startTime(1000L)
                .endTime(2000L)
                .firstBlockTime(1010L)
                .lastBlockTime(1990L)
                .blockCount(21600)
                .txCount(150000)
                .output("123456789")
                .fees("987654")
                .activeStake("555555555")
                .build();
        when(sdkSvc.getLatestEpoch(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkEpoch));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<EpochContent> r = svc.getLatestEpoch();

        assertThat(r.isSuccessful()).isTrue();
        EpochContent epochContent = r.getValue();
        assertThat(epochContent.getEpoch()).isEqualTo(450);
        assertThat(epochContent.getStartTime()).isEqualTo(1000L);
        assertThat(epochContent.getEndTime()).isEqualTo(2000L);
        assertThat(epochContent.getFirstBlockTime()).isEqualTo(1010L);
        assertThat(epochContent.getLastBlockTime()).isEqualTo(1990L);
        assertThat(epochContent.getBlockCount()).isEqualTo(21600);
        assertThat(epochContent.getTxCount()).isEqualTo(150000);
        assertThat(epochContent.getOutput()).isEqualTo("123456789");
        assertThat(epochContent.getFees()).isEqualTo("987654");
        assertThat(epochContent.getActiveStake()).isEqualTo("555555555");
    }

    @Test
    void getLatestEpoch_nullTimes_defaultToZero() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        var sdkEpoch = adlabs.nexus.client.backend.api.epoch.model.Epoch.builder()
                .epoch(451)
                .build();
        when(sdkSvc.getLatestEpoch(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkEpoch));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        EpochContent epochContent = svc.getLatestEpoch().getValue();

        assertThat(epochContent.getStartTime()).isZero();
        assertThat(epochContent.getEndTime()).isZero();
        assertThat(epochContent.getFirstBlockTime()).isZero();
        assertThat(epochContent.getLastBlockTime()).isZero();
    }

    @Test
    void getLatestEpoch_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        when(sdkSvc.getLatestEpoch(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(503, "down"));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<EpochContent> r = svc.getLatestEpoch();

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(503);
    }

    @Test
    void getLatestEpoch_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        when(sdkSvc.getLatestEpoch(any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(svc::getLatestEpoch)
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getEpoch_unsupported() {
        var svc = new NexusEpochService(
                mock(adlabs.nexus.client.backend.api.epoch.EpochService.class),
                adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.getEpoch(450))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private adlabs.nexus.client.backend.api.epoch.model.ProtocolParams sampleSdkProtocolParams() {
        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        LinkedHashMap<String, Long> plutusV1 = new LinkedHashMap<>();
        plutusV1.put("addInteger-cpu-arguments-intercept", 205665L);
        costModels.put("PlutusV1", plutusV1);

        return adlabs.nexus.client.backend.api.epoch.model.ProtocolParams.builder()
                .minFeeA(44)
                .minFeeB(155381)
                .maxBlockSize(90112)
                .maxTxSize(16384)
                .maxBlockHeaderSize(1100)
                .keyDeposit("2000000")
                .poolDeposit("500000000")
                .eMax(18)
                .nOpt(500)
                .a0(new BigDecimal("0.3"))
                .rho(new BigDecimal("0.003"))
                .tau(new BigDecimal("0.2"))
                .decentralisationParam(BigDecimal.ZERO)
                .protocolMajorVer(9)
                .protocolMinorVer(0)
                .minUtxo("34482")
                .minPoolCost("170000000")
                .costModels(costModels)
                .priceMem(new BigDecimal("0.0577"))
                .priceStep(new BigDecimal("0.0000721"))
                .maxTxExMem("14000000")
                .maxTxExSteps("10000000000")
                .maxBlockExMem("62000000")
                .maxBlockExSteps("20000000000")
                .maxValSize("5000")
                .collateralPercent(new BigDecimal("150"))
                .maxCollateralInputs(3)
                .coinsPerUtxoSize("4310")
                .pvtMotionNoConfidence(new BigDecimal("0.51"))
                .pvtCommitteeNormal(new BigDecimal("0.51"))
                .pvtCommitteeNoConfidence(new BigDecimal("0.51"))
                .pvtHardForkInitiation(new BigDecimal("0.51"))
                .pvtPPSecurityGroup(new BigDecimal("0.51"))
                .dvtMotionNoConfidence(new BigDecimal("0.67"))
                .dvtCommitteeNormal(new BigDecimal("0.67"))
                .dvtCommitteeNoConfidence(new BigDecimal("0.6"))
                .dvtUpdateToConstitution(new BigDecimal("0.75"))
                .dvtHardForkInitiation(new BigDecimal("0.6"))
                .dvtPPNetworkGroup(new BigDecimal("0.67"))
                .dvtPPEconomicGroup(new BigDecimal("0.67"))
                .dvtPPTechnicalGroup(new BigDecimal("0.67"))
                .dvtPPGovGroup(new BigDecimal("0.75"))
                .dvtTreasuryWithdrawal(new BigDecimal("0.67"))
                .committeeMinSize(7)
                .committeeMaxTermLength(146)
                .govActionLifetime(6)
                .govActionDeposit(new BigInteger("100000000000"))
                .drepDeposit(new BigInteger("500000000"))
                .drepActivity(20)
                .minFeeRefScriptCostPerByte(new BigDecimal("15"))
                .build();
    }

    @Test
    void getProtocolParameters_latest_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        var sdkParams = sampleSdkProtocolParams();
        when(sdkSvc.getLatestEpochParameters(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkParams));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<ProtocolParams> r = svc.getProtocolParameters();

        assertThat(r.isSuccessful()).isTrue();
        ProtocolParams pp = r.getValue();

        // Shelley-era spread
        assertThat(pp.getMinFeeA()).isEqualTo(44);
        assertThat(pp.getMinFeeB()).isEqualTo(155381);
        assertThat(pp.getMaxBlockSize()).isEqualTo(90112);
        assertThat(pp.getMaxTxSize()).isEqualTo(16384);
        assertThat(pp.getMaxBlockHeaderSize()).isEqualTo(1100);
        assertThat(pp.getKeyDeposit()).isEqualTo("2000000");
        assertThat(pp.getPoolDeposit()).isEqualTo("500000000");
        assertThat(pp.getEMax()).isEqualTo(18);
        assertThat(pp.getNOpt()).isEqualTo(500);
        assertThat(pp.getA0()).isEqualByComparingTo("0.3");
        assertThat(pp.getRho()).isEqualByComparingTo("0.003");
        assertThat(pp.getTau()).isEqualByComparingTo("0.2");
        assertThat(pp.getProtocolMajorVer()).isEqualTo(9);
        assertThat(pp.getProtocolMinorVer()).isEqualTo(0);
        assertThat(pp.getMinUtxo()).isEqualTo("34482");
        assertThat(pp.getMinPoolCost()).isEqualTo("170000000");

        // Alonzo-era
        assertThat(pp.getPriceMem()).isEqualByComparingTo("0.0577");
        assertThat(pp.getPriceStep()).isEqualByComparingTo("0.0000721");
        assertThat(pp.getMaxTxExMem()).isEqualTo("14000000");
        assertThat(pp.getMaxTxExSteps()).isEqualTo("10000000000");
        assertThat(pp.getMaxBlockExMem()).isEqualTo("62000000");
        assertThat(pp.getMaxBlockExSteps()).isEqualTo("20000000000");
        assertThat(pp.getMaxValSize()).isEqualTo("5000");
        assertThat(pp.getCollateralPercent()).isEqualByComparingTo("150");
        assertThat(pp.getMaxCollateralInputs()).isEqualTo(3);
        assertThat(pp.getCoinsPerUtxoSize()).isEqualTo("4310");
        assertThat(pp.getCostModels()).containsKey("PlutusV1");
        assertThat(pp.getCostModels().get("PlutusV1")).containsEntry("addInteger-cpu-arguments-intercept", 205665L);

        // Conway-era
        assertThat(pp.getPvtMotionNoConfidence()).isEqualByComparingTo("0.51");
        assertThat(pp.getDvtCommitteeNoConfidence()).isEqualByComparingTo("0.6");
        assertThat(pp.getCommitteeMinSize()).isEqualTo(7);
        assertThat(pp.getCommitteeMaxTermLength()).isEqualTo(146);
        assertThat(pp.getGovActionLifetime()).isEqualTo(6);
        assertThat(pp.getGovActionDeposit()).isEqualByComparingTo(new BigInteger("100000000000"));
        assertThat(pp.getDrepDeposit()).isEqualByComparingTo(new BigInteger("500000000"));
        assertThat(pp.getDrepActivity()).isEqualTo(20);
        assertThat(pp.getMinFeeRefScriptCostPerByte()).isEqualByComparingTo("15");

        // Fields the Nexus SDK doesn't carry must stay null, not be fabricated.
        assertThat(pp.getCostModelsRaw()).isNull();
        assertThat(pp.getCoinsPerUtxoWord()).isNull();
        assertThat(pp.getExtraEntropy()).isNull();
        assertThat(pp.getNonce()).isNull();
    }

    @Test
    void getProtocolParameters_byEpoch_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        var sdkParams = sampleSdkProtocolParams();
        when(sdkSvc.getEpochParams(eq(adlabs.nexus.client.util.Network.MAINNET), eq(450))).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkParams));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<ProtocolParams> r = svc.getProtocolParameters(450);

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getMinFeeA()).isEqualTo(44);
        assertThat(r.getValue().getDrepDeposit()).isEqualByComparingTo(new BigInteger("500000000"));
    }

    @Test
    void getProtocolParameters_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        when(sdkSvc.getLatestEpochParameters(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(500, "boom"));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<ProtocolParams> r = svc.getProtocolParameters();

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(500);
    }

    @Test
    void getProtocolParameters_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.epoch.EpochService.class);
        when(sdkSvc.getLatestEpochParameters(any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusEpochService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(svc::getProtocolParameters)
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }
}
