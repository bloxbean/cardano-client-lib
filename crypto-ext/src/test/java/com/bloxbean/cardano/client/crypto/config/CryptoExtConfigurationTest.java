package com.bloxbean.cardano.client.crypto.config;

import com.bloxbean.cardano.client.crypto.kes.KesVerifier;
import com.bloxbean.cardano.client.crypto.kes.Sum6KesVerifier;
import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.bc.BcVrfVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoExtConfigurationTest {

    @AfterEach
    void resetDefaults() {
        CryptoExtConfiguration.INSTANCE.setVrfVerifier(new BcVrfVerifier());
        CryptoExtConfiguration.INSTANCE.setKesVerifier(new Sum6KesVerifier());
    }

    @Test
    void defaultVrfVerifier_shouldBeBcVrfVerifier() {
        assertThat(CryptoExtConfiguration.INSTANCE.getVrfVerifier())
                .isInstanceOf(BcVrfVerifier.class);
    }

    @Test
    void defaultKesVerifier_shouldBeSum6KesVerifier() {
        assertThat(CryptoExtConfiguration.INSTANCE.getKesVerifier())
                .isInstanceOf(Sum6KesVerifier.class);
    }

    @Test
    void setVrfVerifier_shouldReturnNewInstance() {
        EcVrfVerifier ecVerifier = new EcVrfVerifier();
        CryptoExtConfiguration.INSTANCE.setVrfVerifier(ecVerifier);

        assertThat(CryptoExtConfiguration.INSTANCE.getVrfVerifier())
                .isSameAs(ecVerifier);
    }

    @Test
    void setKesVerifier_shouldReturnNewInstance() {
        KesVerifier custom = (signature, message, publicKey, period) -> false;
        CryptoExtConfiguration.INSTANCE.setKesVerifier(custom);

        assertThat(CryptoExtConfiguration.INSTANCE.getKesVerifier())
                .isSameAs(custom);
    }
}
