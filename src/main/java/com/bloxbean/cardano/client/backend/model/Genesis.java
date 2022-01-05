package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Genesis {
    private BigDecimal activeSlotsCoefficient;
    private Integer updateQuorum;
    private String maxLovelaceSupply;
    private Integer networkMagic;
    private Integer epochLength;
    private Integer systemStart;
    private Integer slotsPerKesPeriod;
    private Integer slotLength;
    private Integer maxKesEvolutions;
    private Integer securityParam;
}
