package com.bloxbean.cardano.client.quicktx.blueprint.extender.common;

import com.bloxbean.cardano.client.api.model.Amount;

import java.util.List;

public class Receiver {
    protected String address;
    protected List<Amount> amounts;

    public Receiver(String address, Amount amount) {
        this.address = address;
        this.amounts = List.of(amount);
    }

    public Receiver(String address, List<Amount> amounts) {
        this.address = address;
        this.amounts = amounts;
    }

    public String getAddress() {
        return address;
    }

    public List<Amount> getAmounts() {
        return amounts;
    }
}
