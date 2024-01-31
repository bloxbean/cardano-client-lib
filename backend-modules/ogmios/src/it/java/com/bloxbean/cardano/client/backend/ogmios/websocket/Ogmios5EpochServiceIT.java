package com.bloxbean.cardano.client.backend.ogmios.websocket;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.ogmios.OgmiosBaseTest;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Ogmios5EpochServiceIT extends Ogmios5BaseTest {

    EpochService epochService;

    @BeforeEach
    public void setup() {
        epochService =  ogmios5BackendService.getEpochService();
    }

    @Test
    public void testGetLatestProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters();

        ProtocolParams protocolParams = result.getValue();
        System.out.println(JsonUtil.getPrettyJson(protocolParams));

        assertThat(protocolParams).isNotNull();
        assertThat(protocolParams.getPoolDeposit()).isEqualTo("500000000");
        assertThat(protocolParams.getCoinsPerUtxoSize()).isEqualTo("4310");
        assertThat(protocolParams.getEMax()).isNotNull();
        assertThat(protocolParams.getNOpt()).isNotNull();
    }
}
