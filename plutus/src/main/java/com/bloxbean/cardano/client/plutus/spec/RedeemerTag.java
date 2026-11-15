package com.bloxbean.cardano.client.plutus.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

public enum RedeemerTag {
    @JsonProperty("spend")
    Spend(0),
    @JsonProperty("mint")
    Mint(1),
    @JsonProperty("cert")
    Cert(2),
    @JsonProperty("reward")
    Reward(3),
    @JsonProperty("voting")
    Voting(4),
    @JsonProperty("proposing")
    Proposing(5),
    /** Dijkstra's guarding purpose. Experimental: Dijkstra is not final; this may change in any release. */
    @JsonProperty("guarding")
    Guarding(6);

    public final int value;

    RedeemerTag(int value) {
        this.value = value;
    }

    /**
     * @param value a redeemer tag's number
     * @return the tag, or null if there is none with this number
     */
    public static RedeemerTag fromValue(int value) {
        return Arrays.stream(values()).filter(tag -> tag.value == value).findFirst().orElse(null);
    }

    public static RedeemerTag convert(String redeemerTag) {
        return Arrays.stream(values()).filter(value -> value.name().equalsIgnoreCase(redeemerTag)).findFirst().orElse(null);
    }
}
