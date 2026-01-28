package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptChangeDatumYamlTest {

    @Test
    void change_datum_round_trip_serializes_and_restores_inline_datum() {
        // Given
        String changeAddr = "addr_test1_change_inline";
        PlutusData datum = BigIntPlutusData.of(123);

        ScriptTx original = new ScriptTx()
                .withChangeAddress(changeAddr, datum);

        // When
        String yaml = TxPlan.from(original).toYaml();

        // Then: YAML contains change_datum and not change_datum_hash
        assertThat(yaml).contains("change_datum:");
        assertThat(yaml).contains("change_address: " + changeAddr);
        assertThat(yaml).doesNotContain("change_datum_hash:");

        // Round-trip
        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);

        // Verify restored state exposes inline datum hex and not hash
        String restoredDatumHex = restored.getChangeDatumHex();
        assertThat(restoredDatumHex).isNotNull();
        assertThat(restored.getChangeDatumHash()).isNull();
    }

    @Test
    void change_datum_hash_round_trip_serializes_and_restores_hash() {
        // Given
        String changeAddr = "addr_test1_change_hash";
        String datumHash = "9e1199a988ba72ffd6e9c269cadb3b25b8e4acff2e3dce4aef3793110255fc10";

        ScriptTx original = new ScriptTx()
                .withChangeAddress(changeAddr, datumHash);

        // When
        String yaml = TxPlan.from(original).toYaml();

        // Then: YAML contains change_datum_hash and not change_datum
        assertThat(yaml).contains("change_datum_hash: " + datumHash);
        assertThat(yaml).contains("change_address: " + changeAddr);
        assertThat(yaml).doesNotContain("change_datum:");

        // Round-trip
        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);

        // Verify restored state exposes hash and not inline datum
        assertThat(restored.getChangeDatumHash()).isEqualTo(datumHash);
        assertThat(restored.getChangeDatumHex()).isNull();
    }

    @Test
    void change_address_without_datum_round_trip() {
        // Given
        String changeAddr = "addr_test1_change_only";

        ScriptTx original = new ScriptTx()
                .withChangeAddress(changeAddr);

        // When
        String yaml = TxPlan.from(original).toYaml();

        // Then: YAML has change_address only
        assertThat(yaml).contains("change_address: " + changeAddr);
        assertThat(yaml).doesNotContain("change_datum:");
        assertThat(yaml).doesNotContain("change_datum_hash:");

        // Round-trip
        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);
        assertThat(restored.getPublicChangeAddress()).isEqualTo(changeAddr);
        assertThat(restored.getChangeDatumHex()).isNull();
        assertThat(restored.getChangeDatumHash()).isNull();
    }
}
