package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.crypto.bip39.DefaultEntropyProviderImpl;
import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;

public enum Configuration {
    INSTANCE();

    private SigningProvider signingProvider;
    private EntropyProvider entropyProvider;
    private PlutusObjectConverter plutusObjectConverter;

    Configuration() {
        signingProvider = new EdDSASigningProvider();
        entropyProvider = new DefaultEntropyProviderImpl();
        plutusObjectConverter = new DefaultPlutusObjectConverter();
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

    public PlutusObjectConverter getPlutusObjectConverter() {
        return plutusObjectConverter;
    }

    public void setPlutusObjectConverter(PlutusObjectConverter plutusObjectConverter) {
        this.plutusObjectConverter = plutusObjectConverter;
    }
}
