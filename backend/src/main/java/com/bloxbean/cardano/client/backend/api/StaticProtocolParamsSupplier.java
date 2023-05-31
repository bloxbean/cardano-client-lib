package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.model.ProtocolParams;

/**
 * Static provider, meaning it can be used for cases where <code>ProtocolParams</code> is provided from the
 * client, e.g. from java serialisation or manually created (pre-supplied).
 */
public class StaticProtocolParamsSupplier implements ProtocolParamsSupplier {

    private final ProtocolParams protocolParams;

    public StaticProtocolParamsSupplier(ProtocolParams protocolParams) {
        this.protocolParams = protocolParams;
    }

    @Override
    public ProtocolParams getProtocolParams() {
        return protocolParams;
    }

}
