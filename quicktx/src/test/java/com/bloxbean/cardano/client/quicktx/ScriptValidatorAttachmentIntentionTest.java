package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptValidatorAttachmentIntentionTest {

    @Test
    void attach_spending_validator_serializes_to_yaml() {
        PlutusV2Script script = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        ScriptTx scriptTx = new ScriptTx()
                .attachSpendingValidator(script);

        String yaml = scriptTx.toYaml();
        assertThat(yaml).contains("type: validator");
        assertThat(yaml).contains("version: v2");
        assertThat(yaml).contains("cbor_hex:");
        assertThat(yaml).contains("role: spend");
    }
}
