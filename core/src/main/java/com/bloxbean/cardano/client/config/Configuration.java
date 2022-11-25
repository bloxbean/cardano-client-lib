package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;

public enum Configuration {
    INSTANCE();

    private PlutusObjectConverter plutusObjectConverter;
    private int coinSelectionLimit = 20;

    //Can be used to set isAndroid programmatically.
    private boolean isAndroid;

    Configuration() {
        plutusObjectConverter = new DefaultPlutusObjectConverter();
    }

    public SigningProvider getSigningProvider() {
        return CryptoConfiguration.INSTANCE.getSigningProvider();
    }

    public void setSigningProvider(SigningProvider signingProvider) {
        CryptoConfiguration.INSTANCE.setSigningProvider(signingProvider);
    }

    public EntropyProvider getEntropyProvider() {
        return CryptoConfiguration.INSTANCE.getEntropyProvider();
    }

    public void setEntropyProvider(EntropyProvider entropyProvider) {
        CryptoConfiguration.INSTANCE.setEntropyProvider(entropyProvider);
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
