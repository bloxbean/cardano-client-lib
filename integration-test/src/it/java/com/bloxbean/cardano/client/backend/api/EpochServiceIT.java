package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class EpochServiceIT extends BaseITTest {

    EpochService epochService;

    @BeforeEach
    public void setup() {
        epochService = getBackendService().getEpochService();
    }

    @Test
    public void testGetLatestEpoch() throws ApiException {
        Result<EpochContent> result = epochService.getLatestEpoch();

        EpochContent epochContent = result.getValue();

        System.out.println(result);
        System.out.println(JsonUtil.getPrettyJson(epochContent));

        assertThat(result.isSuccessful(), is(true));
        assertThat(epochContent.getEpoch(), not(0));
        assertThat(epochContent.getEpoch(), notNullValue());
    }

    @Test
    public void testGetLatestEpochByNumber() throws ApiException {
        Result<EpochContent> result = epochService.getEpoch(32);

        EpochContent epochContent = result.getValue();

        System.out.println(result);
        System.out.println(JsonUtil.getPrettyJson(epochContent));

        assertThat(result.isSuccessful(), is(true));
        assertThat(epochContent.getEpoch(), is(32));
        assertThat(epochContent.getBlockCount(), greaterThan(0));
    }

    @Test
    public void testGetProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters(8);

        System.out.println(result);

        ProtocolParams protocolParams = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(protocolParams));

        assertThat(protocolParams, notNullValue());
        assertThat(protocolParams.getMinUtxo(), is("34482"));
        assertThat(protocolParams.getPoolDeposit(), is("500000000"));
    }

    @Test
    public void testGetLatestProtocolParameters() throws ApiException {
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
    }
}
