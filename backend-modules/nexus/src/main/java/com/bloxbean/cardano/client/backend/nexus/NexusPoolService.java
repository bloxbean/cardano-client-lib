package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.PoolService;
import com.bloxbean.cardano.client.backend.model.PoolInfo;

/**
 * Nexus Pool Service. bloxbean's PoolService has a single method, which the Nexus SDK's
 * getPool fully backs.
 */
public class NexusPoolService implements PoolService {

    private final adlabs.nexus.client.backend.api.pool.PoolService poolService;
    private final Network network;

    public NexusPoolService(adlabs.nexus.client.backend.api.pool.PoolService poolService, Network network) {
        this.poolService = poolService;
        this.network = network;
    }

    @Override
    public Result<PoolInfo> getPoolInfo(String poolId) throws ApiException {
        try {
            return NexusResultMapper.map(poolService.getPool(network, poolId), this::toPoolInfo);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private PoolInfo toPoolInfo(adlabs.nexus.client.backend.api.pool.model.Pool pool) {
        PoolInfo poolInfo = new PoolInfo();
        poolInfo.setPoolId(pool.getPoolIdBech32());
        poolInfo.setHex(pool.getPoolIdHex());
        poolInfo.setVrfKey(pool.getVrfKeyHash());
        poolInfo.setBlocksMinted(pool.getBlocksMinted() == null ? null : pool.getBlocksMinted().intValue());
        poolInfo.setLiveStake(pool.getLiveStake() == null ? null : pool.getLiveStake().toString());
        poolInfo.setLiveSaturation(pool.getLiveSaturationPct() == null ? null : pool.getLiveSaturationPct().doubleValue());
        poolInfo.setLiveDelegators(pool.getLiveDelegators() == null ? null : pool.getLiveDelegators().intValue());
        poolInfo.setActiveStake(pool.getActiveStake() == null ? null : pool.getActiveStake().toString());
        poolInfo.setActiveSize(pool.getSigma() == null ? null : pool.getSigma().doubleValue());
        poolInfo.setDeclaredPledge(pool.getPledgeDeclared() == null ? null : pool.getPledgeDeclared().toString());
        poolInfo.setLivePledge(pool.getLivePledge() == null ? null : pool.getLivePledge().toString());
        poolInfo.setMarginCost(pool.getMarginPct() == null ? null : pool.getMarginPct().doubleValue());
        poolInfo.setFixedCost(pool.getFixedCost() == null ? null : pool.getFixedCost().toString());
        poolInfo.setRewardAccount(pool.getRewardAddr());
        poolInfo.setOwners(pool.getOwners());
        return poolInfo;
    }
}
