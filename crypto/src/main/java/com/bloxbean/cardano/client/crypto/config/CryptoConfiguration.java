package com.bloxbean.cardano.client.crypto.config;

import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.api.impl.BouncyCastleEd25519Verifier;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.crypto.bip39.DefaultEntropyProviderImpl;
import com.bloxbean.cardano.client.crypto.bip39.api.EntropyProvider;

import java.util.Objects;

public enum CryptoConfiguration {
    INSTANCE();

    private SigningProvider signingProvider;
    private Ed25519Verifier ed25519Verifier;
    private EntropyProvider entropyProvider;

    CryptoConfiguration() {
        signingProvider = new EdDSASigningProvider();
        ed25519Verifier = new BouncyCastleEd25519Verifier();
        entropyProvider = new DefaultEntropyProviderImpl();
    }

    public SigningProvider getSigningProvider() {
        return signingProvider;
    }

    public void setSigningProvider(SigningProvider signingProvider) {
        this.signingProvider = signingProvider;
    }

    /**
     * @return the {@link Ed25519Verifier} that {@link EdDSASigningProvider#verify} uses:
     *         {@link BouncyCastleEd25519Verifier} unless another was set
     */
    public Ed25519Verifier getEd25519Verifier() {
        return ed25519Verifier;
    }

    /**
     * Selects the Ed25519 verifier, for example
     * {@code CryptoConfiguration.INSTANCE.setEd25519Verifier(new I2pEd25519Verifier())} for the i2p-based one.
     * Both verifiers have the outcomes of a cardano-node.
     *
     * @param ed25519Verifier the verifier
     */
    public void setEd25519Verifier(Ed25519Verifier ed25519Verifier) {
        this.ed25519Verifier = Objects.requireNonNull(ed25519Verifier, "ed25519Verifier");
    }

    public EntropyProvider getEntropyProvider() {
        return entropyProvider;
    }

    public void setEntropyProvider(EntropyProvider entropyProvider) {
        this.entropyProvider = entropyProvider;
    }

}
