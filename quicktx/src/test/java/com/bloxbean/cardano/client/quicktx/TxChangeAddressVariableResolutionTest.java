package com.bloxbean.cardano.client.quicktx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TxChangeAddressVariableResolutionTest {

    @Test
    void resolves_variable_for_change_address_in_tx() {
        String addrVar = "addr_test1vqarstuvw";

        String yaml = "version: '1.0'\n" +
                "variables:\n" +
                "  ca: " + addrVar + "\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      change_address: ${ca}\n" +
                "      intentions: []\n";

        Tx tx = AbstractTx.fromYaml(yaml, Tx.class);
        assertThat(tx.getPublicChangeAddress()).isEqualTo(addrVar);
    }
}

