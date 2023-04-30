package com.bloxbean.cardano.client.common;

import com.bloxbean.cardano.client.api.model.ProtocolParams;

@Deprecated
/**
 * @deprecated Use {@link com.bloxbean.cardano.client.api.MinAdaCalculator} instead
 */
public class MinAdaCalculator extends com.bloxbean.cardano.client.api.MinAdaCalculator {
    public MinAdaCalculator(ProtocolParams protocolParams) {
        super(protocolParams);
    }
}
