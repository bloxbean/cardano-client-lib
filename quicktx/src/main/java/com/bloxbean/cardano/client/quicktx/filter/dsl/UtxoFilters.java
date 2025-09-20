package com.bloxbean.cardano.client.quicktx.filter.dsl;

public final class UtxoFilters {
    private UtxoFilters() {}

    public static SimpleFilterBuilder simple() {
        return new SimpleFilterBuilder();
    }
}

