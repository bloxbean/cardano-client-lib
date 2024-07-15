package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PoolInfo {

    private String poolId;
    private String hex;
    private String vrfKey;
    private Integer blocksMinted;
    private String liveStake;
    private Double liveSaturation;
    private Integer liveDelegators;
    private String activeStake;
    private Double activeSize;
    private String declaredPledge;
    private String livePledge;
    private Double marginCost;
    private String fixedCost;
    private String rewardAccount;
    private List<String> owners;
}
