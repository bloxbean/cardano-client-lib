package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * DRep Delegator
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DRepDelegator {

    /**
     * Cardano staking address (reward account) in bech32 format
     */
    private String stakeAddress;

    /**
     * Cardano staking address (reward account) in hex format
     */
    private String stakeAddressHex;

    /**
     * Script hash in case the stake address is locked by a script (null if not applicable)
     */
    private String scriptHash;

    /**
     * Epoch when vote delegation was made
     */
    private Integer epochNo;

    /**
     * Total balance of the account including UTxO, rewards, and MIRs (in number)
     */
    private String amount;
}
