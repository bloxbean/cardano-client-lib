package com.bloxbean.cardano.client.scalus;

import com.bloxbean.cardano.client.api.model.ProtocolParams;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;

/**
 * Shared test fixtures for Scalus module tests.
 */
final class ScalusTestFixtures {

    private ScalusTestFixtures() {}

    static ProtocolParams buildTestProtocolParams() {
        return ProtocolParams.builder()
                .minFeeA(44)
                .minFeeB(155381)
                .maxBlockSize(90112)
                .maxTxSize(16384)
                .maxBlockHeaderSize(1100)
                .keyDeposit("2000000")
                .poolDeposit("500000000")
                .eMax(18)
                .nOpt(500)
                .a0(BigDecimal.valueOf(0.3))
                .rho(BigDecimal.valueOf(0.003))
                .tau(BigDecimal.valueOf(0.2))
                .protocolMajorVer(10)
                .protocolMinorVer(0)
                .minPoolCost("170000000")
                .coinsPerUtxoSize("4310")
                .priceMem(BigDecimal.valueOf(0.0577))
                .priceStep(BigDecimal.valueOf(0.0000721))
                .maxTxExMem("14000000")
                .maxTxExSteps("10000000000")
                .maxBlockExMem("62000000")
                .maxBlockExSteps("20000000000")
                .maxValSize("5000")
                .collateralPercent(BigDecimal.valueOf(150))
                .maxCollateralInputs(3)
                .costModels(new LinkedHashMap<>())
                .minFeeRefScriptCostPerByte(BigDecimal.valueOf(15))
                .govActionDeposit(BigInteger.valueOf(100000000000L))
                .drepDeposit(BigInteger.valueOf(500000000))
                .drepActivity(20)
                .committeeMinSize(7)
                .committeeMaxTermLength(146)
                .govActionLifetime(6)
                .pvtMotionNoConfidence(BigDecimal.valueOf(0.67))
                .pvtCommitteeNormal(BigDecimal.valueOf(0.67))
                .pvtCommitteeNoConfidence(BigDecimal.valueOf(0.67))
                .pvtHardForkInitiation(BigDecimal.valueOf(0.67))
                .pvtPPSecurityGroup(BigDecimal.valueOf(0.67))
                .dvtMotionNoConfidence(BigDecimal.valueOf(0.67))
                .dvtCommitteeNormal(BigDecimal.valueOf(0.67))
                .dvtCommitteeNoConfidence(BigDecimal.valueOf(0.67))
                .dvtUpdateToConstitution(BigDecimal.valueOf(0.67))
                .dvtHardForkInitiation(BigDecimal.valueOf(0.67))
                .dvtPPNetworkGroup(BigDecimal.valueOf(0.67))
                .dvtPPEconomicGroup(BigDecimal.valueOf(0.67))
                .dvtPPTechnicalGroup(BigDecimal.valueOf(0.67))
                .dvtPPGovGroup(BigDecimal.valueOf(0.67))
                .dvtTreasuryWithdrawal(BigDecimal.valueOf(0.67))
                .build();
    }
}
