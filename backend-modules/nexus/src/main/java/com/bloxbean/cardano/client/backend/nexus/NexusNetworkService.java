package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.model.Genesis;

/**
 * Nexus Network Service
 */
public class NexusNetworkService implements NetworkInfoService {

    private final adlabs.nexus.client.backend.api.network.NetworkService networkService;
    private final Network network;

    public NexusNetworkService(adlabs.nexus.client.backend.api.network.NetworkService networkService, Network network) {
        this.networkService = networkService;
        this.network = network;
    }

    @Override
    public Result<Genesis> getNetworkInfo() throws ApiException {
        try {
            return NexusResultMapper.map(networkService.getNetworkInfo(network), this::toGenesis);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Genesis toGenesis(adlabs.nexus.client.backend.api.network.model.NetworkInfo info) {
        Genesis genesis = new Genesis();
        genesis.setActiveSlotsCoefficient(info.getActiveSlotsCoefficient());
        genesis.setUpdateQuorum(info.getUpdateQuorum());
        genesis.setMaxLovelaceSupply(info.getMaxLovelaceSupply());
        genesis.setNetworkMagic(info.getNetworkMagic());
        genesis.setEpochLength(info.getEpochLength());
        genesis.setSystemStart(info.getSystemStart() == null ? null : info.getSystemStart().intValue());
        genesis.setSlotsPerKesPeriod(info.getSlotsPerKesPeriod());
        genesis.setSlotLength(info.getSlotLength() == null ? null : info.getSlotLength().intValue());
        genesis.setMaxKesEvolutions(info.getMaxKesEvolutions());
        genesis.setSecurityParam(info.getSecurityParam());
        return genesis;
    }
}
