package com.bloxbean.cardano.client.plutus.spec;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Getter
@Builder
public class CostModel {
    private Language language;
    private long[] costs;

    public CostModel(@NonNull Language language, @NonNull long[] costs) {
        this.language = language;
        this.costs = costs;
    }
}
