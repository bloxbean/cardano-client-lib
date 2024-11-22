package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

public class ScriptReceiver extends Receiver {
    protected PlutusData datum;

    public ScriptReceiver(String address, Amount amount, PlutusData datum) {
        super(address, amount);
        this.datum = datum;
    }

    public PlutusData getDatum() {
        return datum;
    }
}
