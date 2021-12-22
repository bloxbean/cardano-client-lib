package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GqlNetworkInfoServiceIT extends GqlBaseTest {
    NetworkInfoService networkInfoService;

    @BeforeEach
    public void setup() {
        networkInfoService = backendService.getNetworkInfoService();
    }

    @Test
    public void getNetworkInfo() throws ApiException {
        Result<Genesis> gensisResult = networkInfoService.getNetworkInfo();

        Genesis genesis = gensisResult.getValue();
        assertNotNull(genesis);
        assertEquals(genesis.getActiveSlotsCoefficient().doubleValue(), 0.05);
        assertEquals(genesis.getEpochLength(), 432000);
        assertEquals(genesis.getMaxKesEvolutions(), 62);
        assertEquals(genesis.getMaxLovelaceSupply(), "45000000000000000");
        assertEquals(genesis.getNetworkMagic(), 1097911063);
        assertEquals(genesis.getSecurityParam(), 2160);
        assertEquals(genesis.getSlotLength(), 1);
        assertEquals(genesis.getSlotsPerKesPeriod(), 129600);
    }
}
