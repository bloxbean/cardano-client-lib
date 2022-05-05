package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * AccountAsset
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccountAsset {

    /**
     * Concatenation of the policy_id and hex-encoded asset_name
     */
    private String unit;

    /**
     * The quantity of the unit
     */
    private String quantity;
}