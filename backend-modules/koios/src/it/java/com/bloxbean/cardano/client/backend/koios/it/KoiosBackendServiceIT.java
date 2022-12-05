package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.koios.Constants;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.api.model.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KoiosBackendServiceIT {

    @Test
    void testCreatePreprodBackendServiceWithUrl() throws ApiException {
        BackendService backendService = new KoiosBackendService(Constants.KOIOS_PREPROD_URL);
        NetworkInfoService networkInfoService = backendService.getNetworkInfoService();

        getNetworkInfoAndCompare(networkInfoService);
    }

    public void getNetworkInfoAndCompare(NetworkInfoService networkInfoService) throws ApiException {
        Result<Genesis> genesisResult = networkInfoService.getNetworkInfo();

        Genesis genesis = genesisResult.getValue();
        assertNotNull(genesis);
        assertEquals(0.05,genesis.getActiveSlotsCoefficient().doubleValue());
        assertEquals(432000,genesis.getEpochLength());
        assertEquals(62, genesis.getMaxKesEvolutions());
        assertEquals("45000000000000000", genesis.getMaxLovelaceSupply());
        assertEquals(1, genesis.getNetworkMagic());
        assertEquals(2160, genesis.getSecurityParam());
        assertEquals(1, genesis.getSlotLength());
        assertEquals(129600, genesis.getSlotsPerKesPeriod());
    }
}
