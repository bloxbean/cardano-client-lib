package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

import com.bloxbean.cardano.client.api.model.Amount;

public class PubKeyReceiver extends Receiver {
    public PubKeyReceiver(String address, Amount amount) {
        super(address, amount);
    }
}
