package com.bloxbean.cardano.client.transaction.spec.cert;

public enum MirPot {
    RESERVES(0), TREASURY(1);

    int value;

    MirPot(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
