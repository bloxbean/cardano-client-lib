package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.plutus.util.PlutusOps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProtocolParametersDto {

    private long minFeeCoefficient;
    private Map<String, Map<String, Long>> minFeeConstant; // TODO write own DTO for amount
    private Map<String, Integer> maxBlockBodySize;
    private Map<String, Integer> maxBlockHeaderSize;
    private Map<String, Integer> maxTransactionSize;
    private Map<String, Map<String, Long>> stakeCredentialDeposit;
    private Map<String, Map<String, Long>> stakePoolDeposit;
    private long stakePoolRetirementEpochBound;
    private long desiredNumberOfStakePools;
    private String stakePoolPledgeInfluence;
    private String monetaryExpansion;
    private String treasuryExpansion;
    private Map<String, Map<String, Long>> minStakePoolCost;
    private Map<String, Map<String, Long>> minUtxoDepositConstant;
    private int minUtxoDepositCoefficient;
    private Map<String, Long[]> plutusCostModels;
    private ExecutionUnitDto scriptExecutionPrices;
    private ExecutionUnitDto maxExecutionUnitsPerTransaction;
    private ExecutionUnitDto maxExecutionUnitsPerBlock;
    private Map<String, Integer> maxValueSize;
    private long collateralPercentage;
    private long maxCollateralInputs;
    private Map<String, Integer> version;
    private VotingThresholdDto stakePoolVotingThresholds;
    private VotingThresholdDto delegateRepresentativeVotingThresholds;
    private long constitutionalCommitteeMinSize;
    private long constitutionalCommitteeMaxTermLength;
    private Map<String, Map<String, Long>> governanceActionDeposit;
    private Map<String, Map<String, Long>> delegateRepresentativeDeposit;
    private long delegateRepresentativeMaxIdleTime;
    private MinFeeReferenceScriptsDto minFeeReferenceScripts;

    public ProtocolParams toProtocolParams() {
        ProtocolParams protocolParams = new ProtocolParams();
        protocolParams.setMinFeeA((int) minFeeCoefficient);
        protocolParams.setMinFeeB(minFeeConstant.get("ada").get("lovelace").intValue());
        protocolParams.setMaxBlockSize(maxBlockBodySize.get("bytes").intValue());
        protocolParams.setMaxTxSize(maxTransactionSize.get("bytes").intValue());
        protocolParams.setMaxBlockHeaderSize(maxBlockHeaderSize.get("bytes").intValue());
        protocolParams.setKeyDeposit(stakeCredentialDeposit.get("ada").get("lovelace").toString());
        protocolParams.setPoolDeposit(stakePoolDeposit.get("ada").get("lovelace").toString());
        protocolParams.setEMax((int) stakePoolRetirementEpochBound);
        protocolParams.setNOpt((int) desiredNumberOfStakePools);
        protocolParams.setA0(stringToDecimal(stakePoolPledgeInfluence));
        protocolParams.setRho(stringToDecimal(monetaryExpansion));
        protocolParams.setTau(stringToDecimal(treasuryExpansion));
//        protocolParams.setDecentralisationParam(stringToDecimal()); //Deprecated. Not there
//        protocolParams.setExtraEntropy(); // Not there
        protocolParams.setProtocolMajorVer(version.get("major"));
        protocolParams.setProtocolMinorVer(version.get("minor"));
        protocolParams.setMinUtxo(String.valueOf(minUtxoDepositCoefficient));
        protocolParams.setMinPoolCost(minStakePoolCost.get("ada").get("lovelace").toString());
//        protocolParams.setNonce(currentProtocolParameters.getProtocolParameters().getNonce()); // Not there

        LinkedHashMap<String, LinkedHashMap<String, Long>> costModels = new LinkedHashMap<>();
        costModels.put("PlutusV1",
                new LinkedHashMap<>() {{
                    List<String> plutusOps = PlutusOps.getOperations(1);
                    if (plutusOps.size() == plutusCostModels.get("plutus:v1").length) {
                        for (int i = 0; i < plutusCostModels.get("plutus:v1").length; i++) {
                            put(plutusOps.get(i), plutusCostModels.get("plutus:v1")[i]);
                        }
                    } else {
                        for (int i = 0; i < plutusCostModels.get("plutus:v1").length; i++) {
                            put(String.format("%03d", i), plutusCostModels.get("plutus:v1")[i]);
                        }
                    }
                }}
        );
        costModels.put("PlutusV2",
                new LinkedHashMap<>() {{
                    List<String> plutusOps = PlutusOps.getOperations(2);
                    if (plutusOps.size() == plutusCostModels.get("plutus:v2").length) {
                        for (int i = 0; i < plutusCostModels.get("plutus:v2").length; i++) {
                            put(plutusOps.get(i), plutusCostModels.get("plutus:v2")[i]);
                        }
                    } else {
                        for (int i = 0; i < plutusCostModels.get("plutus:v2").length; i++) {
                            put(String.format("%03d", i), plutusCostModels.get("plutus:v2")[i]);
                        }
                    }
                }}
        );
        costModels.put("PlutusV3",
                new LinkedHashMap<>() {{
                    List<String> plutusOps = PlutusOps.getOperations(3);
                    if (plutusOps.size() == plutusCostModels.get("plutus:v3").length) {
                        for (int i = 0; i < plutusCostModels.get("plutus:v3").length; i++) {
                            put(plutusOps.get(i), plutusCostModels.get("plutus:v3")[i]);
                        }
                    } else {
                        for (int i = 0; i < plutusCostModels.get("plutus:v3").length; i++) {
                            put(String.format("%03d", i), plutusCostModels.get("plutus:v3")[i]);
                        }
                    }
                }}
        );
        protocolParams.setCostModels(costModels);

        protocolParams.setPriceMem(stringToDecimal(scriptExecutionPrices.getMemory()));
        protocolParams.setPriceStep(stringToDecimal(scriptExecutionPrices.getCpu())); // TODO
        protocolParams.setMaxTxExMem(maxExecutionUnitsPerTransaction.getMemory());
        protocolParams.setMaxTxExSteps(maxExecutionUnitsPerTransaction.getCpu());
        protocolParams.setMaxBlockExMem(maxExecutionUnitsPerBlock.getMemory());
        protocolParams.setMaxBlockExSteps(maxExecutionUnitsPerBlock.getCpu());
        protocolParams.setMaxValSize(String.valueOf(maxValueSize.get("bytes").intValue()));
        protocolParams.setCollateralPercent(BigDecimal.valueOf(collateralPercentage));
        protocolParams.setMaxCollateralInputs((int) maxCollateralInputs);
        protocolParams.setCoinsPerUtxoSize(String.valueOf(minUtxoDepositCoefficient));
//      protocolParams.setCoinsPerUtxoWord(String.valueOf(minUtxoDepositCoefficient)); // deprecated

        //TODO
        //Governance releated protocol parameters

        if (minFeeReferenceScripts != null)
            protocolParams.setMinFeeRefScriptCostPerByte(minFeeReferenceScripts.getBase());

        return protocolParams;
    }

    /**
     * Convert a string to BigDecimal. String format :  a/b
     *
     * @param str str
     * @return BigDecimal
     */
    private static BigDecimal stringToDecimal(String str) {
        try {
            if (str == null || str.isEmpty()) {
                return null;
            }
            String[] parts = str.split("/");
            double d = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            return new BigDecimal(d);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
