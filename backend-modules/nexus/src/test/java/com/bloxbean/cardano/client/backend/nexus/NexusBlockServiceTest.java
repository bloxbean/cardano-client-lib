package com.bloxbean.cardano.client.backend.nexus;

import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.Block;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NexusBlockServiceTest {

    @Test
    void getLatestBlock_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.block.BlockService.class);
        var sdkBlock = adlabs.nexus.client.backend.api.block.model.Block.builder()
                .hash("h1").height(100L).slot(5L).epoch(3).build();
        when(sdkSvc.getLatestBlock(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkBlock));

        var svc = new NexusBlockService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Block> r = svc.getLatestBlock();

        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getValue().getHash()).isEqualTo("h1");
        assertThat(r.getValue().getHeight()).isEqualTo(100L);
        assertThat(r.getValue().getSlot()).isEqualTo(5L);
        assertThat(r.getValue().getEpoch()).isEqualTo(3);
    }

    @Test
    void getLatestBlock_error_propagates() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.block.BlockService.class);
        when(sdkSvc.getLatestBlock(any())).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.error(503, "down"));

        var svc = new NexusBlockService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Block> r = svc.getLatestBlock();

        assertThat(r.isSuccessful()).isFalse();
        assertThat(r.code()).isEqualTo(503);
    }

    @Test
    void getLatestBlock_sdkApiException_rethrownAsBloxbean() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.block.BlockService.class);
        when(sdkSvc.getLatestBlock(any()))
                .thenThrow(new adlabs.nexus.client.backend.api.base.exception.ApiException("boom"));

        var svc = new NexusBlockService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(svc::getLatestBlock)
                .isInstanceOf(com.bloxbean.cardano.client.api.exception.ApiException.class)
                .hasMessageContaining("boom");
    }

    @Test
    void getBlockByHash_maps() throws Exception {
        var sdkSvc = mock(adlabs.nexus.client.backend.api.block.BlockService.class);
        var sdkBlock = adlabs.nexus.client.backend.api.block.model.Block.builder()
                .hash("h2").height(200L).slot(9L).epoch(4)
                .epochSlot(11L).slotLeader("pool1").size(500).txCount(2)
                .output("1000").fees("10").blockVrf("vrf").previousBlock("prev")
                .nextBlock("next").confirmations(6).build();
        when(sdkSvc.getBlock(eq(adlabs.nexus.client.util.Network.MAINNET), eq("h2"))).thenReturn(
                adlabs.nexus.client.backend.api.base.Result.success(200, sdkBlock));

        var svc = new NexusBlockService(sdkSvc, adlabs.nexus.client.util.Network.MAINNET);
        Result<Block> r = svc.getBlockByHash("h2");

        assertThat(r.isSuccessful()).isTrue();
        Block block = r.getValue();
        assertThat(block.getHash()).isEqualTo("h2");
        assertThat(block.getHeight()).isEqualTo(200L);
        assertThat(block.getSlot()).isEqualTo(9L);
        assertThat(block.getEpoch()).isEqualTo(4);
        assertThat(block.getEpochSlot()).isEqualTo(11);
        assertThat(block.getSlotLeader()).isEqualTo("pool1");
        assertThat(block.getSize()).isEqualTo(500);
        assertThat(block.getTxCount()).isEqualTo(2);
        assertThat(block.getOutput()).isEqualTo("1000");
        assertThat(block.getFees()).isEqualTo("10");
        assertThat(block.getBlockVrf()).isEqualTo("vrf");
        assertThat(block.getPreviousBlock()).isEqualTo("prev");
        assertThat(block.getNextBlock()).isEqualTo("next");
        assertThat(block.getConfirmations()).isEqualTo(6);
    }

    @Test
    void getBlockByNumber_unsupported() {
        var svc = new NexusBlockService(
                mock(adlabs.nexus.client.backend.api.block.BlockService.class),
                adlabs.nexus.client.util.Network.MAINNET);

        assertThatThrownBy(() -> svc.getBlockByNumber(BigInteger.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
