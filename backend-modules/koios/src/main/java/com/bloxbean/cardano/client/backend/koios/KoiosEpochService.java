package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.plutus.util.PlutusOps;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import rest.koios.client.backend.api.epoch.model.EpochInfo;
import rest.koios.client.backend.api.epoch.model.EpochParams;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Koios Epoch Service
 */
public class KoiosEpochService implements EpochService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Epoch Service
     */
    private final rest.koios.client.backend.api.epoch.EpochService epochService;

    /**
     * Koios Service Constructor
     *
     * @param epochService Koios Epoch Service
     */
    public KoiosEpochService(rest.koios.client.backend.api.epoch.EpochService epochService) {
        this.epochService = epochService;
    }

    private List<String> getInnerKeys(String key, JsonNode costModels) {
        List<String> keys = new ArrayList<>();
        JsonNode node = costModels.path(key);
        if (node.isInt()) {
            return List.of(key);
        }
        if (node.isTextual()) {
            return Collections.emptyList();
        }
        Iterator<String> stringIterator = costModels.path(key).fieldNames();
        while (stringIterator.hasNext()) {
            List<String> getKeys = getInnerKeys(stringIterator.next(), costModels.path(key));
            for (String innerKey : getKeys) {
                keys.add(key + "-" + innerKey);
            }
        }
        return keys;
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<EpochInfo> epochInformationResult = epochService.getLatestEpochInfo();
            if (!epochInformationResult.isSuccessful()) {
                return Result.error(epochInformationResult.getResponse()).code(epochInformationResult.getCode());
            }
            return convertToEpochContent(epochInformationResult.getValue());
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
            rest.koios.client.backend.api.base.Result<EpochParams> epochParametersResult = epochService.getLatestEpochParameters();
            if (!epochParametersResult.isSuccessful()) {
                return Result.error(epochParametersResult.getResponse()).code(epochParametersResult.getCode());
            }
            return convertToProtocolParams(epochParametersResult.getValue());
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
            protocolParams.setCostModels(convertToCostModels(epochParams.getCostModels()));
        }
        if (epochParams.getCoinsPerUtxoSize() != null) {
            protocolParams.setCoinsPerUtxoSize(epochParams.getCoinsPerUtxoSize());
        }

        //Pool voting threshold
        protocolParams.setPvtMotionNoConfidence(epochParams.getPvtMotionNoConfidence());
        protocolParams.setPvtCommitteeNormal(epochParams.getPvtCommitteeNormal());
        protocolParams.setPvtCommitteeNoConfidence(epochParams.getPvtCommitteeNoConfidence());
        protocolParams.setPvtHardForkInitiation(epochParams.getPvtHardForkInitiation());
        protocolParams.setPvtPPSecurityGroup(epochParams.getPvtppSecurityGroup());

        //Drep vote thresholds
        protocolParams.setDvtMotionNoConfidence(epochParams.getDvtMotionNoConfidence());
        protocolParams.setDvtCommitteeNormal(epochParams.getDvtCommitteeNormal());
        protocolParams.setDvtCommitteeNoConfidence(epochParams.getDvtCommitteeNoConfidence());
        protocolParams.setDvtUpdateToConstitution(epochParams.getDvtUpdateToConstitution());
        protocolParams.setDvtHardForkInitiation(epochParams.getDvtHardForkInitiation());

        protocolParams.setDvtPPNetworkGroup(epochParams.getDvtPPNetworkGroup());
        protocolParams.setDvtPPEconomicGroup(epochParams.getDvtPPEconomicGroup());
        protocolParams.setDvtPPTechnicalGroup(epochParams.getDvtPPTechnicalGroup());
        protocolParams.setDvtPPGovGroup(epochParams.getDvtPPGovGroup());
        protocolParams.setDvtTreasuryWithdrawal(epochParams.getDvtTreasuryWithdrawal());

        protocolParams.setCommitteeMinSize(epochParams.getCommitteeMinSize());
        protocolParams.setCommitteeMaxTermLength(epochParams.getCommitteeMaxTermLength());
        protocolParams.setGovActionLifetime(epochParams.getGovActionLifetime());
        if (epochParams.getGovActionDeposit() != null) {
            protocolParams.setGovActionDeposit(new BigInteger(epochParams.getGovActionDeposit().trim()));
        }
        if (epochParams.getDrepDeposit() != null) {
            protocolParams.setDrepDeposit(new BigInteger(epochParams.getDrepDeposit().trim()));
        }
        protocolParams.setDrepActivity(epochParams.getDrepActivity());
        protocolParams.setMinFeeRefScriptCostPerByte(epochParams.getMinFeeRefScriptCostPerByte());

        return Result.success("OK").withValue(protocolParams).code(200);
    }

    private LinkedHashMap<String, LinkedHashMap<String, Long>> convertToCostModels(JsonNode costModelsJsonNode) {
        String costModelsJson = costModelsJsonNode.asText();
        try {
            costModelsJson = objectMapper.writeValueAsString(costModelsJsonNode);
        } catch (JsonProcessingException ignored) {}

        LinkedHashMap<String, LinkedHashMap<String, Long>> res = new LinkedHashMap<>();
        try {
            LinkedHashMap<String, List<Long>> result = objectMapper.readValue(costModelsJson, new TypeReference<LinkedHashMap<String, List<Long>>>() {});
            final AtomicInteger plutusV1IndexHolder = new AtomicInteger();
            LinkedHashMap<String, Long> plutusV1CostModelsMap = new LinkedHashMap<>();
            final AtomicInteger plutusV2IndexHolder = new AtomicInteger();
            LinkedHashMap<String, Long> plutusV2CostModelsMap = new LinkedHashMap<>();
            final AtomicInteger plutusV3IndexHolder = new AtomicInteger();
            LinkedHashMap<String, Long> plutusV3CostModelsMap = new LinkedHashMap<>();
            result.forEach((key, value) -> {
                if (key.equals("PlutusV1")) {
                    if (value.size() == PlutusOps.getOperations(1).size()) {
                        value.forEach(aLong -> {
                            final int index = plutusV1IndexHolder.getAndIncrement();
                            plutusV1CostModelsMap.put(PlutusOps.getOperations(1).get(index), aLong);
                        });
                    } else {
                        value.forEach(aLong -> {
                            final int index = plutusV1IndexHolder.getAndIncrement();
                            plutusV1CostModelsMap.put(String.format("%03d", index), aLong);
                        });
                    }
                    res.put(key, plutusV1CostModelsMap);
                } else if (key.equals("PlutusV2")) {
                    if (value.size() == PlutusOps.getOperations(2).size()) {
                        value.forEach(aLong -> {
                            final int index = plutusV2IndexHolder.getAndIncrement();
                            plutusV2CostModelsMap.put(PlutusOps.getOperations(2).get(index), aLong);
                        });
                    } else {
                        value.forEach(aLong -> {
                            final int index = plutusV2IndexHolder.getAndIncrement();
                            plutusV2CostModelsMap.put(String.format("%03d", index), aLong);
                        });
                    }

                    res.put(key, plutusV2CostModelsMap);
                }  else if (key.equals("PlutusV3")) {

                    if (value.size() == PlutusOps.getOperations(3).size()) {
                        value.forEach(aLong -> {
                            final int index = plutusV3IndexHolder.getAndIncrement();
                            plutusV3CostModelsMap.put(PlutusOps.getOperations(3).get(index), aLong);
                        });
                    } else {
                        value.forEach(aLong -> {
                            final int index = plutusV3IndexHolder.getAndIncrement();
                            plutusV3CostModelsMap.put(String.format("%03d", index), aLong);
                        });
                    }
                    res.put(key, plutusV3CostModelsMap);
                }
            });
        } catch (JsonProcessingException ignored) {}
        return res;
    }
}
