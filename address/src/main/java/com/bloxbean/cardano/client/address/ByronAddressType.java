package com.bloxbean.cardano.client.address;

/**
 * Type of a Byron address, i.e. the kind of spending data its root commits to.
 * Mirrors {@code AddrType} of the Byron ledger, which only accepts these two values.
 */
public enum ByronAddressType {
    /**
     * Address spendable by a verification key (wallet addresses)
     */
    VerKey(0),
    /**
     * Address spendable by a redeem key (AVVM / ADA voucher addresses)
     */
    Redeem(2);

    private final int value;

    ByronAddressType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    static ByronAddressType fromValue(long value) {
        for (ByronAddressType type : values()) {
            if (type.value == value)
                return type;
        }
        return null;
    }
}
