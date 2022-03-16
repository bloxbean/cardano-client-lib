package com.bloxbean.cardano.client.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Block {
    private long time;
    private long height;
    private String hash;
    private long slot;
    private Integer epoch;
    private Integer epochSlot;
    private String slotLeader;
    private Integer size;
    private Integer txCount;
    private String output;
    private String fees;
    private String blockVrf;
    private String previousBlock;
    private String nextBlock;
    private Integer confirmations;
}
