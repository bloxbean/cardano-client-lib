package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The walk's contract, ADR 0003 D6. */
class RecordWalkTest {
    private static final RecordSpec KEYED = RecordSpec.keyed("keyed", List.of(
            FieldSpec.of(0, FieldType.COIN, true, Era.Shelley, null),
            FieldSpec.of(1, FieldType.UINT, true, Era.Shelley, Era.Shelley),
            FieldSpec.of(1, FieldType.UINT, false, Era.Allegra, null),
            FieldSpec.of(2, FieldType.HASH28, false, Era.Alonzo, Era.Babbage),
            FieldSpec.of(2, FieldType.HASH32, false, Era.Conway, null),
            FieldSpec.of(3, FieldType.UINT, false, Era.Shelley, Era.Mary)));
    private static final RecordSpec POSITIONAL = RecordSpec.positional("positional", FieldType.UINT, FieldType.COIN);
    private static final RecordSpec TAGGED = RecordSpec.tagged("tagged", List.of(
            FieldSpec.member(0, Era.Shelley, Era.Mary, FieldType.UINT),
            FieldSpec.member(1, Era.Alonzo, null, FieldType.CREDENTIAL)));

    private static List<String> walk(RecordSpec record, String hex, Era era) {
        RecordingPolicy policy = new RecordingPolicy();
        record.walk(span(hex), era, policy);
        return policy.events;
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    @Test
    void enterSeesTheOriginalContainerAndEntriesComeInSourceOrder() {
        assertThat(walk(KEYED, "d81ebf01050007ff", Era.Conway)).containsExactly(
                "enter keyed d81ebf01050007ff",
                "entry keyed 1:UINT key=01 value=05 occurrence=0",
                "check UINT 05",
                "entry keyed 0:COIN key=00 value=07 occurrence=0",
                "check COIN 07");
    }

    @Test
    void repeatedKeysAreCountedByValueAndKeptAsEncoded() {
        assertThat(walk(KEYED, "a3000100021800" + "03", Era.Shelley)).containsExactly(
                "enter keyed a3000100021800" + "03",
                "entry keyed 0:COIN key=00 value=01 occurrence=0",
                "check COIN 01",
                "entry keyed 0:COIN key=00 value=02 occurrence=1",
                "check COIN 02",
                "entry keyed 0:COIN key=1800 value=03 occurrence=2",
                "check COIN 03",
                "missing keyed 1:UINT");
    }

    @Test
    void aByteStringIsCountedByItsContentInsideAnArrayKeyToo() {
        // "a", h'61', h'61' with a longer head, [0, h'61'], [0, h'61'] with a longer head, [0, "a"]
        String map = "a6" + "6161" + "01" + "4161" + "02" + "580161" + "03" + "82004161" + "04" + "8200580161" + "05"
                + "82006161" + "06";
        assertThat(walk(KEYED, map, Era.Conway)).filteredOn(event -> event.startsWith("entry")).containsExactly(
                "entry keyed null key=6161 value=01 occurrence=0",
                "entry keyed null key=4161 value=02 occurrence=0",
                "entry keyed null key=580161 value=03 occurrence=1",
                "entry keyed null key=82004161 value=04 occurrence=0",
                "entry keyed null key=8200580161 value=05 occurrence=1",
                "entry keyed null key=82006161 value=06 occurrence=0");
    }

    @Test
    void aKeyInNoTableOrNotAnIntegerHasANullField() {
        assertThat(walk(KEYED, "a8" + "0000" + "0a01" + "2002" + "6161" + "03" + "6161" + "04" + "c24100" + "05"
                        + "d81e00" + "06" + "1bffffffffffffffff" + "07", Era.Conway)).containsExactly(
                "enter keyed a8" + "0000" + "0a01" + "2002" + "6161" + "03" + "6161" + "04" + "c24100" + "05"
                        + "d81e00" + "06" + "1bffffffffffffffff" + "07",
                "entry keyed 0:COIN key=00 value=00 occurrence=0",
                "check COIN 00",
                "entry keyed null key=0a value=01 occurrence=0",
                "entry keyed null key=20 value=02 occurrence=0",
                "entry keyed null key=6161 value=03 occurrence=0",
                "entry keyed null key=6161 value=04 occurrence=1",
                "entry keyed null key=c24100 value=05 occurrence=0",
                "entry keyed null key=d81e00 value=06 occurrence=0",
                "entry keyed null key=1bffffffffffffffff value=07 occurrence=0");
    }

    @Test
    void aFieldOutsideItsEraIsReportedAsKnown() {
        List<FieldSpec> seen = new ArrayList<>();
        KEYED.walk(span("a20000" + "0301"), Era.Conway, new FormatPolicy() {
            @Override
            public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence,
                              Era era) {
                seen.add(field);
            }
        });
        assertThat(seen.get(1).key()).isEqualTo(3);
        assertThat(seen.get(1).validIn(Era.Conway)).isFalse();
        assertThat(seen.get(1).validIn(Era.Mary)).isTrue();
    }

    @Test
    void aKeyCanHaveARowPerMeaning() {
        String body = "a2000002" + "41aa";
        assertThat(walk(KEYED, body, Era.Babbage)).contains("entry keyed 2:HASH28 key=02 value=41aa occurrence=0");
        assertThat(walk(KEYED, body, Era.Conway)).contains("entry keyed 2:HASH32 key=02 value=41aa occurrence=0");
        // no row is valid in Shelley: the first row of the key is reported, and it says it is outside its era
        assertThat(walk(KEYED, body, Era.Shelley)).contains("entry keyed 2:HASH28 key=02 value=41aa occurrence=0");
    }

    @Test
    void missingComesAfterTheEntriesForEachFieldRequiredInThisEra() {
        assertThat(walk(KEYED, "a0", Era.Shelley)).containsExactly("enter keyed a0", "missing keyed 0:COIN",
                "missing keyed 1:UINT");
        assertThat(walk(KEYED, "a0", Era.Allegra)).containsExactly("enter keyed a0", "missing keyed 0:COIN");
        assertThat(walk(KEYED, "a10001", Era.Conway)).doesNotContain("missing keyed 0:COIN");
    }

    @Test
    void aShortPositionalRecordReportsMissing() {
        assertThat(walk(POSITIONAL, "8101", Era.Conway)).containsExactly(
                "enter positional 8101",
                "entry positional 0:UINT key=null value=01 occurrence=0",
                "check UINT 01",
                "missing positional 1:COIN");
    }

    @Test
    void aLongPositionalRecordReportsEntriesWithANullField() {
        assertThat(walk(POSITIONAL, "9f010203ff", Era.Conway)).containsExactly(
                "enter positional 9f010203ff",
                "entry positional 0:UINT key=null value=01 occurrence=0",
                "check UINT 01",
                "entry positional 1:COIN key=null value=02 occurrence=0",
                "check COIN 02",
                "entry positional null key=null value=03 occurrence=0");
    }

    @Test
    void aTaggedUnionHasItsTagAsTheKeyAndWalksTheMember() {
        assertThat(walk(TAGGED, "82180001", Era.Shelley)).containsExactly(
                "enter tagged 82180001",
                "entry tagged 0:MEMBER key=1800 value=82180001 occurrence=0",
                "check MEMBER 82180001",
                "enter member 0 82180001",
                "entry member 0 1:UINT key=null value=01 occurrence=0",
                "check UINT 01");
    }

    @Test
    void anUnknownTagHasANullFieldAndNothingElseIsRead() {
        assertThat(walk(TAGGED, "8209f6", Era.Shelley)).containsExactly(
                "enter tagged 8209f6",
                "entry tagged null key=09 value=8209f6 occurrence=0");
        assertThat(walk(TAGGED, "826161f6", Era.Shelley)).containsExactly(
                "enter tagged 826161f6",
                "entry tagged null key=6161 value=826161f6 occurrence=0");
    }

    @Test
    void aNestedRecordIsWalkedAndANullOptionalIsNot() {
        RecordSpec nullable = RecordSpec.positional("nullable", FieldType.OPTIONAL_ANCHOR);
        assertThat(walk(nullable, "81f6", Era.Conway)).containsExactly(
                "enter nullable 81f6",
                "entry nullable 0:OPTIONAL_ANCHOR key=null value=f6 occurrence=0",
                "check OPTIONAL_ANCHOR f6");
        assertThat(walk(nullable, "81826161" + "4100", Era.Conway)).containsExactly(
                "enter nullable 81826161" + "4100",
                "entry nullable 0:OPTIONAL_ANCHOR key=null value=826161" + "4100 occurrence=0",
                "check OPTIONAL_ANCHOR 826161" + "4100",
                "enter anchor 826161" + "4100",
                "entry anchor 0:URL key=null value=6161 occurrence=0",
                "check URL 6161",
                "entry anchor 1:HASH32 key=null value=4100 occurrence=0",
                "check HASH32 4100");
    }

    @Test
    void policyCanRejectByThrowing() {
        FormatPolicy rejectAll = new FormatPolicy() {
            @Override
            public void enter(RecordSpec record, CborSpan container, Era era) {
                throw new IllegalStateException("no");
            }
        };
        assertThatThrownBy(() -> KEYED.walk(span("a0"), Era.Conway, rejectAll))
                .isInstanceOf(IllegalStateException.class);
    }

    /** ADR 0003 D5, D6: what a policy accepts and the walk cannot enter, the reader rejects. */
    @Test
    void aWrongContainerIsRejectedEvenByAcceptAll() {
        assertThatThrownBy(() -> KEYED.walk(span("80"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> POSITIONAL.walk(span("a0"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> TAGGED.walk(span("00"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> TAGGED.walk(span("80"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        // a nested record
        assertThatThrownBy(() -> TAGGED.walk(span("820100"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void acceptAllLetsArityAndTagsPass() {
        POSITIONAL.walk(span("80"), Era.Conway, FormatPolicy.ACCEPT_ALL);
        POSITIONAL.walk(span("83000000"), Era.Conway, FormatPolicy.ACCEPT_ALL);
        KEYED.walk(span("d90102a0"), Era.Conway, FormatPolicy.ACCEPT_ALL);
    }
}
