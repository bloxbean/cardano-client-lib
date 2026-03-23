package com.bloxbean.cardano.client.crypto.config;

import com.bloxbean.cardano.client.crypto.kes.KesSigner;
import com.bloxbean.cardano.client.crypto.kes.KesVerifier;
import com.bloxbean.cardano.client.crypto.kes.Sum6KesSigner;
import com.bloxbean.cardano.client.crypto.kes.Sum6KesVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfProver;
import com.bloxbean.cardano.client.crypto.vrf.VrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.bc.BcVrfProver;
import com.bloxbean.cardano.client.crypto.vrf.bc.BcVrfVerifier;

public enum CryptoExtConfiguration {
    INSTANCE;

    private VrfVerifier vrfVerifier;
    private VrfProver vrfProver;
    private KesVerifier kesVerifier;
    private KesSigner kesSigner;

    CryptoExtConfiguration() {
        vrfVerifier = new BcVrfVerifier();
        vrfProver = new BcVrfProver();
        kesVerifier = new Sum6KesVerifier();
        kesSigner = new Sum6KesSigner();
    }

    public VrfVerifier getVrfVerifier() {
        return vrfVerifier;
    }

    public void setVrfVerifier(VrfVerifier vrfVerifier) {
        this.vrfVerifier = vrfVerifier;
    }

    public VrfProver getVrfProver() {
        return vrfProver;
    }

    public void setVrfProver(VrfProver vrfProver) {
        this.vrfProver = vrfProver;
    }

    public KesVerifier getKesVerifier() {
        return kesVerifier;
    }

    public void setKesVerifier(KesVerifier kesVerifier) {
        this.kesVerifier = kesVerifier;
    }

    public KesSigner getKesSigner() {
        return kesSigner;
    }

    public void setKesSigner(KesSigner kesSigner) {
        this.kesSigner = kesSigner;
    }
}
