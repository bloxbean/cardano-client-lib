package com.bloxbean.cardano.client.config;

import com.bloxbean.cardano.client.coinselection.config.CoinselectionConfig;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.cardano.client.plutus.api.PlutusObjectConverter;
import com.bloxbean.cardano.client.plutus.impl.DefaultPlutusObjectConverter;
import com.bloxbean.cardano.client.util.OSUtil;

public enum Configuration {
    INSTANCE();

    private PlutusObjectConverter plutusObjectConverter;

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
        return CoinselectionConfig.INSTANCE.getCoinSelectionLimit();
    }

    public void setCoinSelectionLimit(int coinSelectionLimit) {
        CoinselectionConfig.INSTANCE.setCoinSelectionLimit(coinSelectionLimit);
    }

    public boolean isAndroid() {
        return OSUtil.isAndroid();
    }

    public void setAndroid(boolean android) {
        OSUtil.setAndroid(android);
    }
}
