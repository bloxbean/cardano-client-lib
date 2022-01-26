package com.bloxbean.cardano.client.coinselection.exception;

import com.bloxbean.cardano.client.coinselection.exception.base.CoinSelectionException;

public class InputsLimitExceededException extends CoinSelectionException {

    public InputsLimitExceededException() {
        super("INPUT_LIMIT_EXCEEDED");
    }
}
