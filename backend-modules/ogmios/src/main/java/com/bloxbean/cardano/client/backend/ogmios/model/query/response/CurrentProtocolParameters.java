package com.bloxbean.cardano.client.backend.ogmios.model.query.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@ToString
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class CurrentProtocolParameters extends QueryResponse {
    private static ObjectMapper objectMapper = new ObjectMapper();

    private Integer minFeeA;
    private Integer minFeeB;
    private Integer maxBlockSize;
    private Integer maxTxSize;
    private Integer maxBlockHeaderSize;
    private String keyDeposit;
    private String poolDeposit;
    private Integer eMax;
    private Integer nOpt;
    private BigDecimal a0;
    private BigDecimal rho;
    private BigDecimal tau;
    private BigDecimal decentralisationParam;
    private String extraEntropy;
    private Integer protocolMajorVer;
    private Integer protocolMinorVer;
    private String minUtxo;
    private String minPoolCost;
    private String nonce;

    //Alonzo changes
    private Map<String, Map<String, Long>> costModels;
    private BigDecimal priceMem;
    private BigDecimal priceStep;
    private String maxTxExMem;
    private String maxTxExSteps;
    private String maxBlockExMem;
    private String maxBlockExSteps;
    private String maxValSize;
    private BigDecimal collateralPercent;
    private Integer maxCollateralInputs;

    //Cost per UTxO word for Alonzo.
    //Cost per UTxO byte for Babbage and later.
    private String coinsPerUtxoSize;
    private String coinsPerUtxoWord;

    public CurrentProtocolParameters(long msgId) {
        super(msgId);
    }

    public static CurrentProtocolParameters deserialize(long msgId, JsonNode result) {
        System.out.printf("Json >> " + result);

        CurrentProtocolParameters protocolParams = new CurrentProtocolParameters(msgId);

        if (result.has("minFeeCoefficient")) {
            protocolParams.setMinFeeA(result.get("minFeeCoefficient").asInt());
        }

        if (result.has("minFeeConstant")) {
            protocolParams.setMinFeeB(result.get("minFeeConstant").asInt());
        }

        if (result.has("maxBlockBodySize")) {
            protocolParams.setMaxBlockSize(result.get("maxBlockBodySize").asInt());
        }

        if (result.has("maxBlockHeaderSize")) {
            protocolParams.setMaxBlockHeaderSize(result.get("maxBlockHeaderSize").asInt());
        }

        if (result.has("maxTxSize")) {
            protocolParams.setMaxTxSize(result.get("maxTxSize").asInt());
        }

        if (result.has("stakeKeyDeposit")) {
            protocolParams.setKeyDeposit(result.get("stakeKeyDeposit").asText());
        }

        if (result.has("poolDeposit")) {
            protocolParams.setPoolDeposit(result.get("poolDeposit").asText());
        }

        if (result.has("poolRetirementEpochBound")) {
            protocolParams.setEMax(result.get("poolRetirementEpochBound").asInt());
        }

        if (result.has("desiredNumberOfPools")) {
            protocolParams.setNOpt(result.get("desiredNumberOfPools").asInt());
        }

        if (result.has("poolInfluence")) {
            protocolParams.setA0(stringToDecimal(result.get("poolInfluence").asText()));
        }

        if (result.has("monetaryExpansion")) {
            protocolParams.setRho(stringToDecimal(result.get("monetaryExpansion").asText()));
        }

        if (result.has("treasuryExpansion")) {
            protocolParams.setTau(stringToDecimal(result.get("treasuryExpansion").asText()));
        }

        if (result.has("protocolVersion")) {
            if (result.get("protocolVersion").has("major"))
                protocolParams.setProtocolMajorVer(result.get("protocolVersion").get("major").asInt());
            if (result.get("protocolVersion").has("minor"))
                protocolParams.setProtocolMinorVer(result.get("protocolVersion").get("minor").asInt());
        }

        if (result.has("minPoolCost")) {
            protocolParams.setMinPoolCost(result.get("minPoolCost").asText());
        }

        if (result.has("coinsPerUtxoByte")) {
            protocolParams.setCoinsPerUtxoSize(result.get("coinsPerUtxoByte").asText());
        }

        if (result.has("coinsPerUtxoByte")) {
            protocolParams.setCoinsPerUtxoSize(result.get("coinsPerUtxoByte").asText());
        }

        if (result.has("costModels")) {
            try {
                Map<String, Map<String, Long>> costModels = objectMapper.readValue(result.get("costModels").toString(),
                        new TypeReference<Map<String, Map<String, Long>>>(){});
                protocolParams.setCostModels(costModels);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        if (result.has("prices") && result.get("prices").has("memory")) {
            protocolParams.setPriceMem(stringToDecimal(result.get("prices").get("memory").asText()));
        }

        if (result.has("prices") && result.get("prices").has("steps")) {
            protocolParams.setPriceStep(stringToDecimal(result.get("prices").get("steps").asText()));
        }

        if (result.has("maxExecutionUnitsPerTransaction") && result.get("maxExecutionUnitsPerTransaction").has("memory")) {
            protocolParams.setMaxTxExMem(result.get("maxExecutionUnitsPerTransaction").get("memory").asText());
        }
        if (result.has("maxExecutionUnitsPerTransaction") && result.get("maxExecutionUnitsPerTransaction").has("steps")) {
            protocolParams.setMaxTxExSteps(result.get("maxExecutionUnitsPerTransaction").get("steps").asText());
        }

        if (result.has("maxExecutionUnitsPerBlock") && result.get("maxExecutionUnitsPerBlock").has("memory")) {
            protocolParams.setMaxBlockExMem(result.get("maxExecutionUnitsPerBlock").get("memory").asText());
        }
        if (result.has("maxExecutionUnitsPerBlock") && result.get("maxExecutionUnitsPerBlock").has("steps")) {
            protocolParams.setMaxBlockExSteps(result.get("maxExecutionUnitsPerBlock").get("steps").asText());
        }

        if (result.has("maxValueSize")) {
            protocolParams.setMaxValSize(result.get("maxValueSize").asText());
        }

        if (result.has("collateralPercentage")) {
            protocolParams.setCollateralPercent(result.get("collateralPercentage").decimalValue());
        }

        if (result.has("maxCollateralInputs")) {
            protocolParams.setMaxCollateralInputs(result.get("maxCollateralInputs").asInt());
        }


        return protocolParams;
    }

    /**
     * Convert a string to BigDecimal. String format :  a/b
     * @param str
     * @return BigDecimal
     */
    private static BigDecimal stringToDecimal(String str) {
        try {
            String[] parts = str.split("/");
            double d = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);

            return new BigDecimal(d);
        } catch (Exception e) {
            log.error("Error in conversion", e);
            log.error("Error converting {} to BigDecimal", str);
            return BigDecimal.ZERO;
        }
    }
}
