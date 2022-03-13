package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KoiosBackendServiceIT {

    @Test
    public void testCreateTestnetBackendServiceWithUrl() throws ApiException {
        BackendService backendService = new KoiosBackendService(Constants.KOIOS_TESTNET_URL);
        NetworkInfoService networkInfoService = backendService.getNetworkInfoService();

        getNetworkInfoAndCompare(networkInfoService);
    }

    public void getNetworkInfoAndCompare(NetworkInfoService networkInfoService) throws ApiException {
        Result<Genesis> gensisResult = networkInfoService.getNetworkInfo();

        Genesis genesis = gensisResult.getValue();
        assertNotNull(genesis);
        assertEquals(0.05,genesis.getActiveSlotsCoefficient().doubleValue());
        assertEquals(432000,genesis.getEpochLength());
        assertEquals(62, genesis.getMaxKesEvolutions());
        assertEquals("45000000000000000", genesis.getMaxLovelaceSupply());
        assertEquals(1097911063, genesis.getNetworkMagic());
        assertEquals(2160, genesis.getSecurityParam());
        assertEquals(1, genesis.getSlotLength());
        assertEquals(129600, genesis.getSlotsPerKesPeriod());
    }
}
