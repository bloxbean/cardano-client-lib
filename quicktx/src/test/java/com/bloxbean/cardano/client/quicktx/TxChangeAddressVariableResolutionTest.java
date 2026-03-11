package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TxChangeAddressVariableResolutionTest {

    @Test
    void resolves_variable_for_change_address_in_tx() {
        String addrVar = "addr_test1vqarstuvw";

        String yaml = """
                version: '1.0'
                variables:
                  ca: %s
                transaction:
                  - tx:
                      change_address: ${ca}
                      intents: []
                """.formatted(addrVar);

        Tx tx = (Tx) TxPlan.getTxs(yaml).get(0);
        assertThat(tx.getPublicChangeAddress()).isEqualTo(addrVar);
    }
}

