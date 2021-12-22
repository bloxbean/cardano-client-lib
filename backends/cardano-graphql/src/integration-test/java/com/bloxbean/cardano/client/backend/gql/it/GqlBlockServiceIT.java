package com.bloxbean.cardano.client.backend.gql.it;

import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.GqlBlockService;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GqlBlockServiceIT extends GqlBaseTest {
    BlockService blockService;

    @BeforeEach
    public void setup() {
        blockService = backendService.getBlockService();
    }

    @Test
    public void testGetLatestBlock() throws ApiException {
        Result<Block> result = blockService.getLastestBlock();

        System.out.println(result.getResponse());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertNotNull(result.getValue());
        assertTrue(result.getValue().getHash().length() > 10);
    }

    @Test
    public void testGetBlockByHash() throws ApiException {
        String hash = "2c6770fe6005af279c0eda6ba0464cb3ee7d974da6ff408adc8cf839732431dc";
        Result<Block> result = blockService.getBlockByHash(hash);

        Block block = result.getValue();
        System.out.println(result.getResponse());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertNotNull(result.getValue());
        assertTrue(result.getValue().getHash().length() > 10);
        assertEquals(31280906, block.getSlot());
        assertEquals("302f5b7e8814ebd67b898abe6ec2963ae09432e48dff31a1e9684a70", block.getSlotLeader());
        assertEquals(3, block.getSize());
    }

    @Test
    public void testGetBlockByNumber() throws ApiException {
        Integer number = 2734278;
        Result<Block> result = blockService.getBlockByNumber(BigInteger.valueOf(number));

        Block block = result.getValue();
        System.out.println(result.getResponse());
        System.out.println(JsonUtil.getPrettyJson(result.getValue()));
        assertNotNull(result.getValue());
        assertTrue(result.getValue().getHash().length() > 10);
        assertEquals(31280906, block.getSlot());
        assertEquals("302f5b7e8814ebd67b898abe6ec2963ae09432e48dff31a1e9684a70", block.getSlotLeader());
        assertEquals(3, block.getSize());
    }
}
