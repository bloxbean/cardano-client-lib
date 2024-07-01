package com.bloxbean.cardano.client.plutus.spec;

public enum Language {
    PLUTUS_V1(0), PLUTUS_V2(1), PLUTUS_V3(2);

    private int key;

    Language(int key) {
        this.key = key;
    }

    public int getKey() {
        return key;
    }
}
