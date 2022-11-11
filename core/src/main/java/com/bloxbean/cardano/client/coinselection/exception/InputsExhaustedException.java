package com.bloxbean.cardano.client.coinselection.exception;

import com.bloxbean.cardano.client.coinselection.exception.base.CoinSelectionException;

public class InputsExhaustedException extends CoinSelectionException {

    public InputsExhaustedException() {
        super("INPUTS_EXHAUSTED");
    }
}
