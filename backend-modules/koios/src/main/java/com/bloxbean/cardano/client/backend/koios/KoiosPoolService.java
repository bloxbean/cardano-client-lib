package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.PoolInfo;
import org.apache.commons.collections4.CollectionUtils;
import rest.koios.client.backend.api.pool.PoolService;
import rest.koios.client.backend.factory.options.Options;

import java.util.List;

/**
 * Koios Pool Service
 */
public class KoiosPoolService implements com.bloxbean.cardano.client.backend.api.PoolService {

    /**
     * Pool Service
     */
    private final PoolService poolService;

    /**
     * KoiosNetworkService Constructor
     *
     * @param poolService poolService
     */
    public KoiosPoolService(PoolService poolService) {
        this.poolService = poolService;
    }

    @Override
    public Result<PoolInfo> getPoolInfo(String poolId) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.pool.model.PoolInfo>> poolInfoResult = poolService.getPoolInformation(List.of(poolId), Options.EMPTY);
            if (!poolInfoResult.isSuccessful()) {
                return Result.error(poolInfoResult.getResponse()).code(poolInfoResult.getCode());
            } else if (CollectionUtils.isEmpty(poolInfoResult.getValue())) {
                return Result.error("Pool Not Found").code(404);
            }
            rest.koios.client.backend.api.pool.model.PoolInfo info = poolInfoResult.getValue().get(0);
            PoolInfo poolInfo = new PoolInfo();
            poolInfo.setPoolId(info.getPoolIdBech32());
            poolInfo.setHex(info.getPoolIdHex());
            poolInfo.setVrfKey(info.getVrfKeyHash());
            poolInfo.setBlocksMinted(info.getBlockCount());
            poolInfo.setLiveStake(info.getLiveStake());
            poolInfo.setLiveSaturation(info.getLiveSaturation());
            poolInfo.setLiveDelegators(info.getLiveDelegators());
            poolInfo.setActiveStake(info.getActiveStake());
            poolInfo.setActiveSize(info.getSigma());
            poolInfo.setDeclaredPledge(info.getPledge());
            poolInfo.setLivePledge(info.getLivePledge());
            poolInfo.setMarginCost(info.getMargin());
            poolInfo.setFixedCost(info.getFixedCost());
            poolInfo.setRewardAccount(info.getRewardAddr());
            poolInfo.setOwners(info.getOwners());
            return Result.success("OK").withValue(poolInfo).code(200);
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            return Result.error(e.getMessage());
        }
    }
}
