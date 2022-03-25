package com.bloxbean.cardano.client.backend.model;

import com.bloxbean.cardano.client.transaction.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.RedeemerTag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvaluationResult {
    private RedeemerTag redeemerTag;
    private int index;
    private ExUnits exUnits;
}
