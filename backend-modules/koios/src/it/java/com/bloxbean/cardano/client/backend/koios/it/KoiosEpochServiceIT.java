package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class KoiosEpochServiceIT extends KoiosBaseTest {

    private EpochService epochService;

    @BeforeEach
    public void setup() {
        epochService = backendService.getEpochService();
    }

    @Test
    void testGetLatestEpoch() throws ApiException {
        Result<EpochContent> result = epochService.getLatestEpoch();

        EpochContent epochContent = result.getValue();

        System.out.println(result);
        System.out.println(JsonUtil.getPrettyJson(epochContent));

        assertThat(result.isSuccessful(), is(true));
        assertThat(epochContent.getEpoch(), not(0));
        assertThat(epochContent.getEpoch(), notNullValue());
    }

    @Test
    void testGetLatestEpochByNumber() throws ApiException {
        Result<EpochContent> result = epochService.getEpoch(37);

        EpochContent epochContent = result.getValue();

        System.out.println(result);
        System.out.println(JsonUtil.getPrettyJson(epochContent));

        assertThat(result.isSuccessful(), is(true));
        assertThat(epochContent.getEpoch(), is(37));
        assertThat(epochContent.getBlockCount(), greaterThan(0));
    }

    @Test
    void testGetProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters(37);

        System.out.println(result);

        ProtocolParams protocolParams = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(protocolParams));

        assertThat(protocolParams, notNullValue());
        assertThat(protocolParams.getMinUtxo(), is("0"));
        assertThat(protocolParams.getPoolDeposit(), is("500000000"));
    }

    @Test
    void testGetLatestProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters();

        System.out.println(result);

        ProtocolParams protocolParams = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(protocolParams));

        assertThat(protocolParams, notNullValue());
        assertThat(protocolParams, notNullValue());
        assertThat(protocolParams.getPoolDeposit(), is("500000000"));
        assertThat(protocolParams.getCoinsPerUtxoSize(), is("4310"));
        assertThat(protocolParams.getEMax(), notNullValue());
        assertThat(protocolParams.getNOpt(), notNullValue());

        assertThat(protocolParams.getPvtMotionNoConfidence(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getPvtCommitteeNormal(),greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getPvtCommitteeNormal(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getPvtHardForkInitiation(), greaterThan(new BigDecimal(0)));

        assertThat(protocolParams.getDvtMotionNoConfidence(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getDvtCommitteeNormal(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getDvtCommitteeNoConfidence(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getDvtUpdateToConstitution(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getDvtHardForkInitiation(), greaterThan(new BigDecimal(0)));
//        assertThat(protocolParams.getDvtPPNetworkGroup(), greaterThan(new BigDecimal(0)));
//        assertThat(protocolParams.getDvtPPEconomicGroup(), greaterThan(new BigDecimal(0)));
//        assertThat(protocolParams.getDvtPPTechnicalGroup(), greaterThan(new BigDecimal(0)));
//        assertThat(protocolParams.getDvtPPGovGroup(), greaterThan(new BigDecimal(0)));
        assertThat(protocolParams.getDvtTreasuryWithdrawal(), greaterThan(new BigDecimal(0)));

        assertThat(protocolParams.getCommitteeMinSize(), notNullValue());
        assertThat(protocolParams.getCommitteeMaxTermLength(), greaterThan(0));
        assertThat(protocolParams.getGovActionLifetime(), greaterThan(0));
        assertThat(protocolParams.getGovActionDeposit(), greaterThan(BigInteger.ZERO));
        assertThat(protocolParams.getDrepDeposit(), greaterThan(BigInteger.ZERO));
        assertThat(protocolParams.getDrepActivity(), greaterThan(0));
        assertThat(protocolParams.getMinFeeRefScriptCostPerByte(), greaterThan(BigDecimal.ZERO));

        assertThat(protocolParams.getCostModels().get("PlutusV1").size(), is(166));
        assertThat(protocolParams.getCostModels().get("PlutusV2").size(), is(175));
        assertThat(protocolParams.getCostModels().get("PlutusV3").size(), greaterThan(251));
    }
}
