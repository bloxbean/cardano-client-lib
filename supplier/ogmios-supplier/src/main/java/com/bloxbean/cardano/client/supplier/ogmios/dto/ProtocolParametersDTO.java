package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProtocolParametersDTO {

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
    private ExecutionUnitDTO scriptExecutionPrices;
    private ExecutionUnitDTO maxExecutionUnitsPerTransaction;
    private ExecutionUnitDTO maxExecutionUnitsPerBlock;
    private Map<String, Integer> maxValueSize;
    private long collateralPercentage;
    private long maxCollateralInputs;
    private Map<String, Integer> version;
    private VotingThresholdDTO stakePoolVotingThresholds;
    private VotingThresholdDTO delegateRepresentativeVotingThresholds;
    private long constitutionalCommitteeMinSize;
    private long constitutionalCommitteeMaxTermLength;
    private Map<String, Map<String, Long>> governanceActionDeposit;
    private Map<String, Map<String, Long>> delegateRepresentativeDeposit;
    private long delegateRepresentativeMaxIdleTime;

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

        Map<String, Map<String, Long>> costModels = new HashMap<>();
        costModels.put("PlutusV1",
                new HashMap<>() {{
                    for (int i = 0; i < plutusCostModels.get("plutus:v1").length; i++) {
                        put(PlutusCostModelConstants.PLUTUS_V1_COST_MODEL[i], plutusCostModels.get("plutus:v1")[i]);
                    }
                }}
        );
        costModels.put("PlutusV2",
                new HashMap<>() {{
                    for (int i = 0; i < plutusCostModels.get("plutus:v2").length; i++) {
                        put(PlutusCostModelConstants.PLUTUS_V2_COST_MODEL[i], plutusCostModels.get("plutus:v2")[i]);
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
//        protocolParams.setCoinsPerUtxoWord(String.valueOf(minUtxoDepositCoefficient)); // deprecated
        return protocolParams;
    }

    /**
     * Convert a string to BigDecimal. String format :  a/b
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
