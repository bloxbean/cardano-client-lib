package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.PoolInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PoolServiceIT extends BaseITTest {

    @Test
    public void testGetPoolInfo() throws ApiException {
        String poolId = "pool13la5erny3srx9u4fz9tujtl2490350f89r4w4qjhk0vdjmuv78v";
        PoolService poolService = getBackendService().getPoolService();
        Result<PoolInfo> result = poolService.getPoolInfo(poolId);

        assertTrue(result.isSuccessful());
        assertEquals(200, result.code());
        assertNotNull(result.getValue());
    }
}
