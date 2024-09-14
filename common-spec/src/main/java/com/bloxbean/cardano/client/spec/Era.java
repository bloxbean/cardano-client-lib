package com.bloxbean.cardano.client.spec;

/**
 * List of Eras which can be used during transaction serialization
 */
public enum Era {
    Babbage(6),
    Conway(7);

    public final int value;
    Era(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
