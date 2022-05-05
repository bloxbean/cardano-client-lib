package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * AccountInformation
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccountInformation {

    /**
     * Registration state of an account
     */
    private Boolean active;

    /**
     * Balance of the account in Lovelaces
     */
    private String controlledAmount;

    /**
     * Sum of all rewards for the account in Lovelaces
     */
    private String rewardsSum;

    /**
     * Sum of all funds from reserves for the account in Lovelaces
     */
    private String reservesSum;

    /**
     * Sum of all the withdrawals for the account in Lovelaces
     */
    private String withdrawalsSum;

    /**
     * Sum of all funds from treasury for the account in Lovelaces
     */
    private String treasurySum;

    /**
     * Sum of available rewards that haven't been withdrawn yet for the account in Lovelaces
     */
    private String withdrawableAmount;

    /**
     * Bech32 pool ID that owns the account
     */
    private String pool_id;
}