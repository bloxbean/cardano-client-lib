package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.model.Block;

import java.math.BigInteger;

/**
 * Nexus Block Service
 */
public class NexusBlockService implements BlockService {

    private final adlabs.nexus.client.backend.api.block.BlockService blockService;
    private final Network network;

    public NexusBlockService(adlabs.nexus.client.backend.api.block.BlockService blockService, Network network) {
        this.blockService = blockService;
        this.network = network;
    }

    @Override
    public Result<Block> getLatestBlock() throws ApiException {
        try {
            return NexusResultMapper.map(blockService.getLatestBlock(network), this::toBlock);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<Block> getBlockByHash(String blockHash) throws ApiException {
        try {
            return NexusResultMapper.map(blockService.getBlock(network, blockHash), this::toBlock);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException {
        throw new UnsupportedOperationException("getBlockByNumber not supported by Nexus");
    }

    private Block toBlock(adlabs.nexus.client.backend.api.block.model.Block bl) {
        Block block = new Block();
        block.setTime(bl.getTime() == null ? 0L : bl.getTime());
        block.setHeight(bl.getHeight() == null ? 0L : bl.getHeight());
        block.setHash(bl.getHash());
        block.setSlot(bl.getSlot() == null ? 0L : bl.getSlot());
        block.setEpoch(bl.getEpoch());
        block.setEpochSlot(bl.getEpochSlot() == null ? null : bl.getEpochSlot().intValue());
        block.setSlotLeader(bl.getSlotLeader());
        block.setSize(bl.getSize());
        block.setTxCount(bl.getTxCount());
        block.setOutput(bl.getOutput());
        block.setFees(bl.getFees());
        block.setBlockVrf(bl.getBlockVrf());
        block.setPreviousBlock(bl.getPreviousBlock());
        block.setNextBlock(bl.getNextBlock());
        block.setConfirmations(bl.getConfirmations());
        return block;
    }
}
