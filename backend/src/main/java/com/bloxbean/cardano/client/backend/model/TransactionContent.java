package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TransactionContent {

    private String hash;
    private String block;
    private Long blockHeight;
    private Long blockTime;
    private Long slot;
    private Integer index;

    @Builder.Default
    private List<TxOutputAmount> outputAmount = new ArrayList<>();
    private String fees;
    private String deposit;
    private Integer size;
    private String invalidBefore;
    private String invalidHereafter;
    private Integer utxoCount;
    private Integer withdrawalCount;
    private Integer mirCertCount;
    private Integer delegationCount;
    private Integer stakeCertCount;
    private Integer poolUpdateCount;
    private Integer poolRetireCount;
    private Integer assetMintOrBurnCount;
    private Integer redeemerCount;
    private Boolean validContract;

}
