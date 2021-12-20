package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.api.impl.DefaultSigningProvider;

public enum Configuration {
    INSTANCE();

    private SigningProvider signingProvider;

    Configuration() {
        signingProvider = new DefaultSigningProvider();
    }

    public SigningProvider getSigningProvider() {
        return signingProvider;
    }

    public void setSigningProvider(SigningProvider signingProvider) {
        this.signingProvider = signingProvider;
    }
}
