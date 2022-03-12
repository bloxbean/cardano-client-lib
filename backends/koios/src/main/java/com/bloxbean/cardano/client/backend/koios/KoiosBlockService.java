package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import rest.koios.client.backend.api.block.model.BlockInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.filters.Filter;
import rest.koios.client.backend.factory.options.filters.FilterType;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class KoiosBlockService implements BlockService {

    private final rest.koios.client.backend.api.block.BlockService blockService;
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public KoiosBlockService(rest.koios.client.backend.api.block.BlockService blockService) {
        this.blockService = blockService;
    }

    @Override
    public Result<Block> getLastestBlock() throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.block.model.Block>>
                    blockList = blockService.getBlockList(Options.builder().option(Limit.of(1)).build());
            if (!blockList.isSuccessful()) {
                return Result.error(blockList.getResponse()).code(blockList.getCode());
            }
            return getBlockByHash(blockList.getValue().get(0).getHash());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<Block> getBlockByHash(String blockHash) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<BlockInfo> blockInfo = blockService.getBlockInformation(blockHash);
            if (!blockInfo.isSuccessful()) {
                return Result.error(blockInfo.getResponse()).code(blockInfo.getCode());
            }
            return convertToBlock(blockInfo.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        } catch (ParseException e) {
            return Result.error("Failed to Parse System Start Date").code(500);
        }
    }

    private Result<Block> convertToBlock(BlockInfo blockInfo) throws ParseException {
        Block block = new Block();
        block.setTime(simpleDateFormat.parse(blockInfo.getBlockTime()).getTime());
        block.setHeight(blockInfo.getBlockHeight());
        block.setHash(blockInfo.getHash());
        block.setSlot(blockInfo.getAbsSlot());
        block.setEpoch(blockInfo.getEpochNo());
        block.setEpochSlot(blockInfo.getEpochSlot());
        block.setSlotLeader(blockInfo.getPool());
        block.setSize(Math.toIntExact(blockInfo.getBlockSize()));
        block.setTxCount(blockInfo.getTxCount());
        block.setOutput(blockInfo.getTotalOutput());
        block.setFees(blockInfo.getTotalFees());
        block.setBlockVrf(blockInfo.getVrfKey());
        block.setPreviousBlock(blockInfo.getParentHash());
        block.setNextBlock(blockInfo.getChildHash());
        block.setConfirmations(Math.toIntExact(blockInfo.getNumConfirmations()));
        return Result.success("OK").withValue(block).code(200);
    }

    @Override
    public Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.block.model.Block>>
                    blockList = blockService.getBlockList(Options.builder().option(Filter.of("block_height", FilterType.EQ, blockNumber.toString())).build());
            if (!blockList.isSuccessful()) {
                return Result.error(blockList.getResponse()).code(blockList.getCode());
            }
            return getBlockByHash(blockList.getValue().get(0).getHash());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }
}
