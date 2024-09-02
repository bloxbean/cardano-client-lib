package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

/**
 * Committee Member
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CommitteeMember {

    /**
     * Member authentication status (authorized, not_authorized, resigned)
     */
    private String status;

    private String ccHotd;

    private String ccColdId;

    /**
     * Committee member key hash from last valid hot key authorization certificate in hex format (null if not applicable)
     */
    private String ccHotHex;

    /**
     * Committee member cold key hash in hex format
     */
    private String ccColdHex;

    /**
     * Epoch number in which the committee member vote rights expire
     */
    private Integer expirationEpoch;

    /**
     * Flag which shows if this credential is a script hash (null if not applicable)
     */
    private Boolean ccHotHasScript;

    /**
     * Flag which shows if this credential is a script hash
     */
    private Boolean ccColdHasScript;
}