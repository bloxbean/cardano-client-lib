package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import rest.koios.client.backend.api.epoch.model.EpochInfo;
import rest.koios.client.backend.api.epoch.model.EpochParams;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.Order;
import rest.koios.client.backend.factory.options.SortType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Koios Epoch Service
 */
public class KoiosEpochService implements EpochService {

    /**
     * Epoch Service
     */
    private final rest.koios.client.backend.api.epoch.EpochService epochService;

    /**
     * Koios Service Constructor
     * @param epochService Koios Epoch Service
     */
    public KoiosEpochService(rest.koios.client.backend.api.epoch.EpochService epochService) {
        this.epochService = epochService;
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<EpochInfo>> epochInformationResult = epochService.getEpochInformation(Options.builder()
                    .option(Limit.of(1))
                    .option(Order.by("epoch_no", SortType.DESC)).build());
            if (!epochInformationResult.isSuccessful()) {
                return Result.error(epochInformationResult.getResponse()).code(epochInformationResult.getCode());
            }
            return convertToEpochContent(epochInformationResult.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<EpochInfo> epochInformationResult = epochService.getEpochInformationByEpoch(epoch);
            if (!epochInformationResult.isSuccessful()) {
                return Result.error(epochInformationResult.getResponse()).code(epochInformationResult.getCode());
            }
            return convertToEpochContent(epochInformationResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<EpochParams> epochParametersResult = epochService.getEpochParametersByEpoch(epoch);
            if (!epochParametersResult.isSuccessful()) {
                return Result.error(epochParametersResult.getResponse()).code(epochParametersResult.getCode());
            }
            return convertToProtocolParams(epochParametersResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<List<EpochParams>> epochParametersResult = epochService.getEpochParameters(Options.builder()
                    .option(Limit.of(1))
                    .option(Order.by("epoch_no", SortType.DESC))
                    .build());
            if (!epochParametersResult.isSuccessful()) {
                return Result.error(epochParametersResult.getResponse()).code(epochParametersResult.getCode());
            }
            return convertToProtocolParams(epochParametersResult.getValue().get(0));
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<EpochContent> convertToEpochContent(EpochInfo epochInfo) {
        EpochContent epochContent = new EpochContent();
        epochContent.setEpoch(epochInfo.getEpochNo());
        epochContent.setStartTime(epochInfo.getStartTime());
        epochContent.setEndTime(epochInfo.getEndTime());
        epochContent.setFirstBlockTime(epochInfo.getFirstBlockTime());
        epochContent.setLastBlockTime(epochInfo.getLastBlockTime());
        epochContent.setBlockCount(epochInfo.getBlkCount());
        epochContent.setTxCount(epochInfo.getTxCount());
        epochContent.setOutput(epochInfo.getOutSum());
        epochContent.setFees(epochInfo.getFees());
        epochContent.setActiveStake(epochInfo.getActiveStake());
        return Result.success("OK").withValue(epochContent).code(200);
    }

    private Result<ProtocolParams> convertToProtocolParams(EpochParams epochParams) {
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA(epochParams.getMinFeeA());
        protocolParams.setMinFeeB(epochParams.getMinFeeB());
        protocolParams.setMaxBlockSize(epochParams.getMaxBlockSize());
        protocolParams.setMaxTxSize(epochParams.getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(epochParams.getMaxBhSize());
        protocolParams.setKeyDeposit(epochParams.getKeyDeposit());
        protocolParams.setPoolDeposit(epochParams.getPoolDeposit());
        protocolParams.setEMax(epochParams.getMaxEpoch());
        protocolParams.setNOpt(epochParams.getOptimalPoolCount());
        protocolParams.setA0(epochParams.getInfluence());
        protocolParams.setRho(epochParams.getMonetaryExpandRate());
        protocolParams.setTau(epochParams.getTreasuryGrowthRate());
        protocolParams.setDecentralisationParam(epochParams.getDecentralisation());
        protocolParams.setExtraEntropy(epochParams.getExtraEntropy());
        protocolParams.setProtocolMajorVer(epochParams.getProtocolMajor());
        protocolParams.setProtocolMinorVer(epochParams.getProtocolMinor());
        protocolParams.setMinUtxo(epochParams.getMinUtxoValue());
        protocolParams.setMinPoolCost(epochParams.getMinPoolCost());
        protocolParams.setNonce(epochParams.getNonce());
        if (epochParams.getPriceMem() != null) {
            protocolParams.setPriceMem(epochParams.getPriceMem());
        }
        if (epochParams.getPriceStep() != null) {
            protocolParams.setPriceStep(epochParams.getPriceStep());
        }
        if (epochParams.getMaxTxExMem() != null) {
            protocolParams.setMaxTxExMem(epochParams.getMaxTxExMem());
        }
        if (epochParams.getMaxTxExSteps() != null) {
            protocolParams.setMaxTxExSteps(epochParams.getMaxTxExSteps());
        }
        if (epochParams.getMaxBlockExMem() != null) {
            protocolParams.setMaxBlockExMem(epochParams.getMaxBlockExMem());
        }
        if (epochParams.getMaxBlockExSteps() != null) {
            protocolParams.setMaxBlockExSteps(epochParams.getMaxBlockExSteps());
        }
        if (epochParams.getMaxValSize() != null) {
            protocolParams.setMaxValSize(epochParams.getMaxValSize());
        }
        if (epochParams.getCollateralPercent() != null) {
            protocolParams.setCollateralPercent(BigDecimal.valueOf(epochParams.getCollateralPercent()));
        }
        if (epochParams.getMaxCollateralInputs() != null) {
            protocolParams.setMaxCollateralInputs(epochParams.getMaxCollateralInputs());
        }
        if (epochParams.getCostModels() != null) {
            protocolParams.setCostModels(epochParams.getCostModels());
        }
        if (epochParams.getCoinsPerUtxoWord() != null) {
            protocolParams.setCoinsPerUtxoWord(epochParams.getCoinsPerUtxoWord());
        }
        if (epochParams.getCoinsPerUtxoSize() != null) {
            protocolParams.setCoinsPerUtxoSize(epochParams.getCoinsPerUtxoSize());
        }
        return Result.success("OK").withValue(protocolParams).code(200);
    }
}
