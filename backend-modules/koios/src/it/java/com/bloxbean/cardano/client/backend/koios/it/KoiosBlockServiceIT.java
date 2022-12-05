package com.bloxbean.cardano.client.backend.koios.it;

import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KoiosBlockServiceIT  extends KoiosBaseTest {

    private BlockService blockService;

    @BeforeEach
    public void setup() {
        blockService = backendService.getBlockService();
    }

    @Test
    void testGetLatestBlock() throws ApiException {
        Result<Block> block = blockService.getLatestBlock();

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }

    @Test
    void testGetBlockByHash() throws ApiException {
        Result<Block> block = blockService.getBlockByHash("065b9f0a52b3d3897160a065a7fe2bcb64b2bf635937294ade457de6a7bfd2a4");

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }

    @Test
    void testGetBlockByNumber() throws ApiException {
        Result<Block> block = blockService.getBlockByNumber(new BigInteger("300112"));

        assertNotNull(block.getValue());
        System.out.println(JsonUtil.getPrettyJson(block.getValue()));
    }
}
