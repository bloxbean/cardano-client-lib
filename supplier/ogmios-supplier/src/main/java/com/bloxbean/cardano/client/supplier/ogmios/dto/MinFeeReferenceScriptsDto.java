package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinFeeReferenceScriptsDto {
    private int range;
    private BigDecimal base;
    private BigDecimal multiplier;
}
