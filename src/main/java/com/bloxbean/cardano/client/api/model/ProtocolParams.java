package com.bloxbean.cardano.client.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProtocolParams {
    private Integer minFeeA;
    private Integer minFeeB;
    private Integer maxBlockSize;
    private Integer maxTxSize;
    private Integer maxBlockHeaderSize;
    private String keyDeposit;
    private String poolDeposit;
    @JsonProperty("e_max")
    private Integer eMax;
    @JsonProperty("n_opt")
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
    @Deprecated
    private String coinsPerUtxoWord;
}
