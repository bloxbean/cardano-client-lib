package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 D4's three entry points, and what a damaged block does. */
class BlockEnvelopeTest {
    private static final String HEADER = RawHeaderTest.NESTED_HEADER;
    private static final String CONWAY_BLOCK = "85" + HEADER + "80" + "80" + "a0" + "80";
    private static final String SHELLEY_BLOCK = "84" + RawHeaderTest.FLAT_HEADER + "80" + "80" + "a0";

    private static byte[] bytes(String hex) {
        return decodeHexString(hex);
    }

    private static BlockEnvelope envelope(String hex) {
        return BlockEnvelope.of(bytes(hex));
    }

    private static String hex(CborSpan span) {
        return encodeHexString(span.bytes());
    }

    // ---- the envelope ----

    @ParameterizedTest
    @CsvSource({"00, 0", "01, 1", "02, 2", "07, 7", "08, 8", "09, 9", "1807, 7", "1b0000000000000007, 7", "1864, 100",
            "1b7fffffffffffffff, 9223372036854775807", "1bffffffffffffffff, -1"})
    void anyEraIndexIsAccepted(String index, long expected) {
        assertThat(envelope("82" + index + CONWAY_BLOCK).eraIndex()).isEqualTo(expected);
    }

    @Test
    void theBlockIsNotLookedInto() {
        assertThat(hex(envelope("8207" + CONWAY_BLOCK).block())).isEqualTo(CONWAY_BLOCK);
        assertThat(hex(envelope("8201" + "8300" + "80" + "a0").block())).isEqualTo("8300" + "80" + "a0"); // Byron
        assertThat(hex(envelope("8207" + "00").block())).isEqualTo("00");
    }

    @Test
    void anyArrayFormAndTag24AreAccepted() {
        String top = "8207" + CONWAY_BLOCK;
        assertThat(envelope("9f07" + CONWAY_BLOCK + "ff").eraIndex()).isEqualTo(7);
        assertThat(envelope("980207" + CONWAY_BLOCK).eraIndex()).isEqualTo(7);
        assertThat(hex(envelope(embedded(top)).block())).isEqualTo(CONWAY_BLOCK);
        assertThat(hex(BlockEnvelope.of(CborSpan.of(bytes(embedded(top)))).block())).isEqualTo(CONWAY_BLOCK);
        // a chunked tag 24, as RawBlock has always allowed
        assertThat(envelope("d8185f" + bytesHead(top) + top + "ff").eraIndex()).isEqualTo(7);
        assertThat(envelope("d90018" + embedded(top).substring(4)).eraIndex()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "00",
            "a0",
            "80", // not two items
            "8107",
            "830780" + "80",
            "8280" + "80", // the index is no unsigned integer
            "8220" + "80",
            "82f6" + "80",
            "82c107" + "80", // a tagged index or envelope
            "c1" + "820780",
            "d818" + "00", // tag 24 on no byte string, or on bytes that are not one item
            "d818" + "41" + "fc",
            "d818" + "42" + "0000",
            "d818" + "d818" + "43" + "820780",
            "8207", // malformed or with trailing bytes
            "820780" + "00",
            "8207fc"})
    void whatIsNoSuchEnvelopeIsRejected(String hex) {
        assertThatThrownBy(() -> envelope(hex)).isInstanceOf(CborRuntimeException.class)
                .isNotInstanceOf(UnsupportedEraException.class);
    }

    /** A sequence: the caller holds the first item's span, and the rest is the caller's to judge. */
    @Test
    void aSpanOfTheFirstItemIgnoresWhatFollows() {
        for (String trailing : new String[]{"00", "81", "fc"}) {
            byte[] sequence = bytes("8207" + CONWAY_BLOCK + trailing);
            BlockEnvelope envelope = BlockEnvelope.of(CborSpan.at(sequence, 0));
            assertThat(RawBlock.of(envelope).rawHeader().blockNumber()).isEqualTo(RawHeaderTest.BLOCK_NUMBER);
            assertThatThrownBy(() -> BlockEnvelope.of(sequence)).isInstanceOf(CborRuntimeException.class);
        }
    }

    // ---- reading a block ----

    @Test
    void rawBlockReadsAnEnvelopeOfAKnownEra() {
        RawBlock conway = RawBlock.of(envelope("8207" + CONWAY_BLOCK));
        assertThat(conway.era()).isEqualTo(Era.Conway);
        assertThat(hex(conway.header())).isEqualTo(HEADER);
        assertThat(conway.invalidTransactions()).isPresent();
        RawBlock shelley = RawBlock.of(envelope("8202" + SHELLEY_BLOCK));
        assertThat(shelley.era()).isEqualTo(Era.Shelley);
        assertThat(shelley.invalidTransactions()).isEmpty();
        assertThat(shelley.rawHeader().nonceVrf()).isPresent();
    }

    @ParameterizedTest
    @CsvSource({"00, 0", "01, 1", "08, 8", "09, 9", "1864, 100", "1bffffffffffffffff, -1"})
    void anEraWithoutARowIsRejectedAsUnsupported(String index, long expected) {
        byte[] block = bytes("82" + index + CONWAY_BLOCK);
        assertThatThrownBy(() -> RawBlock.of(BlockEnvelope.of(block)))
                .isInstanceOf(CborRuntimeException.class)
                .isInstanceOfSatisfying(UnsupportedEraException.class,
                        e -> assertThat(e.eraIndex()).isEqualTo(expected));
        assertThatThrownBy(() -> RawBlock.of(block)).isInstanceOf(UnsupportedEraException.class);
    }

    @Test
    void readAsReadsWhateverTheIndexCarries() {
        for (String index : new String[]{"00", "02", "07", "08", "09", "1bffffffffffffffff"}) {
            BlockEnvelope envelope = envelope("82" + index + CONWAY_BLOCK);
            RawBlock block = RawBlock.readAs(envelope, EraSpec.newest());
            assertThat(block.era()).isEqualTo(Era.Conway);
            assertThat(hex(block.header())).isEqualTo(HEADER);
            assertThat(block.rawHeader().vrfResult()).isPresent();
        }
        assertThat(envelope("8209" + CONWAY_BLOCK).eraIndex()).isEqualTo(9);
        assertThat(RawBlock.readAs(envelope("8207" + SHELLEY_BLOCK), Era.Mary).era()).isEqualTo(Era.Mary);
        assertThatThrownBy(() -> RawBlock.readAs(envelope("8207" + CONWAY_BLOCK), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** D4 and D8: a block that does not fit the named era's layouts is a parse failure, not an unsupported era. */
    @Test
    void readAsRejectsWhatDoesNotFitTheLayout() {
        String[] others = {"8209" + "82" + HEADER + "80", // Dijkstra's two items
                "8209" + SHELLEY_BLOCK, // an older layout than the newest
                "8209" + "86" + CONWAY_BLOCK.substring(2) + "80",
                "8209" + "00"};
        for (String other : others) {
            assertThatThrownBy(() -> RawBlock.readAs(envelope(other), EraSpec.newest()))
                    .isInstanceOf(CborRuntimeException.class).isNotInstanceOf(UnsupportedEraException.class);
        }
        assertThatThrownBy(() -> RawBlock.of(envelope("8207" + SHELLEY_BLOCK)))
                .isInstanceOf(CborRuntimeException.class).isNotInstanceOf(UnsupportedEraException.class);
        assertThatThrownBy(() -> RawBlock.readAs(envelope("8209" + CONWAY_BLOCK), Era.Shelley))
                .isInstanceOf(CborRuntimeException.class);
    }

    /** A mismatched count and a bad index are rejected whichever entry point reads the block (ADR 0001 D6). */
    @Test
    void theBlockRulesHoldOnEveryEntryPoint() {
        String[] broken = {"85" + HEADER + "80" + "81a0" + "a0" + "80", "85" + HEADER + "80" + "80" + "a0" + "8100"};
        for (String block : broken) {
            assertThatThrownBy(() -> RawBlock.of(bytes("8207" + block))).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> RawBlock.of(envelope("8207" + block))).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> RawBlock.readAs(envelope("8209" + block), Era.Conway))
                    .isInstanceOf(CborRuntimeException.class);
        }
    }

    /** {@code RawBlock.of(bytes)} keeps its checks: it still reads the three forms and nothing else. */
    @Test
    void ofBytesIsUnchanged() {
        String top = "8207" + CONWAY_BLOCK;
        assertThat(RawBlock.of(bytes(top)).era()).isEqualTo(Era.Conway);
        assertThat(RawBlock.of(bytes(embedded(top))).era()).isEqualTo(Era.Conway);
        assertThat(RawBlock.of(bytes(CONWAY_BLOCK), Era.Conway).era()).isEqualTo(Era.Conway);
        assertThat(RawBlock.of(bytes(embedded(CONWAY_BLOCK)), Era.Conway).era()).isEqualTo(Era.Conway);
        assertThat(RawBlock.of(bytes(top), Era.Conway).era()).isEqualTo(Era.Conway);
        for (String rejected : new String[]{CONWAY_BLOCK, top + "00", "82c107" + CONWAY_BLOCK, "c1" + top}) {
            assertThatThrownBy(() -> RawBlock.of(bytes(rejected))).as(rejected)
                    .isInstanceOf(CborRuntimeException.class);
        }
        assertThatThrownBy(() -> RawBlock.of(bytes(top), Era.Babbage)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawBlock.of(bytes("82c107" + CONWAY_BLOCK), Era.Conway))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawBlock.of(bytes("8201" + CONWAY_BLOCK), Era.Conway))
                .isInstanceOf(UnsupportedEraException.class).hasMessageContaining("Byron");
        assertThatThrownBy(() -> RawBlock.of(bytes("8208" + CONWAY_BLOCK))).hasMessageContaining("Dijkstra");
        assertThatThrownBy(() -> RawBlock.of(bytes("82" + HEADER + "80"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Conway block has 5 items, found 2");
    }

    /** The view does not look into the header until asked, as it did not before. */
    @Test
    void aBlockReadsWithABadHeaderUntilTheHeaderIsAskedFor() {
        for (String header : new String[]{"00", "80", HEADER.replace("820000", "00"), RawHeaderTest.FLAT_HEADER}) {
            RawBlock block = RawBlock.of(bytes("8207" + "85" + header + "80" + "80" + "a0" + "80"));
            assertThat(block.txCount()).isZero();
            assertThat(hex(block.header())).isEqualTo(header);
        }
        RawBlock wrongLayout = RawBlock.of(bytes("8207" + "85" + RawHeaderTest.FLAT_HEADER + "8080a080"));
        assertThatThrownBy(wrongLayout::rawHeader).isInstanceOf(CborRuntimeException.class);
        RawBlock notAnArray = RawBlock.of(bytes("8207" + "85" + "00" + "80" + "80" + "a0" + "80"));
        assertThatThrownBy(notAnArray::rawHeader).isInstanceOf(CborRuntimeException.class);
    }

    // ---- a damaged block ----

    @Test
    void aDamagedBlockStillHasItsHeader() {
        String[] blocks = {
                "85" + HEADER + "fc", // a malformed item after the header
                "85" + HEADER + "80" + "81", // a truncated one
                "85" + HEADER, // items missing
                "85" + HEADER + "80" + "80" + "a0" + "80" + "fc", // malformed trailing bytes
                "85" + HEADER + "a0" + "a0" + "a0" + "a0", // well-formed, but wrong types
                "9f" + HEADER + "ff", // the wrong number of items
                "81" + HEADER,
                "9f" + HEADER + "80"};
        for (String block : blocks) {
            byte[] damaged = bytes("8207" + block);
            CborSpan header = BlockEnvelope.headerOf(damaged);
            assertThat(hex(header)).as(block).isEqualTo(HEADER);
            assertThat(RawHeader.of(header).blockNumber()).isEqualTo(RawHeaderTest.BLOCK_NUMBER);
            assertThat(RawHeader.of(header, Era.Conway).slot()).isEqualTo(RawHeaderTest.SLOT);
            assertThat(BlockEnvelope.eraIndexOf(damaged)).isEqualTo(7);
            assertThatThrownBy(() -> RawBlock.of(damaged)).as(block).isInstanceOf(CborRuntimeException.class);
        }
    }

    @Test
    void everyCutAfterTheHeaderStillHasTheHeaderAndEveryCutBeforeDoesNot() {
        byte[] whole = bytes("8207" + CONWAY_BLOCK);
        int headerEnd = 3 + bytes(HEADER).length;
        for (int length = 0; length < whole.length; length++) {
            byte[] cut = Arrays.copyOf(whole, length);
            assertThatThrownBy(() -> RawBlock.of(cut)).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> BlockEnvelope.of(cut)).isInstanceOf(CborRuntimeException.class);
            if (length >= headerEnd)
                assertThat(hex(BlockEnvelope.headerOf(cut))).as("cut at %d", length).isEqualTo(HEADER);
            else
                assertThatThrownBy(() -> BlockEnvelope.headerOf(cut)).as("cut at %d", length)
                        .isInstanceOf(CborRuntimeException.class);
            if (length >= 2)
                assertThat(BlockEnvelope.eraIndexOf(cut)).isEqualTo(7);
        }
    }

    /** The inputs of Yaci's test that a block without a readable header has no number. */
    @ParameterizedTest
    @ValueSource(strings = {"", "8207", "820700", "82078100", "8207818100"})
    void anUnreadableHeaderIsRejected(String hex) {
        assertThatThrownBy(() -> RawHeader.of(BlockEnvelope.headerOf(bytes(hex))))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void tagsAndAnyArrayFormAreLookedThroughOnTheWayToTheHeader() {
        assertThat(hex(BlockEnvelope.headerOf(bytes("c1" + "9f07" + "c1" + "9f" + HEADER + "fc"))))
                .isEqualTo(HEADER);
        assertThat(BlockEnvelope.eraIndexOf(bytes("c1" + "9802" + "c107"))).isEqualTo(7);
        assertThat(BlockEnvelope.eraIndexOf(bytes("82" + "1bffffffffffffffff"))).isEqualTo(-1);
    }

    @Test
    void tag24IsNotLookedIntoOnADamagedBlock() {
        assertThatThrownBy(() -> BlockEnvelope.headerOf(bytes(embedded("8207" + CONWAY_BLOCK))))
                .isInstanceOf(CborRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "80", "8180", "81f6", "8120", "81c280", "00"})
    void anEraIndexThatIsNoUnsignedIntegerIsRejected(String hex) {
        assertThatThrownBy(() -> BlockEnvelope.eraIndexOf(bytes(hex))).isInstanceOf(CborRuntimeException.class);
    }

    private static String embedded(String hex) {
        return "d818" + bytesHead(hex) + hex;
    }

    private static String bytesHead(String hex) {
        return String.format("5a%08x", hex.length() / 2);
    }
}
