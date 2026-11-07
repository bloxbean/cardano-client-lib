package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Layouts are the pinned CDDL's. Every item differs, so a wrong position shows. */
class RawHeaderTest {
    static final long BLOCK_NUMBER = 293_707;
    static final long SLOT = 10_000_000;

    private static final String NUMBER = "1a" + "00047b4b";
    private static final String SLOT_ITEM = "1a" + "00989680";
    private static final String PREV = bytes32("aa");
    private static final String ISSUER = bytes32("bb");
    private static final String VRF_KEY = bytes32("cc");
    private static final String VRF_1 = "82" + "41" + "d1" + "41" + "d2";
    private static final String VRF_2 = "82" + "41" + "e1" + "41" + "e2";
    private static final String SIZE = "1904d2";
    private static final String BODY_HASH = bytes32("dd");
    private static final String HOT = bytes32("ee");
    private static final String SEQUENCE = "03";
    private static final String KES = "1864";
    private static final String SIGMA = "42" + "f1f2";
    private static final String MAJOR = "09";
    private static final String MINOR = "01";
    private static final String BODY_SIGNATURE = "42" + "f3f4";

    private static final String FLAT_ITEMS = NUMBER + SLOT_ITEM + PREV + ISSUER + VRF_KEY + VRF_1 + VRF_2 + SIZE
            + BODY_HASH + HOT + SEQUENCE + KES + SIGMA + MAJOR + MINOR;
    private static final String NESTED_ITEMS = NUMBER + SLOT_ITEM + PREV + ISSUER + VRF_KEY + VRF_1 + SIZE + BODY_HASH
            + "84" + HOT + SEQUENCE + KES + SIGMA + "82" + MAJOR + MINOR;

    private static final String EB_HASH = bytes32("ab");
    private static final String EB_ANNOUNCEMENT = "82" + EB_HASH + "1904d3";

    static final String FLAT_HEADER = "82" + "8f" + FLAT_ITEMS + BODY_SIGNATURE;
    static final String NESTED_HEADER = "82" + "8a" + NESTED_ITEMS + BODY_SIGNATURE;
    /** The 12-item Leios header of Dijkstra's CDDL: a Leios certificate in the body, an endorser block announced. */
    static final String LEIOS_HEADER = "82" + "8c" + NESTED_ITEMS + "f5" + EB_ANNOUNCEMENT + BODY_SIGNATURE;

    private static String bytes32(String value) {
        return "5820" + value.repeat(32);
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(decodeHexString(hex));
    }

    private static String hex(byte[] bytes) {
        return encodeHexString(bytes);
    }

    private static void assertCommonFields(RawHeader header) {
        assertThat(header.blockNumber()).isEqualTo(BLOCK_NUMBER);
        assertThat(header.slot()).isEqualTo(SLOT);
        assertThat(hex(header.prevHash().orElseThrow())).isEqualTo("aa".repeat(32));
        assertThat(hex(header.issuerVkey())).isEqualTo("bb".repeat(32));
        assertThat(hex(header.vrfVkey())).isEqualTo("cc".repeat(32));
        assertThat(header.bodySize()).isEqualTo(1234);
        assertThat(hex(header.bodyHash())).isEqualTo("dd".repeat(32));
        assertThat(hex(header.hotVkey())).isEqualTo("ee".repeat(32));
        assertThat(header.sequenceNumber()).isEqualTo(3);
        assertThat(header.kesPeriod()).isEqualTo(100);
        assertThat(hex(header.sigma())).isEqualTo("f1f2");
        assertThat(header.protocolMajor()).isEqualTo(9);
        assertThat(header.protocolMinor()).isEqualTo(1);
        assertThat(hex(header.bodySignature())).isEqualTo("f3f4");
    }

    private static void assertFlat(RawHeader header) {
        assertCommonFields(header);
        assertThat(hex(header.nonceVrf().orElseThrow().output())).isEqualTo("d1");
        assertThat(hex(header.nonceVrf().orElseThrow().proof())).isEqualTo("d2");
        assertThat(hex(header.leaderVrf().orElseThrow().output())).isEqualTo("e1");
        assertThat(hex(header.leaderVrf().orElseThrow().proof())).isEqualTo("e2");
        assertThat(header.vrfResult()).isEmpty();
    }

    private static void assertNested(RawHeader header) {
        assertCommonFields(header);
        assertThat(hex(header.vrfResult().orElseThrow().output())).isEqualTo("d1");
        assertThat(hex(header.vrfResult().orElseThrow().proof())).isEqualTo("d2");
        assertThat(header.nonceVrf()).isEmpty();
        assertThat(header.leaderVrf()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Era.class, names = {"Shelley", "Allegra", "Mary", "Alonzo"})
    void theFlatLayoutIsReadToAlonzo(Era era) {
        assertFlat(RawHeader.of(span(FLAT_HEADER), era));
        assertThatThrownBy(() -> RawHeader.of(span(NESTED_HEADER), era)).isInstanceOf(CborRuntimeException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Era.class, names = {"Babbage", "Conway"})
    void theNestedLayoutIsReadFromBabbage(Era era) {
        assertNested(RawHeader.of(span(NESTED_HEADER), era));
        assertThatThrownBy(() -> RawHeader.of(span(FLAT_HEADER), era)).isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D4a: Dijkstra has the 10-item header node 11.2.0 sends and the 12-item Leios header, by shape. */
    @Test
    void dijkstraReadsBothNestedLayouts() {
        RawHeader leios = RawHeader.of(span(LEIOS_HEADER), Era.Dijkstra);
        assertNested(leios);
        assertThat(leios.leiosCertInBody()).contains(true);
        RawEbReferencesAnnouncement announcement = leios.ebReferencesAnnouncement().orElseThrow();
        assertThat(hex(announcement.hash())).isEqualTo("ab".repeat(32));
        assertThat(announcement.size()).isEqualTo(1235);
        assertThat(leios.hash()).isEqualTo(Blake2bUtil.blake2bHash256(decodeHexString(LEIOS_HEADER)));
        assertThat(RawHeader.of(span(LEIOS_HEADER)).ebReferencesAnnouncement()).isPresent();

        RawHeader praos = RawHeader.of(span(NESTED_HEADER), Era.Dijkstra);
        assertNested(praos);
        assertThat(praos.leiosCertInBody()).isEmpty();
        assertThat(praos.ebReferencesAnnouncement()).isEmpty();
        assertThat(RawHeader.of(span(NESTED_HEADER)).leiosCertInBody()).isEmpty();

        RawHeader none = RawHeader.of(span(LEIOS_HEADER.replace("f5" + EB_ANNOUNCEMENT, "f4" + "f6")), Era.Dijkstra);
        assertThat(none.leiosCertInBody()).contains(false);
        assertThat(none.ebReferencesAnnouncement()).isEmpty();
        assertThatThrownBy(() -> RawHeader.of(span(FLAT_HEADER), Era.Dijkstra))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("10 or 12 items, found 15");

        RawHeader badFlag = RawHeader.of(span(LEIOS_HEADER.replace("f5" + EB_ANNOUNCEMENT, "00" + "00")));
        assertThat(badFlag.slot()).isEqualTo(SLOT);
        assertThatThrownBy(badFlag::leiosCertInBody).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(badFlag::ebReferencesAnnouncement).isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 I14: a 12-item header is refused where Conway is meant, as it was before D4a. */
    @Test
    void aTwelveItemHeaderIsRefusedAsConway() {
        for (Era era : new Era[]{Era.Babbage, Era.Conway}) {
            assertThatThrownBy(() -> RawHeader.of(span(LEIOS_HEADER), era))
                    .isInstanceOf(CborRuntimeException.class).hasMessageContaining("10 items, found 12");
        }
        assertThatThrownBy(() -> RawHeader.walk(span(LEIOS_HEADER), new RecordingPolicy()))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("15 or 10 items, found 12");
        assertThatThrownBy(() -> RawHeader.walk(span(LEIOS_HEADER), Era.Conway, new RecordingPolicy()))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aWalkOfADijkstraHeaderReadsEitherLayout() {
        RecordingPolicy leios = new RecordingPolicy();
        RawHeader.walk(span(LEIOS_HEADER), Era.Dijkstra, leios);
        assertThat(leios.events).noneMatch(event -> event.contains("missing") || event.contains(" null "));
        assertThat(leios.events).contains("check BOOL f5", "enter eb_references_announcement " + EB_ANNOUNCEMENT);

        RecordingPolicy praos = new RecordingPolicy();
        RawHeader.walk(span(NESTED_HEADER), Era.Dijkstra, praos);
        assertThat(praos.events).noneMatch(event -> event.contains("missing") || event.contains(" null "));
        assertThat(praos.events).anyMatch(event -> event.startsWith("enter protocol_version"));

        assertThatThrownBy(() -> RawHeader.walk(span(FLAT_HEADER), Era.Dijkstra, new RecordingPolicy()))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawHeader.walk(span(LEIOS_HEADER), null, new RecordingPolicy()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutAnEraTheLayoutComesFromTheShape() {
        assertFlat(RawHeader.of(span(FLAT_HEADER)));
        assertNested(RawHeader.of(span(NESTED_HEADER)));
    }

    @Test
    void anEraIsRequiredWhenNamed() {
        assertThatThrownBy(() -> RawHeader.of(span(NESTED_HEADER), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aBlockReadsItsHeaderWithItsEra() {
        byte[] block = decodeHexString("8206" + "85" + NESTED_HEADER + "80" + "80" + "a0" + "80");
        assertNested(RawBlock.of(block).rawHeader());
        byte[] flat = decodeHexString("8205" + "85" + FLAT_HEADER + "80" + "80" + "a0" + "80");
        assertFlat(RawBlock.of(flat).rawHeader());
    }

    @Test
    void theHashIsOfTheHeadersBytes() {
        for (String header : new String[]{FLAT_HEADER, NESTED_HEADER}) {
            byte[] expected = Blake2bUtil.blake2bHash256(decodeHexString(header));
            assertThat(RawHeader.of(span(header)).hash()).isEqualTo(expected);
            assertThat(hex(RawHeader.of(span(header)).span().bytes())).isEqualTo(header);
        }
        // the span of a block, so the hash does not take in what is around it
        CborSpan inBlock = BlockEnvelope.headerOf(decodeHexString("8207" + "85" + NESTED_HEADER + "fc"));
        assertThat(RawHeader.of(inBlock).hash()).isEqualTo(Blake2bUtil.blake2bHash256(decodeHexString(NESTED_HEADER)));
    }

    /** ADR 0003 D8's correction: the same values in another encoding are another hash, of the bytes received. */
    @Test
    void aNonMinimalEncodingReadsTheSameAndHashesItsOwnBytes() {
        String[] encodings = {
                NESTED_HEADER.replace(NUMBER, "1b" + "00000000" + "00047b4b"),
                NESTED_HEADER.replace(SLOT_ITEM, "1b" + "00000000" + "00989680"),
                NESTED_HEADER.replaceFirst("^828a", "82980a"),
                NESTED_HEADER.replace(PREV, "590020" + "aa".repeat(32))};
        for (String encoded : encodings) {
            RawHeader header = RawHeader.of(span(encoded), Era.Conway);
            assertNested(header);
            assertThat(header.hash()).isEqualTo(Blake2bUtil.blake2bHash256(decodeHexString(encoded)));
            assertThat(header.hash()).isNotEqualTo(Blake2bUtil.blake2bHash256(decodeHexString(NESTED_HEADER)));
        }
    }

    @Test
    void everyArrayMayBeIndefiniteAndEveryStringChunked() {
        String flat = "9f" + "9f" + NUMBER + SLOT_ITEM + PREV + ISSUER + VRF_KEY + "9f41d141d2ff" + "9f41e141e2ff"
                + SIZE + BODY_HASH + HOT + SEQUENCE + KES + SIGMA + MAJOR + MINOR + "ff" + BODY_SIGNATURE + "ff";
        assertFlat(RawHeader.of(span(flat), Era.Alonzo));
        assertFlat(RawHeader.of(span(flat)));

        String nested = "9f" + "9f" + NUMBER + SLOT_ITEM + PREV + ISSUER + VRF_KEY + "9f41d141d2ff" + SIZE + BODY_HASH
                + "9f" + HOT + SEQUENCE + KES + SIGMA + "ff" + "9f" + MAJOR + MINOR + "ff" + "ff"
                + BODY_SIGNATURE + "ff";
        assertNested(RawHeader.of(span(nested), Era.Babbage));
        assertNested(RawHeader.of(span(nested)));
        assertThat(RawHeader.of(span(nested)).hash()).isEqualTo(Blake2bUtil.blake2bHash256(decodeHexString(nested)));

        String chunked = NESTED_HEADER.replace(SIGMA, "5f41f141f2ff").replace(BODY_SIGNATURE, "5f42f3f4ff")
                .replace(PREV, "5f5810" + "aa".repeat(16) + "5810" + "aa".repeat(16) + "ff");
        assertNested(RawHeader.of(span(chunked)));
    }

    /** Numbers read as Yaci's models read them: a tag is looked through, and a bignum is a number (ADR 0003 D5). */
    @Test
    void numbersAreReadInAnyEncoding() {
        assertThat(blockNumberOf("c1" + NUMBER)).isEqualTo(BLOCK_NUMBER);
        assertThat(blockNumberOf("c2" + "43" + "047b4b")).isEqualTo(BLOCK_NUMBER);
        assertThat(blockNumberOf("1bffffffffffffffff")).isEqualTo(-1);
        assertThat(blockNumberOf("20")).isEqualTo(-1);
        assertThatThrownBy(() -> blockNumberOf("41" + "00")).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> blockNumberOf("f6")).isInstanceOf(CborRuntimeException.class);
    }

    private static long blockNumberOf(String number) {
        return RawHeader.of(span(NESTED_HEADER.replace(NUMBER, number))).blockNumber();
    }

    @Test
    void aNullPreviousHashIsEmpty() {
        for (String header : new String[]{FLAT_HEADER, NESTED_HEADER})
            assertThat(RawHeader.of(span(header.replace(PREV, "f6"))).prevHash()).isEmpty();
    }

    @Test
    void hashAndKeyLengthsAreNotChecked() {
        RawHeader header = RawHeader.of(span(NESTED_HEADER.replace(BODY_HASH, "41" + "00").replace(HOT, "40")));
        assertThat(header.bodyHash()).hasSize(1);
        assertThat(header.hotVkey()).isEmpty();
    }

    @Test
    void tagsOnTheHeaderAndItsItemsAreLookedThrough() {
        String tagged = "c1" + "82" + "c1" + "8a" + NESTED_ITEMS + "c1" + BODY_SIGNATURE;
        assertNested(RawHeader.of(span(tagged)));
        assertThat(RawHeader.of(span(NESTED_HEADER.replace(BODY_HASH, "c1" + BODY_HASH))).bodyHash()).hasSize(32);
        assertThat(RawHeader.of(span(NESTED_HEADER.replace("84" + HOT, "c184" + HOT))).sequenceNumber()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00", // not an array
            "a0",
            "80", // not two items
            "8180",
            "83" + "8a" + "00",
            "8200" + "40", // no header body array
            "82" + "a0" + "40",
            "82" + "80" + "40", // a body of no layout's size
            "82" + "8b" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "40",
            "82" + "90" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00" + "00"
                    + "00" + "00" + "00" + "00" + "00" + "00" + "40"})
    void aHeaderIsRejectedWhenItsArraysAreWrong(String hex) {
        assertThatThrownBy(() -> RawHeader.of(span(hex))).isInstanceOf(CborRuntimeException.class);
        for (Era era : Era.values())
            assertThatThrownBy(() -> RawHeader.of(span(hex), era)).isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D5: a view reads a field when asked, so a bad one fails only then. */
    @Test
    void aBadItemFailsOnlyWhenItIsRead() {
        RawHeader header = RawHeader.of(span(NESTED_HEADER.replace(VRF_1, "00")));
        assertThat(header.blockNumber()).isEqualTo(BLOCK_NUMBER);
        assertThatThrownBy(header::vrfResult).isInstanceOf(CborRuntimeException.class);

        RawHeader shortVrf = RawHeader.of(span(NESTED_HEADER.replace(VRF_1, "8141d1")));
        assertThatThrownBy(shortVrf::vrfResult).isInstanceOf(CborRuntimeException.class);

        RawHeader longVrf = RawHeader.of(span(NESTED_HEADER.replace(VRF_1, "8341d141d240")));
        assertThatThrownBy(longVrf::vrfResult).isInstanceOf(CborRuntimeException.class);

        RawHeader badCert = RawHeader.of(span(NESTED_HEADER.replace("84" + HOT, "83" + HOT).replace(SIGMA, "")));
        assertThat(badCert.slot()).isEqualTo(SLOT);
        assertThat(badCert.protocolMajor()).isEqualTo(9);
        assertThatThrownBy(badCert::hotVkey).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(badCert::sequenceNumber).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(badCert::kesPeriod).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(badCert::sigma).isInstanceOf(CborRuntimeException.class);

        RawHeader noVersion = RawHeader.of(span(NESTED_HEADER.replace("82" + MAJOR + MINOR, "00")));
        assertThat(noVersion.sequenceNumber()).isEqualTo(3);
        assertThatThrownBy(noVersion::protocolMajor).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(noVersion::protocolMinor).isInstanceOf(CborRuntimeException.class);

        RawHeader textKey = RawHeader.of(span(NESTED_HEADER.replace(ISSUER, "6161")));
        assertThatThrownBy(textKey::issuerVkey).isInstanceOf(CborRuntimeException.class);
        assertThat(textKey.bodySize()).isEqualTo(1234);
    }

    /** The tables nest as the views read them, so a walk reports every item of either layout as known. */
    @Test
    void theLayoutTablesWalkEveryItem() {
        RecordingPolicy flat = new RecordingPolicy();
        HeaderLayout.FLAT_15.body.walk(span("8f" + FLAT_ITEMS), Era.Shelley, flat);
        assertThat(flat.events).noneMatch(event -> event.contains("missing") || event.contains(" null "));
        assertThat(flat.events).anyMatch(event -> event.startsWith("enter vrf_cert"));

        RecordingPolicy nested = new RecordingPolicy();
        HeaderLayout.NESTED_10.body.walk(span("8a" + NESTED_ITEMS), Era.Conway, nested);
        assertThat(nested.events).noneMatch(event -> event.contains("missing") || event.contains(" null "));
        assertThat(nested.events).anyMatch(event -> event.startsWith("enter operational_cert"));
        assertThat(nested.events).anyMatch(event -> event.startsWith("enter protocol_version"));
        assertThat(nested.events).contains("check MINOR_PROTOCOL_VERSION " + MINOR);
    }

    /** With no era: the header's record, then its body in the layout it has, told of that layout's first era. */
    @Test
    void aWalkOfAHeaderReadsItsBodyInItsLayout() {
        RecordingPolicy flat = new RecordingPolicy();
        RawHeader.walk(span("82" + "8f" + FLAT_ITEMS + "40"), flat);
        assertThat(flat.events.get(0)).startsWith("enter header");
        assertThat(flat.events).anyMatch(event -> event.startsWith("enter header_body"));
        assertThat(flat.events).anyMatch(event -> event.startsWith("check KES_SIGNATURE"));
        assertThat(flat.events).anyMatch(event -> event.startsWith("check UINT32"));
        assertThat(flat.events).anyMatch(event -> event.startsWith("check VRF_PROOF"));

        RecordingPolicy nested = new RecordingPolicy();
        RawHeader.walk(span("82" + "8a" + NESTED_ITEMS + "40"), nested);
        assertThat(nested.events).anyMatch(event -> event.startsWith("enter operational_cert"));

        assertThatThrownBy(() -> RawHeader.walk(span("82" + "83000000" + "40"), new RecordingPolicy()))
                .isInstanceOf(CborRuntimeException.class);
    }
}
