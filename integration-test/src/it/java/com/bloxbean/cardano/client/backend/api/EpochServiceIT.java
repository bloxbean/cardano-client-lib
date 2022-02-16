package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.factory.BackendFactory;
import com.bloxbean.cardano.client.backend.impl.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class EpochServiceIT extends BaseITTest {

    BackendService backendService;
    EpochService epochService;

    @BeforeEach
    public void setup() {
        backendService = BackendFactory.getBlockfrostBackendService(Constants.BLOCKFROST_TESTNET_URL, bfProjectId);
        epochService = backendService.getEpochService();
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
        Result<EpochContent> result = epochService.getEpoch(130);

        EpochContent epochContent = result.getValue();

        System.out.println(result);
        System.out.println(JsonUtil.getPrettyJson(epochContent));

        assertThat(result.isSuccessful(), is(true));
        assertThat(epochContent.getEpoch(), is(130));
        assertThat(epochContent.getBlockCount(), greaterThan(0));
    }

    @Test
    public void testGetProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters(130);

        System.out.println(result);

        ProtocolParams protocolParams = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(protocolParams));

        assertThat(protocolParams, notNullValue());
        assertThat(protocolParams.getMinUtxo(), is("1000000"));
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
        assertThat(protocolParams.getCoinsPerUtxoWord(), is("34482"));
        assertThat(protocolParams.getEMax(), notNullValue());
        assertThat(protocolParams.getNOpt(), notNullValue());
    }
}
