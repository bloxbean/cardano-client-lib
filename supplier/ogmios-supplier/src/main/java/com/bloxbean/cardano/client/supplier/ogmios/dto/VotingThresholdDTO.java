package com.bloxbean.cardano.client.supplier.ogmios.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VotingThresholdDTO {

    private String noConfidence;
    private Map<String, String> constitutionalCommittee;
    private String hardForkInitiation;
    private Map<String, String> protocolParametersUpdate;
    private String treasuryWithdrawals;
    private String constitution;
}
