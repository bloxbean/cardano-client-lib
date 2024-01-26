package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidatorDto {
    private String purpose;
    private int index;
}
