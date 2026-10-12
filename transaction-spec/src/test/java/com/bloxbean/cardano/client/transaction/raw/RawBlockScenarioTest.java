package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The block-level scenarios of ADR 0001 sections 6.1 and 6.9 for {@link RawBlock}, on synthetic blocks: envelopes and
 * eras, alignment, the aux data map and invalid transactions (D6), and the block body hash against an independent
 * computation.
 */
class RawBlockScenarioTest {
    private static final String HEADER = "82" + "80" + "40"; // stands in for [header_body, signature]
    private static final String BODY_A = "a3008001800200";
    private static final String BODY_B = "a3008001800201";
    private static final String BODIES = "82" + BODY_A + BODY_B;
    private static final String WITNESSES = "82" + "a0" + "a0";

    // [header, bodies, witnesses, aux (, invalid)]
    private static String block(String aux, String invalid) {
        return (invalid == null ? "84" : "85") + HEADER + BODIES + WITNESSES + aux + (invalid == null ? "" : invalid);
    }

    private static String envelope(int era, String block) {
        return "82" + String.format("%02x", era) + block;
    }

    private static RawBlock of(String hex) {
        return RawBlock.of(decodeHexString(hex));
    }

    // blake2b256 of the concatenated blake2b256 of each part
    private static String bodyHash(String... parts) {
        ByteArrayOutputStream hashes = new ByteArrayOutputStream();
        for (String part : parts)
            hashes.writeBytes(blake2bHash256(decodeHexString(part)));
        return encodeHexString(blake2bHash256(hashes.toByteArray()));
    }

    @Test
    void alonzoOnBlocksHaveFourBodyParts() {
        RawBlock raw = of(envelope(7, block("a1" + "01" + "a0", "81" + "00")));
        assertThat(raw.era()).isEqualTo(Era.Conway);
        assertThat(raw.txCount()).isEqualTo(2);
        assertThat(encodeHexString(raw.bodyHash())).isEqualTo(bodyHash(BODIES, WITNESSES, "a1" + "01" + "a0", "8100"));
        assertThat(raw.invalidTxIndexes()).containsExactly(0);
        assertThat(raw.tx(0).isValid()).isFalse();
        assertThat(raw.tx(1).isValid()).isTrue();
        assertThat(raw.tx(0).auxData()).isEmpty();
        assertThat(raw.tx(1).auxData().orElseThrow().bytes()).isEqualTo(decodeHexString("a0"));
        assertThat(encodeHexString(raw.txBytes(0))).isEqualTo("84" + BODY_A + "a0" + "f4" + "f6");
        assertThat(encodeHexString(raw.txBytes(1))).isEqualTo("84" + BODY_B + "a0" + "f5" + "a0");
        assertThat(raw.header().bytes()).isEqualTo(decodeHexString(HEADER));
    }

    @Test
    void shelleyToMaryBlocksHaveThreeBodyPartsAndAllTxsAreValid() {
        for (int era = 2; era <= 4; era++) {
            RawBlock raw = of(envelope(era, block("a0", null)));
            assertThat(raw.era().value).isEqualTo(era);
            assertThat(encodeHexString(raw.bodyHash())).isEqualTo(bodyHash(BODIES, WITNESSES, "a0"));
            assertThat(raw.invalidTransactions()).isEmpty();
            assertThat(raw.invalidTxIndexes()).isEmpty();
            assertThat(raw.tx(0).isValid()).isTrue();
            assertThat(encodeHexString(raw.txBytes(0))).isEqualTo("83" + BODY_A + "a0" + "f6");
            int shelleyEra = era;
            assertThatThrownBy(() -> of(envelope(shelleyEra, block("a0", "80")))).isInstanceOf(CborRuntimeException.class);
        }
        assertThatThrownBy(() -> of(envelope(5, block("a0", null)))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void emptyInvalidTransactionsMeanAllValid() {
        RawBlock raw = of(envelope(6, block("a0", "80")));
        assertThat(raw.tx(0).isValid()).isTrue();
        assertThat(raw.tx(1).isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"820101", "820100", "8102", "8120", "81" + "c100"})
    void invalidTransactionsMustBeAscendingIndexes(String invalid) {
        assertThatThrownBy(() -> of(envelope(7, block("a0", invalid)))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void auxDataIndexes() {
        // out of range, or not an index
        assertThatThrownBy(() -> of(envelope(6, block("a1" + "02" + "a0", "80")))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> of(envelope(6, block("a1" + "6130" + "a0", "80")))).isInstanceOf(CborRuntimeException.class);

        // a repeated index: the last value wins until Babbage; the body hash covers the map as encoded
        String repeated = "a2" + "00" + "a10100" + "00" + "a10101";
        for (int era : new int[]{2, 5, 6}) {
            String invalid = era >= 5 ? "80" : null;
            RawBlock raw = of(envelope(era, block(repeated, invalid)));
            assertThat(raw.tx(0).auxData().orElseThrow().bytes()).isEqualTo(decodeHexString("a10101"));
            assertThat(encodeHexString(raw.bodyHash())).isEqualTo(era >= 5
                    ? bodyHash(BODIES, WITNESSES, repeated, "80") : bodyHash(BODIES, WITNESSES, repeated));
        }
        // ... and the block is rejected in Conway
        assertThatThrownBy(() -> of(envelope(7, block(repeated, "80"))))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("repeated");
    }

    @Test
    void txCountsMustAlign() {
        String block = "85" + HEADER + BODIES + "81a0" + "a0" + "80";
        assertThatThrownBy(() -> of(envelope(7, block))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void envelopesAndEras() {
        String block = block("a0", "80");
        byte[] bare = decodeHexString(block);
        RawBlock enveloped = of(envelope(6, block));
        assertThat(RawBlock.of(bare, Era.Babbage).bodyHash()).isEqualTo(enveloped.bodyHash());
        assertThat(RawBlock.of(RawBlockRealCorpusTest.embedded(decodeHexString(envelope(6, block)))).bodyHash())
                .isEqualTo(enveloped.bodyHash());
        assertThat(RawBlock.of(decodeHexString(envelope(6, block)), Era.Babbage).era()).isEqualTo(Era.Babbage);

        // a bare block needs its era; an envelope must agree with the era given
        assertThatThrownBy(() -> RawBlock.of(bare)).isInstanceOf(CborRuntimeException.class).hasMessageContaining("era");
        assertThatThrownBy(() -> RawBlock.of(decodeHexString(envelope(6, block)), Era.Conway))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Conway");
    }

    @Test
    void byronAndDijkstraAreNotSupported() {
        for (int era : new int[]{0, 1})
            assertThatThrownBy(() -> of(envelope(era, "83" + "80" + "80" + "80")))
                    .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Byron");
        assertThatThrownBy(() -> of(envelope(8, "82" + HEADER + "80")))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Dijkstra");
        assertThatThrownBy(() -> RawBlock.of(decodeHexString("82" + HEADER + "80"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Dijkstra");
        assertThatThrownBy(() -> of(envelope(9, block("a0", "80")))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> Era.fromValue(1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Byron");
        assertThat(Era.fromValue(5)).isEqualTo(Era.Alonzo);
    }

    @Test
    void aTransactionsRecordsAreCheckedWhenItIsRead() {
        String bodies = "81" + "a2" + "0080" + "0080"; // a body repeating key 0
        RawBlock raw = of(envelope(7, "85" + HEADER + bodies + "81a0" + "a0" + "80"));
        assertThatThrownBy(() -> raw.tx(0)).isInstanceOf(CborRuntimeException.class).hasMessageContaining("duplicate key");
    }
}
