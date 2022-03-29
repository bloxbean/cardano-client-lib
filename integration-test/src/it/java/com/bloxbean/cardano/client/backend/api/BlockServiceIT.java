package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BlockServiceIT extends BaseITTest {

    BlockService service;

    @BeforeEach
    public void setup() {
        service = getBackendService().getBlockService();
    }

    @Test
    public void testGetLatestBlock() throws ApiException {
        Result<Block> block = service.getLatestBlock();

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }

    @Test
    public void testGetBlockByHash() throws ApiException {
        Result<Block> block = service.getBlockByHash("adc25b348d96f18e7d075f7b284d8934fafa4cd4bb2383751a1ab2161ee9ecde");

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }

    @Test
    public void testGetBlockByNumber() throws ApiException {
        Result<Block> block = service.getBlockByNumber(new BigInteger("2590132"));

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }
}
