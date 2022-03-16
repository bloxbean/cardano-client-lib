package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.model.ProtocolParams;

@FunctionalInterface
public interface ProtocolParamsSupplier {
    ProtocolParams getProtocolParams();
}
