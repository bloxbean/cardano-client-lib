package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OgmiosEpochServiceIT extends OgmiosBaseTest {

    EpochService epochService;

    @BeforeEach
    public void setup() {
        epochService =  ogmiosBackendService.getEpochService();
    }

    @Test
    public void testGetLatestProtocolParameters() throws ApiException {
        Result<ProtocolParams> result = epochService.getProtocolParameters();
        ProtocolParams protocolParams = result.getValue();

        assertThat(protocolParams).isNotNull();
        assertThat(protocolParams.getPoolDeposit()).isEqualTo("500000000");
        assertEquals(protocolParams.getCollateralPercent().intValue(), 150);
        assertThat(protocolParams.getEMax()).isNotNull();
        assertThat(protocolParams.getNOpt()).isNotNull();
    }
}
