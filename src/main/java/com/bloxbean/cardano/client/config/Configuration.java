package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.api.impl.NativeSigningProvider;

public enum Configuration {
    INSTANCE();

    private boolean useNativeLibForAccountGen;
    private SigningProvider signingProvider;

    Configuration() {
        useNativeLibForAccountGen = true; //default value
        signingProvider = new NativeSigningProvider();
    }

    public boolean isUseNativeLibForAccountGen() {
        return useNativeLibForAccountGen;
    }

    public void setUseNativeLibForAccountGen(boolean useNativeLibForAccountGen) {
        this.useNativeLibForAccountGen = useNativeLibForAccountGen;
    }

    public SigningProvider getSigningProvider() {
        return signingProvider;
    }

    public void setSigningProvider(SigningProvider signingProvider) {
        this.signingProvider = signingProvider;
    }
}
