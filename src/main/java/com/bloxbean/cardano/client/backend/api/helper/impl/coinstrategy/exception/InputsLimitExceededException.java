package com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception;

import com.bloxbean.cardano.client.backend.api.helper.impl.coinstrategy.exception.base.CoinSelectionException;

public class InputsLimitExceededException extends CoinSelectionException {

    public InputsLimitExceededException() {
        super("INPUT_LIMIT_EXCEEDED");
    }
}
