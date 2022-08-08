package com.bloxbean.cardano.client.backend.ogmios.model.base.iface;

import com.bloxbean.cardano.client.backend.ogmios.model.query.response.CurrentProtocolParameters;

public interface LocalStateQuery {

    /**
     * Get the current Protocol Parameters.
     * @return {@link CurrentProtocolParameters}
     */
    CurrentProtocolParameters currentProtocolParameters();

}
