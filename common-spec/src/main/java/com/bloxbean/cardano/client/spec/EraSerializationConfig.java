package com.bloxbean.cardano.client.spec;

public enum EraSerializationConfig {
    INSTANCE();

    private Era era;

    EraSerializationConfig() {
        this.era = Era.Conway;
    }

    public Era getEra() {
        return era;
    }

}
