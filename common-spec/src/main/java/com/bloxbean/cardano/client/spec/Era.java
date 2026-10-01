package com.bloxbean.cardano.client.spec;

/**
 * Shelley-family eras, with {@code value} equal to the era index of the hard-fork combinator (the first element of a
 * block's {@code [era, block]} envelope).
 * <p>
 * Transaction serialization distinguishes Conway from the eras before it, so Shelley to Alonzo serialize as Babbage
 * does. CCL's builders do not produce pre-Alonzo shapes; Shelley, Allegra, Mary and Alonzo are for decoding and
 * verifying, for example with {@code RawBlock} and {@code RawTx.scriptDataHash}. Byron (era 0 and 1) and later eras
 * have no constant.
 */
public enum Era {
    Shelley(2),
    Allegra(3),
    Mary(4),
    Alonzo(5),
    Babbage(6),
    Conway(7);

    public final int value;
    Era(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * @param hfcEraIndex the era index of the hard-fork combinator
     * @return the era
     * @throws IllegalArgumentException for Byron (0, 1) and any index without a constant
     */
    public static Era fromValue(int hfcEraIndex) {
        for (Era era : values()) {
            if (era.value == hfcEraIndex)
                return era;
        }
        if (hfcEraIndex == 0 || hfcEraIndex == 1)
            throw new IllegalArgumentException("Byron (era " + hfcEraIndex + ") is not supported");
        throw new IllegalArgumentException("Unknown era " + hfcEraIndex);
    }
}
