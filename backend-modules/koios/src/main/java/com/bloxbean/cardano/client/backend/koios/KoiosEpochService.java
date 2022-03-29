package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import rest.koios.client.backend.api.epoch.model.EpochInfo;
import rest.koios.client.backend.api.epoch.model.EpochParams;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Options;
import rest.koios.client.backend.factory.options.Order;
import rest.koios.client.backend.factory.options.SortType;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class KoiosEpochService implements EpochService {

    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private final rest.koios.client.backend.api.epoch.EpochService epochService;

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
        } catch (ParseException e) {
            throw new ApiException("Failed to Parse Date: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<EpochInfo> epochInformationResult = epochService.getEpochInformationByEpoch(Long.valueOf(epoch));
            if (!epochInformationResult.isSuccessful()) {
                return Result.error(epochInformationResult.getResponse()).code(epochInformationResult.getCode());
            }
            return convertToEpochContent(epochInformationResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        } catch (ParseException e) {
            throw new ApiException("Failed to Parse Date: " + e.getMessage(), e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<EpochParams> epochParametersResult = epochService.getEpochParametersByEpoch(Long.valueOf(epoch));
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

    private Result<EpochContent> convertToEpochContent(EpochInfo epochInfo) throws ParseException {
        EpochContent epochContent = new EpochContent();
        epochContent.setEpoch(Math.toIntExact(epochInfo.getEpochNo()));
        epochContent.setStartTime(simpleDateFormat.parse(epochInfo.getStartTime()).getTime() / 1000);
        epochContent.setEndTime(simpleDateFormat.parse(epochInfo.getEndTime()).getTime() / 1000);
        epochContent.setFirstBlockTime(simpleDateFormat.parse(epochInfo.getFirstBlockTime()).getTime() / 1000);
        epochContent.setLastBlockTime(simpleDateFormat.parse(epochInfo.getLastBlockTime()).getTime() / 1000);
        epochContent.setBlockCount(epochInfo.getBlkCount());
        epochContent.setTxCount(Math.toIntExact(epochInfo.getTxCount()));
        epochContent.setOutput(String.valueOf(epochInfo.getOutSum()));
        epochContent.setFees(String.valueOf(epochInfo.getFees()));
        epochContent.setActiveStake(String.valueOf(epochInfo.getActiveStake()));
        return Result.success("OK").withValue(epochContent).code(200);
    }

    private Result<ProtocolParams> convertToProtocolParams(EpochParams epochInfo) {
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA(epochInfo.getMinFeeA());
        protocolParams.setMinFeeB(epochInfo.getMinFeeB());
        protocolParams.setMaxBlockSize(epochInfo.getMaxBlockSize());
        protocolParams.setMaxTxSize(epochInfo.getMaxTxSize());
        protocolParams.setMaxBlockHeaderSize(epochInfo.getMaxBhSize());
        protocolParams.setKeyDeposit(String.valueOf(epochInfo.getKeyDeposit()));
        protocolParams.setPoolDeposit(String.valueOf(epochInfo.getPoolDeposit()));
        protocolParams.setEMax(epochInfo.getMaxEpoch());
        protocolParams.setNOpt(epochInfo.getOptimalPoolCount());
        protocolParams.setA0(BigDecimal.valueOf(epochInfo.getInfluence()));
        protocolParams.setRho(BigDecimal.valueOf(epochInfo.getMonetaryExpandRate()));
        protocolParams.setTau(BigDecimal.valueOf(epochInfo.getTreasuryGrowthRate()));
        protocolParams.setDecentralisationParam(BigDecimal.valueOf(epochInfo.getDecentralisation()));
        protocolParams.setExtraEntropy(epochInfo.getEntropy());
        protocolParams.setProtocolMajorVer(epochInfo.getProtocolMajor());
        protocolParams.setProtocolMinorVer(epochInfo.getProtocolMinor());
        protocolParams.setMinUtxo(String.valueOf(epochInfo.getMinUtxoValue()));
        protocolParams.setMinPoolCost(String.valueOf(epochInfo.getMinPoolCost()));
        protocolParams.setNonce(epochInfo.getNonce());
        if (epochInfo.getPriceMem() != null) {
            protocolParams.setPriceMem(BigDecimal.valueOf(epochInfo.getPriceMem()));
        }
        if (epochInfo.getPriceStep() != null) {
            protocolParams.setPriceStep(BigDecimal.valueOf(epochInfo.getPriceStep()));
        }
        if (epochInfo.getMaxTxExMem() != null) {
            protocolParams.setMaxTxExMem(String.valueOf(epochInfo.getMaxTxExMem()));
        }
        if (epochInfo.getMaxTxExSteps() != null) {
            protocolParams.setMaxTxExSteps(String.valueOf(epochInfo.getMaxTxExSteps()));
        }
        if (epochInfo.getMaxBlockExMem() != null) {
            protocolParams.setMaxBlockExMem(String.valueOf(epochInfo.getMaxBlockExMem()));
        }
        if (epochInfo.getMaxBlockExSteps() != null) {
            protocolParams.setMaxBlockExSteps(String.valueOf(epochInfo.getMaxBlockExSteps()));
        }
        if (epochInfo.getCollateralPercent() != null) {
            protocolParams.setCollateralPercent(BigDecimal.valueOf(epochInfo.getCollateralPercent()));
        }
        if (epochInfo.getMaxCollateralInputs() != null) {
            protocolParams.setMaxCollateralInputs(epochInfo.getMaxCollateralInputs());
        }
        if (epochInfo.getCoinsPerUtxoWord() != null) {
            protocolParams.setCoinsPerUtxoWord(String.valueOf(epochInfo.getCoinsPerUtxoWord()));
        }
        return Result.success("OK").withValue(protocolParams).code(200);
    }
}
