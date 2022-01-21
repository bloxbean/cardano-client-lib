package com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception;

import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception.base.CoinSelectionException;

public class InputsExhaustedException extends CoinSelectionException {

    public InputsExhaustedException() {
        super("INPUTS_EXHAUSTED");
    }
}
