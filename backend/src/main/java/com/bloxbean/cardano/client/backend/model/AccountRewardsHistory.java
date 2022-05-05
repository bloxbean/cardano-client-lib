package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * AccountRewardsHistory
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccountRewardsHistory {

    /**
     * Epoch of the associated reward
     */
    private Integer epoch;

    /**
     * Rewards for given epoch in Lovelaces
     */
    private String amount;

    /**
     * Bech32 pool ID being delegated to
     */
    private String poolId;

    /**
     * Type of the reward
     */
    private String type;
}