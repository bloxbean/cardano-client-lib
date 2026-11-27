package com.bloxbean.cardano.client.plutus.spec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A decoded datum still represents its bytes, whichever way a map loses pairs at every level of nesting. */
class DatumRepresentsBytesDepthTest {
    private static final int DEPTH = 4000;

    static List<String> nestedLosses() {
        return List.of(
                // one Plutus key, a0 and bfff: the next level in the value kept, then in the value lost
                "a2a000bfff".repeat(DEPTH) + "00",
                "a2a0".repeat(DEPTH) + "00" + "bfff00".repeat(DEPTH),
                // a key the CBOR repeats: the next level in the value kept, then in the value lost
                "a2010001".repeat(DEPTH) + "00",
                "a201".repeat(DEPTH) + "00" + "0100".repeat(DEPTH),
                // both ways in turn
                "a2a000bfffa2010001".repeat(DEPTH / 2) + "00");
    }

    /** Lost keys inside lost keys: each level keeps one form of the key and loses the other, which holds the next. */
    private static String lostKeysWithin(int depth) {
        String kept = "a0";
        String lost = "bfff";
        for (int i = 0; i < depth; i++) {
            lost = "a2" + kept + "00" + lost + "00";
            kept = "a1" + kept + "00";
        }
        return "a2" + kept + "00" + lost + "00";
    }

    @org.junit.jupiter.api.Test
    void lostKeysThatHoldLostKeys() throws Exception {
        byte[] bytes = HexFormat.of().parseHex(lostKeysWithin(170));
        PlutusData decoded = PlutusData.deserialize(bytes);

        assertThat(PlutusData.representsBytes(decoded, bytes, PlutusData.mapsThatLostPairs(decoded))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("nestedLosses")
    void stillRepresentsItsBytes(String datum) throws Exception {
        byte[] bytes = HexFormat.of().parseHex(datum);
        PlutusData decoded = PlutusData.deserialize(bytes);

        assertThat(PlutusData.representsBytes(decoded, bytes, PlutusData.mapsThatLostPairs(decoded))).isTrue();
    }
}
