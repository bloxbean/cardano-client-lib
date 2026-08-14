package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;

/**
 * Nexus Epoch Service
 */
public class NexusEpochService implements EpochService {

    private final adlabs.nexus.client.backend.api.epoch.EpochService epochService;
    private final Network network;

    public NexusEpochService(adlabs.nexus.client.backend.api.epoch.EpochService epochService, Network network) {
        this.epochService = epochService;
        this.network = network;
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        try {
            return NexusResultMapper.map(epochService.getLatestEpoch(network), this::toEpochContent);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
        throw new UnsupportedOperationException("getEpoch not supported by Nexus");
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
        try {
            return NexusResultMapper.map(epochService.getEpochParams(network, epoch), this::toProtocolParams);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() throws ApiException {
        try {
            return NexusResultMapper.map(epochService.getLatestEpochParameters(network), this::toProtocolParams);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private EpochContent toEpochContent(adlabs.nexus.client.backend.api.epoch.model.Epoch ep) {
        EpochContent epochContent = new EpochContent();
        epochContent.setEpoch(ep.getEpoch());
        epochContent.setStartTime(ep.getStartTime() == null ? 0L : ep.getStartTime());
        epochContent.setEndTime(ep.getEndTime() == null ? 0L : ep.getEndTime());
        epochContent.setFirstBlockTime(ep.getFirstBlockTime() == null ? 0L : ep.getFirstBlockTime());
        epochContent.setLastBlockTime(ep.getLastBlockTime() == null ? 0L : ep.getLastBlockTime());
        epochContent.setBlockCount(ep.getBlockCount());
        epochContent.setTxCount(ep.getTxCount());
        epochContent.setOutput(ep.getOutput());
        epochContent.setFees(ep.getFees());
        epochContent.setActiveStake(ep.getActiveStake());
        return epochContent;
    }

    // Nexus SDK's ProtocolParams already declares the same type per field as bloxbean's
    // (incl. govActionDeposit/drepDeposit as BigInteger and costModels as LinkedHashMap<String,LinkedHashMap<String,Long>>),
    // so every overlapping field is a direct assignment; no String->BigInteger conversion is needed here.
    private ProtocolParams toProtocolParams(adlabs.nexus.client.backend.api.epoch.model.ProtocolParams pp) {
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA(pp.getMinFeeA());
        protocolParams.setMinFeeB(pp.getMinFeeB());
        protocolParams.setMaxBlockSize(pp.getMaxBlockSize());
        protocolParams.setMaxTxSize(pp.getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(pp.getMaxBlockHeaderSize());
        protocolParams.setKeyDeposit(pp.getKeyDeposit());
        protocolParams.setPoolDeposit(pp.getPoolDeposit());
        protocolParams.setEMax(pp.getEMax());
        protocolParams.setNOpt(pp.getNOpt());
        protocolParams.setA0(pp.getA0());
        protocolParams.setRho(pp.getRho());
        protocolParams.setTau(pp.getTau());
        protocolParams.setDecentralisationParam(pp.getDecentralisationParam());
        protocolParams.setExtraEntropy(pp.getExtraEntropy());
        protocolParams.setProtocolMajorVer(pp.getProtocolMajorVer());
        protocolParams.setProtocolMinorVer(pp.getProtocolMinorVer());
        protocolParams.setMinUtxo(pp.getMinUtxo());
        protocolParams.setMinPoolCost(pp.getMinPoolCost());
        protocolParams.setNonce(pp.getNonce());
        // costModelsRaw has no Nexus SDK source (SDK carries only the map-of-map form); left null.
        protocolParams.setCostModels(pp.getCostModels());
        protocolParams.setPriceMem(pp.getPriceMem());
        protocolParams.setPriceStep(pp.getPriceStep());
        protocolParams.setMaxTxExMem(pp.getMaxTxExMem());
        protocolParams.setMaxTxExSteps(pp.getMaxTxExSteps());
        protocolParams.setMaxBlockExMem(pp.getMaxBlockExMem());
        protocolParams.setMaxBlockExSteps(pp.getMaxBlockExSteps());
        protocolParams.setMaxValSize(pp.getMaxValSize());
        protocolParams.setCollateralPercent(pp.getCollateralPercent());
        protocolParams.setMaxCollateralInputs(pp.getMaxCollateralInputs());
        protocolParams.setCoinsPerUtxoSize(pp.getCoinsPerUtxoSize());
        // coinsPerUtxoWord (deprecated Alonzo-word variant) has no Nexus SDK source; left null.

        // Conway: pool voting thresholds
        protocolParams.setPvtMotionNoConfidence(pp.getPvtMotionNoConfidence());
        protocolParams.setPvtCommitteeNormal(pp.getPvtCommitteeNormal());
        protocolParams.setPvtCommitteeNoConfidence(pp.getPvtCommitteeNoConfidence());
        protocolParams.setPvtHardForkInitiation(pp.getPvtHardForkInitiation());
        protocolParams.setPvtPPSecurityGroup(pp.getPvtPPSecurityGroup());

        // Conway: DRep vote thresholds
        protocolParams.setDvtMotionNoConfidence(pp.getDvtMotionNoConfidence());
        protocolParams.setDvtCommitteeNormal(pp.getDvtCommitteeNormal());
        protocolParams.setDvtCommitteeNoConfidence(pp.getDvtCommitteeNoConfidence());
        protocolParams.setDvtUpdateToConstitution(pp.getDvtUpdateToConstitution());
        protocolParams.setDvtHardForkInitiation(pp.getDvtHardForkInitiation());
        protocolParams.setDvtPPNetworkGroup(pp.getDvtPPNetworkGroup());
        protocolParams.setDvtPPEconomicGroup(pp.getDvtPPEconomicGroup());
        protocolParams.setDvtPPTechnicalGroup(pp.getDvtPPTechnicalGroup());
        protocolParams.setDvtPPGovGroup(pp.getDvtPPGovGroup());
        protocolParams.setDvtTreasuryWithdrawal(pp.getDvtTreasuryWithdrawal());

        protocolParams.setCommitteeMinSize(pp.getCommitteeMinSize());
        protocolParams.setCommitteeMaxTermLength(pp.getCommitteeMaxTermLength());
        protocolParams.setGovActionLifetime(pp.getGovActionLifetime());
        protocolParams.setGovActionDeposit(pp.getGovActionDeposit());
        protocolParams.setDrepDeposit(pp.getDrepDeposit());
        protocolParams.setDrepActivity(pp.getDrepActivity());
        protocolParams.setMinFeeRefScriptCostPerByte(pp.getMinFeeRefScriptCostPerByte());
        return protocolParams;
    }
}
