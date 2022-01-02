package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * PolicyAsset
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PolicyAsset {
    /**
     * Concatenation of the policy_id and hex-encoded asset_name
     */
    private String asset;
    /**
     * Current asset quantity
     */
    private String quantity;
}