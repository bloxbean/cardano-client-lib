package com.bloxbean.cardano.client.plutus.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RedeemerTag {
    @JsonProperty("spend")
    Spend(0),
    @JsonProperty("mint")
    Mint(1),
    @JsonProperty("cert")
    Cert(2),
    @JsonProperty("reward")
    Reward(3);

    public final int value;

    RedeemerTag(int value) {
        this.value = value;
    }
}
