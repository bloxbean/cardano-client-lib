package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep Epoch Summary
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRepEpochSummary {

    /**
     * Epoch number of the block
     */
    private Integer epochNo;

    /**
     * The total amount of voting power between all DReps including pre-defined roles for the epoch
     */
    private String amount;

    /**
     * The total number of DReps with vote power for the epoch
     */
    private Integer dreps;
}
