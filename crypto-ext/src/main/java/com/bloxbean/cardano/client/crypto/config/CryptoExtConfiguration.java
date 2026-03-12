package com.bloxbean.cardano.client.crypto.config;

import com.bloxbean.cardano.client.crypto.kes.KesVerifier;
import com.bloxbean.cardano.client.crypto.kes.Sum6KesVerifier;
import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfVerifier;

public enum CryptoExtConfiguration {
    INSTANCE;

    private VrfVerifier vrfVerifier;
    private KesVerifier kesVerifier;

    CryptoExtConfiguration() {
        vrfVerifier = new EcVrfVerifier();
        kesVerifier = new Sum6KesVerifier();
    }

    public VrfVerifier getVrfVerifier() {
        return vrfVerifier;
    }

    public void setVrfVerifier(VrfVerifier vrfVerifier) {
        this.vrfVerifier = vrfVerifier;
    }

    public KesVerifier getKesVerifier() {
        return kesVerifier;
    }

    public void setKesVerifier(KesVerifier kesVerifier) {
        this.kesVerifier = kesVerifier;
    }
}
