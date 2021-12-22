package com.bloxbean.cardano.client.backend.gql;

import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.gql.util.DateUtil;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.gql.BlockByHashQuery;
import com.bloxbean.cardano.gql.BlockByNumberQuery;
import com.bloxbean.cardano.gql.RecentBlockQuery;
import com.bloxbean.cardano.gql.fragment.BlockFragment;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.backend.gql.util.ConversionUtil.intValue;

public class GqlBlockService extends BaseGqlService implements BlockService {

    public GqlBlockService(String gqlUrl) {
        super(gqlUrl);
    }

    public GqlBlockService(String gqlUrl, Map<String, String > headers) {
        super(gqlUrl, headers);
    }

    public GqlBlockService(String gqlUrl, OkHttpClient okHttpClient) {
        super(gqlUrl, okHttpClient);
    }

    @Override
    public Result<Block> getLastestBlock() throws ApiException {
        RecentBlockQuery blockQuery = new RecentBlockQuery();
        RecentBlockQuery.Data data = execute(blockQuery);
        if(data == null)
            return Result.error("Unable to get the latest block");

        List<RecentBlockQuery.Block> blocks = data.blocks();
        if(blocks == null || blocks.size() == 0)
            return Result.error("Unable to get the latest block");

        BlockFragment gqlBlock = blocks.get(0).fragments().blockFragment();

        Block block = gqlBlockToBlock(gqlBlock);
        return processSuccessResult(block);
    }

    @Override
    public Result<Block> getBlockByHash(String blockHash) throws ApiException {
        BlockByHashQuery query = new BlockByHashQuery(blockHash);
        BlockByHashQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to get the block for hash: " + blockHash);

        List<BlockByHashQuery.Block> blocks = data.blocks();
        if(blocks == null || blocks.size() == 0)
            return Result.error("Unable to get the block for hash: " + blockHash);

        BlockFragment gqlBlock = blocks.get(0).fragments().blockFragment();

        Block block = gqlBlockToBlock(gqlBlock);
        return processSuccessResult(block);
    }

    @Override
    public Result<Block> getBlockByNumber(BigInteger blockNumber) throws ApiException {
        BlockByNumberQuery query = new BlockByNumberQuery(blockNumber.intValue());
        BlockByNumberQuery.Data data = execute(query);
        if(data == null)
            return Result.error("Unable to get the block for number: " + blockNumber);

        List<BlockByNumberQuery.Block> blocks = data.blocks();
        if(blocks == null || blocks.size() == 0)
            return Result.error("Unable to get the block for number: " + blockNumber);

        BlockFragment gqlBlock = blocks.get(0).fragments().blockFragment();

        Block block = gqlBlockToBlock(gqlBlock);
        return processSuccessResult(block);
    }

    @NotNull
    private Block gqlBlockToBlock(BlockFragment gqlBlock) {
        Block block = new Block();
        block.setTime(DateUtil.convertDateTimeToLong(String.valueOf(gqlBlock.forgedAt())));
        block.setHeight(gqlBlock.number());
        block.setHash(String.valueOf(gqlBlock.hash()));
        block.setSlot(gqlBlock.slotNo());
        block.setEpoch(gqlBlock.epoch().number());
        block.setEpochSlot(gqlBlock.slotInEpoch());
        block.setSlotLeader(String.valueOf(gqlBlock.slotLeader().hash()));
        block.setSize(((BigDecimal) gqlBlock.size()).intValue());

        block.setTxCount(intValue(gqlBlock.transactionsCount()));
        //TODO
//        block.setOutput(gqlBlock.);
        block.setFees(String.valueOf(gqlBlock.fees()));
        block.setBlockVrf(String.valueOf(gqlBlock.vrfKey()));
        block.setPreviousBlock(String.valueOf(gqlBlock.previousBlock().hash()));
        //TODO
//        block.setConfirmations();
        return block;
    }
}
