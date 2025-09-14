package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusV2Script;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptMintingIntentionTest {

    @Test
    void scriptMinting_serializes_to_yaml_with_redeemer_and_output_datum() {
        // Given
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        Asset asset = new Asset("newAsset", BigInteger.valueOf(1000));
        PlutusData redeemer = BigIntPlutusData.of(11);
        PlutusData outputDatum = BigIntPlutusData.of(42);

        ScriptTx scriptTx = new ScriptTx()
                .mintAsset(plutusScript, List.of(asset), redeemer, "addr_test1_receiver_mint", outputDatum)
                .payToAddress("addr_test1_some_other", Amount.ada(1));

        // When
        String yaml = scriptTx.toYaml();

        // Then
        assertThat(yaml).contains("type: script_minting");
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("cbor_hex:");
        assertThat(yaml).contains("redeemer_hex:");
        assertThat(yaml).contains("output_datum_hex:");
        assertThat(yaml).contains("receiver: addr_test1_receiver_mint");

        // Structure sanity
        Yaml parser = new Yaml();
        Map<String, Object> doc = parser.load(yaml);
        assertThat(doc).containsKey("transaction");
    }

    @Test
    void scriptMinting_round_trip_restores_intention() {
        // Given
        PlutusV2Script plutusScript = PlutusV2Script.builder()
                .type("PlutusScriptV2")
                .cborHex("49480100002221200101")
                .build();

        Asset asset = new Asset("xyz", BigInteger.valueOf(500));
        PlutusData redeemer = BigIntPlutusData.of(5);

        ScriptTx original = new ScriptTx()
                .mintAsset(plutusScript, asset, redeemer, "addr_test1_receiver_round");

        String yaml = original.toYaml();

        // When
        ScriptTx restored = AbstractTx.fromYaml(yaml, ScriptTx.class);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().stream()
                .anyMatch(i -> "script_minting".equals(i.getType()))).isTrue();
    }
}
