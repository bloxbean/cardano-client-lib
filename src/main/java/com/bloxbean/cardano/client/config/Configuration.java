package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.api.impl.DefaultSigningProvider;
import com.bloxbean.cardano.client.crypto.bip39.DefaultEntropyProviderImpl;
import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;

public enum Configuration {
    INSTANCE();

    private SigningProvider signingProvider;
    private EntropyProvider entropyProvider;

    Configuration() {
        signingProvider = new DefaultSigningProvider();
        entropyProvider = new DefaultEntropyProviderImpl();
    }

    public SigningProvider getSigningProvider() {
        return signingProvider;
    }

    public void setSigningProvider(SigningProvider signingProvider) {
        this.signingProvider = signingProvider;
    }

    public EntropyProvider getEntropyProvider() {
        return entropyProvider;
    }

    public void setEntropyProvider(EntropyProvider entropyProvider) {
        this.entropyProvider = entropyProvider;
    }
}
