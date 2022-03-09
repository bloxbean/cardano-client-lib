package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.coinselection.UtxoSelectionStrategy;
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
    private int coinSelectionLimit = 20;

    //Can be used to set isAndroid programmatically.
    private boolean isAndroid;

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

    public int getCoinSelectionLimit() {
        return coinSelectionLimit;
    }

    public void setCoinSelectionLimit(int coinSelectionLimit) {
        this.coinSelectionLimit = coinSelectionLimit;
    }

    public boolean isAndroid() {
        return isAndroid;
    }

    public void setAndroid(boolean android) {
        isAndroid = android;
    }
}
