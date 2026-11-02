package com.bloxbean.cardano.client.common.cbor;

import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CborSpanTest {

    private static CborSpan span(String hex) {
        return CborSpan.of(decodeHexString(hex));
    }

    private static String hex(CborSpan span) {
        return encodeHexString(span.bytes());
    }

    @Test
    void scalarsKeepTheirEncoding() {
        CborSpan nonMinimal = span("1800");
        assertThat(nonMinimal.majorType()).isEqualTo(0);
        assertThat(nonMinimal.asLong()).isZero();
        assertThat(nonMinimal.headerLength()).isEqualTo(2);
        assertThat(hex(nonMinimal)).isEqualTo("1800");

        assertThat(span("3903e7").asLong()).isEqualTo(-1000);
        assertThat(span("3b7fffffffffffffff").asLong()).isEqualTo(Long.MIN_VALUE);
        assertThat(span("f5").asBoolean()).isTrue();
        assertThat(span("f4").asBoolean()).isFalse();
        assertThat(span("f6").isNull()).isTrue();
        assertThat(span("f5").isNull()).isFalse();
        assertThat(span("c1f6").isNull()).isFalse();
    }

    @Test
    void asLongRejectsOverflowAndOtherTypes() {
        assertThatThrownBy(() -> span("1b8000000000000000").asLong()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("3b8000000000000000").asLong()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("4100").asLong()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("c101").asLong()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("f6").asBoolean()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("c1f5").asBoolean()).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void asBigIntegerReadsIntegersAndBignums() {
        BigInteger twoTo64 = BigInteger.ONE.shiftLeft(64);
        assertThat(span("1bffffffffffffffff").asBigInteger()).isEqualTo(twoTo64.subtract(BigInteger.ONE));
        assertThat(span("3bffffffffffffffff").asBigInteger()).isEqualTo(twoTo64.negate());
        assertThat(span("c249010000000000000000").asBigInteger()).isEqualTo(twoTo64);
        assertThat(span("c349010000000000000000").asBigInteger()).isEqualTo(twoTo64.negate().subtract(BigInteger.ONE));
        // Tag 2 at a non-minimal width, chunked payload
        assertThat(span("d900025f4101480000000000000000ff").asBigInteger()).isEqualTo(twoTo64);
        assertThat(span("c240").asBigInteger()).isZero();
        assertThatThrownBy(() -> span("c401").asBigInteger()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("c201").asBigInteger()).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void stringsConcatenateChunks() {
        assertThat(span("43010203").byteString()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(span("5f4101420203ff").byteString()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(span("5fff").byteString()).isEmpty();
        assertThat(span("63616263").text()).isEqualTo("abc");
        assertThat(span("7f6161626263ff").text()).isEqualTo("abc");
        assertThat(span("5f4101420203ff").isIndefinite()).isTrue();
        assertThatThrownBy(() -> span("63616263").byteString()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("c143010203").byteString()).isInstanceOf(CborRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"d90102", "da00000102", "db0000000000000102"})
    void setTagIsReadFromItsArgumentAtAnyWidth(String tagHead) {
        CborSpan set = span(tagHead + "820102");
        assertThat(set.tag()).isEqualTo(258);
        assertThat(set.majorType()).isEqualTo(4);
        assertThat(set.headerLength()).isEqualTo(tagHead.length() / 2 + 1);

        CborSpan untagged = set.untagIf(258);
        assertThat(untagged.tag()).isEqualTo(-1);
        assertThat(untagged.items()).extracting(CborSpanTest::hex).containsExactly("01", "02");
        assertThat(set.untag().offset()).isEqualTo(untagged.offset());

        assertThat(set.untagIf(259)).isSameAs(set);
        assertThatThrownBy(set::items).isInstanceOf(CborRuntimeException.class).hasMessageContaining("tagged");
    }

    @Test
    void untaggedSetIsUnchangedByUntagIf() {
        CborSpan set = span("820102");
        assertThat(set.untagIf(258)).isSameAs(set);
        assertThat(set.tag()).isEqualTo(-1);
        assertThatThrownBy(set::untag).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void nestedTagsReportTheOutermost() {
        CborSpan tagged = span("c1d9010280");
        assertThat(tagged.tag()).isEqualTo(1);
        assertThat(tagged.majorType()).isEqualTo(4);
        assertThat(tagged.headerLength()).isEqualTo(5);
        assertThat(tagged.untag().tag()).isEqualTo(258);
        assertThat(tagged.untagIf(258)).isSameAs(tagged);
        assertThat(tagged.untag().untagIf(258).size()).isZero();
    }

    @Test
    void arraysOfAnyHeaderWidth() {
        CborSpan nonMinimal = span("980401020304");
        assertThat(nonMinimal.headerLength()).isEqualTo(2);
        assertThat(nonMinimal.size()).isEqualTo(4);
        assertThat(nonMinimal.get(3).asLong()).isEqualTo(4);
        assertThat(nonMinimal.isIndefinite()).isFalse();

        CborSpan wide = span("9a00000002" + "01" + "02");
        assertThat(wide.headerLength()).isEqualTo(5);
        assertThat(wide.items()).hasSize(2);

        CborSpan indefinite = span("9f010203ff");
        assertThat(indefinite.isIndefinite()).isTrue();
        assertThat(indefinite.headerLength()).isEqualTo(1);
        assertThat(indefinite.size()).isEqualTo(3);
        assertThat(indefinite.get(2).asLong()).isEqualTo(3);
        assertThatThrownBy(() -> indefinite.get(3)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> nonMinimal.get(4)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> nonMinimal.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);

        assertThat(span("80").items()).isEmpty();
        assertThat(span("9fff").items()).isEmpty();
        assertThat(span("9fff").size()).isZero();
    }

    @Test
    void mapEntriesKeepOrderAndDuplicates() {
        // {1: 0, 1: 1} and an indefinite map with keys out of order
        List<Map.Entry<CborSpan, CborSpan>> duplicates = span("a201000101").entries();
        assertThat(duplicates).hasSize(2);
        assertThat(hex(duplicates.get(0).getValue())).isEqualTo("00");
        assertThat(hex(duplicates.get(1).getValue())).isEqualTo("01");

        CborSpan indefinite = span("bf0201820102f5ff");
        assertThat(indefinite.size()).isEqualTo(2);
        assertThat(indefinite.entries()).extracting(e -> hex(e.getKey())).containsExactly("02", "820102");
        assertThat(span("a0").entries()).isEmpty();
        assertThat(span("bfff").entries()).isEmpty();
        assertThatThrownBy(() -> span("820102").entries()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("a0").items()).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void fieldReadsRecordsByUintKey() {
        // {0: h'aa', 1 (non-minimal): 2, "x": 3}, keys out of order
        CborSpan record = span("a3" + "1801" + "02" + "00" + "41aa" + "6178" + "03");
        assertThat(hex(record.field(0).orElseThrow())).isEqualTo("41aa");
        assertThat(record.field(1).orElseThrow().asLong()).isEqualTo(2);
        assertThat(record.field(5)).isEmpty();

        CborSpan indefinite = span("bf" + "07" + "f6" + "00" + "80" + "ff");
        assertThat(hex(indefinite.field(0).orElseThrow())).isEqualTo("80");
        assertThat(indefinite.field(7).orElseThrow().isNull()).isTrue();
    }

    @Test
    void fieldRejectsADuplicatedRecordKey() {
        // key 0 encoded minimally and non-minimally
        CborSpan record = span("a3" + "00" + "01" + "01" + "02" + "1800" + "03");
        assertThatThrownBy(() -> record.field(1))
                .isInstanceOf(CborRuntimeException.class)
                .hasMessageContaining("duplicate key 0");
        assertThatThrownBy(() -> record.field(0)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("d90102a0").field(0)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void embeddedDefiniteIsAWindowIntoTheSameBuffer() {
        byte[] buf = decodeHexString("d81843820102");
        CborSpan embedded = CborSpan.of(buf).embedded();
        assertThat(embedded.buffer()).isSameAs(buf);
        assertThat(embedded.offset()).isEqualTo(3);
        assertThat(hex(embedded)).isEqualTo("820102");
        assertThat(embedded.get(1).asLong()).isEqualTo(2);

        // tag 24 at a non-minimal width
        assertThat(hex(span("d9001843820102").embedded())).isEqualTo("820102");
    }

    @Test
    void embeddedChunkedIsConcatenated() {
        CborSpan embedded = span("d8185f4182420102ff").embedded();
        assertThat(hex(embedded)).isEqualTo("820102");
        assertThat(embedded.offset()).isZero();
    }

    @Test
    void embeddedRejectsWrongShapes() {
        assertThatThrownBy(() -> span("d81943820102").embedded()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("d81863820102").embedded()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> span("43820102").embedded()).isInstanceOf(CborRuntimeException.class);
        // payload is two items
        assertThatThrownBy(() -> span("d818420102").embedded()).isInstanceOf(CborRuntimeException.class);
        // payload truncated
        assertThatThrownBy(() -> span("d818428201").embedded()).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void replacingKeepsEveryOtherByte() {
        CborSpan array = span("9f0102d9010280ff");
        List<CborSpan> items = array.items();
        byte[] replaced = array.replacing(List.of(items.get(1), items.get(2)),
                List.of(decodeHexString("182a"), decodeHexString("d9010281f5")));
        assertThat(encodeHexString(replaced)).isEqualTo("9f01182ad9010281f5ff");

        assertThat(array.replacing(List.of(), List.of())).isEqualTo(array.bytes());
        assertThatThrownBy(() -> array.replacing(List.of(items.get(2), items.get(1)), List.of(new byte[1], new byte[1])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> array.replacing(List.of(span("01")), List.of(new byte[1])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spansAreSlicesOfTheInput() {
        // [ {0: [h'01'], 1: 9}, [_ 1, 2], #6.24(h'820102'), null ] with a non-minimal outer header
        byte[] buf = decodeHexString("9804" + "a200814101" + "0109" + "9f0102ff" + "d81843820102" + "f6");
        CborSpan tx = CborSpan.of(buf);
        List<CborSpan> items = tx.items();
        assertThat(items).hasSize(4);

        int pos = tx.offset() + tx.headerLength();
        for (CborSpan item : items) {
            assertThat(item.buffer()).isSameAs(buf);
            assertThat(item.offset()).isEqualTo(pos);
            pos += item.length();
        }
        assertThat(pos).isEqualTo(buf.length);
        assertThat(hex(items.get(0).field(0).orElseThrow().get(0))).isEqualTo("4101");
        assertThat(hex(items.get(2).embedded())).isEqualTo("820102");
    }

    @Test
    void ofWindowAndSkip() {
        byte[] buf = decodeHexString("ff820102ff");
        assertThat(CborSpan.skip(buf, 1, buf.length)).isEqualTo(4);
        CborSpan window = CborSpan.of(buf, 1, 3);
        assertThat(window.offset()).isEqualTo(1);
        assertThat(window.size()).isEqualTo(2);
        assertThatThrownBy(() -> CborSpan.of(buf, 1, 4)).isInstanceOf(CborRuntimeException.class)
                .hasMessageContaining("trailing bytes");
        assertThatThrownBy(() -> CborSpan.of(buf, 3, 5)).isInstanceOf(IndexOutOfBoundsException.class);

        CborSpan at = CborSpan.at(buf, 1);
        assertThat(at.offset()).isEqualTo(1);
        assertThat(at.length()).isEqualTo(3);
        assertThatThrownBy(() -> CborSpan.at(buf, 0)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> CborSpan.at(decodeHexString("8201"), 0)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void toStringShowsHex() {
        assertThat(span("820102").toString()).contains("820102").contains("length=3");
    }
}
