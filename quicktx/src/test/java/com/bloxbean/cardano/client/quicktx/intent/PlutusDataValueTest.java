package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlutusDataValueTest {

    @Test
    void resolvesStructuredTemplateWithoutWholeYamlSubstitution() throws Exception {
        var node = YamlSerializer.getYamlMapper().readTree("""
                constructor: 0
                fields:
                  - bytes: ${owner_pkh}
                  - int: ${nonce}
                """);
        PlutusDataValue unresolved = PlutusDataValue.structured(node);

        PlutusDataValue resolved = unresolved.resolve(Map.of(
                "owner_pkh", "ab".repeat(28),
                "nonce", new BigInteger("922337203685477580812345")));

        assertThat(unresolved.isResolved()).isFalse();
        assertThat(resolved.requireResolved()).isEqualTo(ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString("ab".repeat(28))),
                BigIntPlutusData.of(new BigInteger("922337203685477580812345"))));
    }

    @Test
    void resolvesAndValidatesCborHexOnlyAfterVariableResolution() {
        PlutusData expected = ConstrPlutusData.of(0, ListPlutusData.of(BigIntPlutusData.of(7)));
        PlutusDataValue unresolved = PlutusDataValue.cborHex("${redeemer_cbor}");

        assertThat(unresolved.isResolved()).isFalse();
        assertThat(unresolved.resolve(Map.of("redeemer_cbor", expected.serializeToHex()))
                .requireResolved()).isEqualTo(expected);
        assertThatThrownBy(() -> unresolved.resolve(Map.of("redeemer_cbor", "not-hex")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to resolve Plutus data");
    }

    @Test
    void rejectsCompetingRepresentations() throws Exception {
        var structured = YamlSerializer.getYamlMapper().readTree("int: 1");
        PlutusDataValue first = PlutusDataValue.readStructured(
                null, structured, "transfer_redeemer");

        assertThatThrownBy(() -> PlutusDataValue.readCborHex(
                first, BigIntPlutusData.of(1).serializeToHex(), "transfer_redeemer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Exactly one of transfer_redeemer and transfer_redeemer_hex may be provided");
    }

    @Test
    void resolvedHexUsesCanonicalStructuredSerialization() {
        PlutusDataValue resolved = PlutusDataValue
                .cborHex(BigIntPlutusData.of(99).serializeToHex())
                .resolve(Map.of());

        assertThat(resolved.cborHexForYaml()).isNull();
        assertThat(resolved.structuredForYaml().path("int").bigIntegerValue())
                .isEqualTo(BigInteger.valueOf(99));
    }

    @Test
    void missingVariableFailsDuringResolution() throws Exception {
        var node = YamlSerializer.getYamlMapper().readTree("bytes: ${missing}");

        assertThatThrownBy(() -> PlutusDataValue.structured(node).resolve(Map.of(), "datum"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to resolve datum")
                .hasMessageContaining("Variable not found: missing");
    }
}
