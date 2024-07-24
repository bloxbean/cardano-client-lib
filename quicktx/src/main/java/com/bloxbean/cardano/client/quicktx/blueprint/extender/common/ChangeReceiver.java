package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;

public class ChangeReceiver {
    private String address;
    private PlutusData datum;
    private boolean isScript;

    public ChangeReceiver(String address, PlutusData datum) {
        this.address = address;
        this.datum = datum;
        this.isScript = true;
    }

    public ChangeReceiver(String address) {
        this.address = address;
        this.isScript = false;
    }

    public String getAddress() {
        return address;
    }

    public PlutusData getDatum() {
        return datum;
    }

    public boolean isScript() {
        return isScript;
    }
}
