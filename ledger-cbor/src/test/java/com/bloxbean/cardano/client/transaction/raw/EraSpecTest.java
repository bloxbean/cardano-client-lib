package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EraSpecTest {

    /** ADR 0003 I2: adding an {@link Era} constant without a row fails here. */
    @Test
    void everyEraHasARow() {
        for (Era era : Era.values())
            assertThat(EraSpec.of(era).era).isEqualTo(era);
    }

    /** ADR 0003 I1: the build keeps the models out of this module, so the reader cannot import one. */
    @Test
    void noModelIsOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("com.bloxbean.cardano.client.transaction.spec.Transaction"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.bloxbean.cardano.client.plutus.spec.PlutusData"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
