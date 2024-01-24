package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;

public enum Vote {
    NO(0), YES(1), ABSTAIN(2);

    private final int value;
    Vote(int value) {
        this.value = value;
    }

    public DataItem serialize() {
        return new UnsignedInteger(value);
    }

    /**
     * ; no - 0
     * ; yes - 1
     * ; abstain - 2
     * vote = 0 .. 2
     *
     * @param voteDI
     * @return
     */
    public static Vote deserialize(UnsignedInteger voteDI) {
        int vote = voteDI.getValue().intValue();
        switch (vote) {
            case 0:
                return Vote.NO;
            case 1:
                return Vote.YES;
            case 2:
                return Vote.ABSTAIN;
            default:
                throw new IllegalArgumentException("Invalid vote. Expected 0,1,2. Found : " + vote);
        }
    }
}
