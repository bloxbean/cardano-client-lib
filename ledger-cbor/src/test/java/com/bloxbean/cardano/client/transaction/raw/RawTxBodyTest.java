package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawTxBodyTest {
    private static final String H28 = "581c" + "11".repeat(28);
    private static final String H32 = "5820" + "22".repeat(32);
    private static final String RA = "581d" + "e1" + "33".repeat(28);
    private static final String ADDR = "581d" + "61" + "44".repeat(28);
    private static final String INPUT = "82" + H32 + "05";
    private static final String MIN = "00" + "81" + INPUT + "01" + "80" + "02" + "1864";
    private static final String TAG24_DATUM = "d818" + "42" + "1845";
    private static final String TAG24_SCRIPT = "d818" + "46" + "82" + "02" + "43" + "010203";

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static String hex(byte[] bytes) {
        return HexUtil.encodeHexString(bytes);
    }

    private static byte[] tx(String body) {
        return HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6");
    }

    private static RawTxBody conway(String body) {
        return RawTx.of(tx(body)).body(Era.Conway);
    }

    /** Every accessor, for a test that reads them all. */
    static List<Runnable> accessors(RawTxBody body) {
        return List.of(body::inputs, () -> body.inputs().forEach(input -> {
            input.index();
            input.transactionId();
        }), body::outputs, () -> body.outputs().forEach(RawTxBodyTest::readOutput), body::fee, body::ttl,
                body::certificates, body::withdrawals, body::update, body::auxDataHash, body::validityStart, body::mint,
                body::scriptDataHash, body::collateral, body::requiredSigners, body::networkId,
                () -> body.collateralReturn().ifPresent(RawTxBodyTest::readOutput), body::totalCollateral,
                body::referenceInputs, body::votingProcedures, body::proposalProcedures, body::currentTreasury,
                body::donation, () -> GovernanceViewsTest.readAll(body));
    }

    private static void readOutput(RawOutput output) {
        output.addressBytes();
        output.amount().ifPresent(value -> {
            value.coin();
            value.multiAssets();
        });
        output.datumHash();
        output.inlineDatumBytes();
        output.scriptRefBytes();
    }

    @Test
    void readsEveryField() {
        String body = "b3" + "00" + "d90102" + "81" + INPUT + "01" + "80" + "02" + "1864" + "03" + "1a00010000"
                + "04" + "81" + "8200" + "8200" + H28 + "05" + "a1" + RA + "07" + "06" + "82" + "a0" + "00"
                + "07" + H32 + "08" + "01" + "09" + "a1" + H28 + "a1" + "41" + "aa" + "20" + "0b" + H32
                + "0d" + "81" + INPUT + "0e" + "81" + H28 + "0f" + "01" + "10" + "82" + ADDR + "02" + "11" + "03"
                + "12" + "81" + INPUT + "13" + "a0" + "14" + "80";
        RawTxBody read = conway(body);

        assertThat(read.era()).isEqualTo(Era.Conway);
        assertThat(read.span().bytes()).isEqualTo(HexUtil.decodeHexString(body));
        assertThat(read.inputs()).hasSize(1);
        assertThat(hex(read.inputs().get(0).transactionId())).isEqualTo("22".repeat(32));
        assertThat(read.inputs().get(0).index()).isEqualTo(5);
        assertThat(read.outputs()).isEmpty();
        assertThat(read.fee()).contains(BigInteger.valueOf(100));
        assertThat(read.ttl().getAsLong()).isEqualTo(65536);
        assertThat(read.certificates().get(0).kind()).isEqualTo(CertKind.STAKE_REGISTRATION);
        assertThat(hex(read.withdrawals().get(0).rewardAccount())).isEqualTo("e1" + "33".repeat(28));
        assertThat(read.withdrawals().get(0).coin()).isEqualTo(7);
        assertThat(read.update().orElseThrow().span().bytes()).isEqualTo(HexUtil.decodeHexString("82a000"));
        assertThat(hex(read.auxDataHash().orElseThrow())).isEqualTo("22".repeat(32));
        assertThat(read.validityStart().getAsLong()).isEqualTo(1);
        assertThat(hex(read.mint().get(0).policyId())).isEqualTo("11".repeat(28));
        assertThat(hex(read.mint().get(0).assets().get(0).name())).isEqualTo("aa");
        assertThat(read.mint().get(0).assets().get(0).quantity()).isEqualTo(-1);
        assertThat(hex(read.scriptDataHash().orElseThrow())).isEqualTo("22".repeat(32));
        assertThat(read.collateral().orElseThrow()).hasSize(1);
        assertThat(hex(read.requiredSigners().orElseThrow().get(0))).isEqualTo("11".repeat(28));
        assertThat(read.networkId().getAsInt()).isEqualTo(1);
        assertThat(read.collateralReturn().orElseThrow().amount().orElseThrow().coin()).isEqualTo(2);
        assertThat(read.totalCollateral()).contains(BigInteger.valueOf(3));
        assertThat(read.referenceInputs().orElseThrow()).hasSize(1);
        assertThat(read.votingProcedures()).isPresent();
        assertThat(read.proposalProcedures()).isPresent();
        assertThat(read.currentTreasury()).isEmpty();
        assertThat(read.donation()).isEmpty();
    }

    @Test
    void anAbsentFieldIsEmptyAndAnEmptySetIsNot() {
        RawTxBody read = conway("a4" + MIN + "0d" + "80");
        assertThat(read.ttl()).isEmpty();
        assertThat(read.certificates()).isEmpty();
        assertThat(read.withdrawals()).isEmpty();
        assertThat(read.mint()).isEmpty();
        assertThat(read.collateral().orElseThrow()).isEmpty();
        assertThat(read.referenceInputs()).isEmpty();
        assertThat(read.requiredSigners()).isEmpty();
        assertThat(read.networkId()).isEmpty();
    }

    @Test
    void inputsAndOutputsAreRequiredWhenRead() {
        RawTxBody read = RawTxBody.lenient(span("a1" + "0200"), Era.Conway);
        assertThatThrownBy(read::inputs).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(read::outputs).isInstanceOf(CborRuntimeException.class);
        assertThat(read.fee()).contains(BigInteger.ZERO);
    }

    @Test
    void aKeyOfAnotherTypeIsNotAByteStringKeyWithTheSameContent() {
        // a text key first, then a byte string key with its bytes: two keys, as a decoded map has them
        String text = "78" + RA.substring(2);
        RawTxBody body = RawTxBody.lenient(span("a4" + MIN + "05" + "a2" + text + "01" + RA + "02"), Era.Conway);
        assertThatThrownBy(body::withdrawals).isInstanceOf(CborRuntimeException.class);
        assertThat(RawItems.distinct(span("a2" + "00" + "01" + "40" + "02"), "map")).hasSize(2);
        assertThat(RawItems.distinct(span("a2" + "4161" + "01" + "5f4161ff" + "02"), "map")).hasSize(1);
    }

    @Test
    void anEraIsRequired() {
        assertThatThrownBy(() -> RawTx.of(tx("a3" + MIN)).body(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RawTxBody.lenient(span("a0"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aKeyThatChangesMeaningIsReadByItsEra() {
        FieldSpec signers = FieldSpec.of(14, FieldType.KEY_HASH_SET, false, Era.Alonzo, Era.Babbage);
        FieldSpec guards = FieldSpec.of(14, FieldType.NONEMPTY_SET, false, Era.Conway, null);
        RecordSpec table = RecordSpec.keyed("t", List.of(signers, guards));
        assertThat(table.replacedIn(signers, Era.Babbage)).isFalse();
        assertThat(table.replacedIn(signers, Era.Conway)).isTrue();
        // before a field, and after one that nothing replaces, it is only outside its era
        assertThat(table.replacedIn(guards, Era.Babbage)).isFalse();
        FieldSpec update = TxBodyField.UPDATE_PROPOSAL.spec();
        assertThat(TxBodyField.RECORD.replacedIn(update, Era.Conway)).isFalse();

        String body = "a4" + MIN + "0e" + "81" + H28;
        assertThat(conway(body).requiredSigners()).isPresent();
        assertThat(RawTx.of(tx(body)).body(Era.Shelley).requiredSigners()).isPresent();
    }

    @Test
    void keysAreComparedByValueAndTaggedKeysAreOtherKeys() {
        RawTxBody read = conway("a5" + MIN + "1807" + H32 + "c107" + H32);
        assertThat(read.auxDataHash()).isPresent();
        assertThat(conway("a4" + MIN + "c107" + H32).auxDataHash()).isEmpty();
        assertThat(conway("a4" + MIN + "dbffffffffffffffff07" + H32).auxDataHash()).isEmpty();
    }

    @Test
    void aRepeatedAssetOrAccountKeepsItsFirstPositionAndItsLastValue() {
        String policyA = "581c" + "aa".repeat(28);
        String policyB = "581c" + "bb".repeat(28);
        String mint = "a3" + policyA + "a2" + "4101" + "01" + "4102" + "02"
                + policyB + "a1" + "40" + "03"
                + "5f" + policyA + "ff" + "a2" + "4103" + "04" + "5f4101ff" + "05";
        List<RawMultiAsset> read = conway("a4" + MIN + "09" + mint).mint();
        assertThat(read).hasSize(2);
        assertThat(hex(read.get(0).policyId())).isEqualTo("aa".repeat(28));
        assertThat(read.get(0).assets()).extracting(RawMultiAsset.Asset::quantity)
                .containsExactly(BigInteger.valueOf(4), BigInteger.valueOf(5));
        assertThat(read.get(1).assets()).hasSize(1);

        String withdrawals = "a3" + RA + "01" + "c2" + RA + "02" + RA + "03";
        assertThat(conway("a4" + MIN + "05" + withdrawals).withdrawals()).extracting(RawWithdrawal::coin)
                .containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(2));
        assertThatThrownBy(() -> conway("a4" + MIN + "05" + "a1" + "00" + "00").withdrawals())
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void outputsInBothForms() {
        String legacy = "83" + ADDR + "01" + H32;
        String map = "a4" + "00" + ADDR + "01" + "82" + "01" + "a1" + H28 + "a1" + "40" + "02"
                + "02" + "82" + "01" + TAG24_DATUM + "03" + TAG24_SCRIPT;
        for (RawTxBody body : List.of(conway("a3" + "0080" + "01" + "82" + legacy + map + "0200"),
                RawTxBody.lenient(span("a3" + "0080" + "01" + "82" + legacy + map + "0200"), Era.Conway))) {
            RawOutput a = body.outputs().get(0);
            assertThat(hex(a.addressBytes().orElseThrow())).isEqualTo("61" + "44".repeat(28));
            assertThat(a.amount().orElseThrow().coin()).isEqualTo(1);
            assertThat(a.amount().orElseThrow().multiAssets()).isEmpty();
            assertThat(hex(a.datumHash().orElseThrow())).isEqualTo("22".repeat(32));
            assertThat(a.inlineDatumBytes()).isEmpty();
            assertThat(a.scriptRefBytes()).isEmpty();

            RawOutput b = body.outputs().get(1);
            assertThat(b.amount().orElseThrow().multiAssets().get(0).assets().get(0).quantity()).isEqualTo(2);
            assertThat(b.datumHash()).isEmpty();
            assertThat(hex(b.inlineDatumBytes().orElseThrow())).isEqualTo("1845");
            assertThat(hex(b.inlineDatum().orElseThrow().span().bytes())).isEqualTo("1845");
            assertThat(hex(b.scriptRefBytes().orElseThrow())).isEqualTo("8202" + "43" + "010203");
            assertThat(b.scriptRef().orElseThrow().type()).isEqualTo(2);
        }
    }

    @Test
    void anOutputReadAsTheModelsReadOne() {
        // a map form without an address; a datum option of another kind; bytes with no tag 24
        RawOutput noAddress = RawOutput.lenient(span("a2" + "0100" + "02" + "82" + "05" + "6161"));
        assertThat(noAddress.addressBytes()).isEmpty();
        assertThat(noAddress.datumHash()).isEmpty();
        assertThat(noAddress.inlineDatumBytes()).isEmpty();
        assertThat(hex(RawOutput.lenient(span("a2" + "0100" + "02" + "82" + "01" + "41aa")).inlineDatumBytes()
                .orElseThrow())).isEqualTo("aa");
        assertThat(hex(RawOutput.lenient(span("a2" + "0100" + "03" + "41aa")).scriptRefBytes().orElseThrow()))
                .isEqualTo("aa");
        // a tagged kind is not the kind, and a tag on a hash is looked through
        assertThat(RawOutput.lenient(span("a2" + "0100" + "02" + "82" + "c100" + "41aa")).datumHash()).isEmpty();
        assertThat(hex(RawOutput.lenient(span("a2" + "0100" + "02" + "82" + "00" + "c141aa")).datumHash()
                .orElseThrow())).isEqualTo("aa");
        assertThat(hex(RawOutput.lenient(span("c1" + "83" + ADDR + "00" + "c141aa")).datumHash().orElseThrow()))
                .isEqualTo("aa");
        // no value
        RawOutput noValue = RawOutput.lenient(span("a1" + "00" + ADDR));
        assertThatThrownBy(noValue::amount).isInstanceOf(CborRuntimeException.class);
        // a value the models read as none
        assertThat(RawOutput.lenient(span("a1" + "01" + "6161")).amount()).isEmpty();
        // what never reads
        for (String bad : List.of("00", "6161", "81" + ADDR, "84" + ADDR + "000000", "a2" + "0100" + "0100"))
            assertThatThrownBy(() -> RawOutput.lenient(span(bad))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawOutput.lenient(span("a2" + "0100" + "02" + "81" + "00")).datumHash())
                .isInstanceOf(CborRuntimeException.class);
        // the map form repeats a key of any kind
        assertThatThrownBy(() -> RawOutput.lenient(span("a3" + "0100" + "6161" + "00" + "6161" + "01")))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void valuesInEveryForm() {
        assertThat(RawValue.of(span("05")).coin()).isEqualTo(5);
        assertThat(RawValue.of(span("c2" + "4101")).coin()).isEqualTo(1);
        assertThat(RawValue.of(span("82" + "05" + "a0")).coin()).isEqualTo(5);
        assertThat(RawValue.of(span("9f" + "05" + "a0" + "ff")).coin()).isEqualTo(5);
        for (String bad : List.of("6161", "81" + "00", "83" + "00" + "a0" + "00"))
            assertThatThrownBy(() -> RawValue.of(span(bad))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawValue.of(span("82" + "00" + "00")).multiAssets())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawValue.of(span("82" + "6161" + "a0")).coin())
                .isInstanceOf(CborRuntimeException.class);

        // as the models read them
        assertThat(RawValue.lenient(span("20")).orElseThrow().coin()).isEqualTo(-1);
        assertThat(RawValue.lenient(span("c2" + "4101")).orElseThrow().coin()).isEqualTo(1);
        assertThat(RawValue.lenient(span("c5" + "4101"))).isEmpty();
        for (String none : List.of("6161", "a0", "f6"))
            assertThat(RawValue.lenient(span(none))).isEmpty();
        assertThatThrownBy(() -> RawValue.lenient(span("4101"))).isInstanceOf(RuntimeException.class);
        assertThat(RawValue.lenient(span("80")).orElseThrow().coin()).isZero();
        assertThat(RawValue.lenient(span("83" + "01" + "a0" + "00")).orElseThrow().coin()).isZero();
        assertThat(RawValue.lenient(span("82" + "6161" + "a0")).orElseThrow().coin()).isNull();
        assertThatThrownBy(() -> RawValue.lenient(span("9f" + "01" + "a0" + "ff")))
                .isInstanceOf(CborRuntimeException.class);
        assertThat(RawValue.lenient(span("82" + "00" + "a1" + H28 + "a1" + "40" + "6161")).orElseThrow().multiAssets()
                .get(0).assets().get(0).quantity()).isNull();
    }

    @Test
    void readingATransactionAsTheModelsReadOne() {
        byte[] tagged = HexUtil.decodeHexString("c1" + "83" + "c1" + "a3" + MIN + "c1" + "a0" + "f6");
        RawTx tx = RawTx.lenient(tagged);
        assertThat(tx.body(Era.Conway).fee()).contains(BigInteger.valueOf(100));
        assertThat(tx.isValid()).isTrue();
        assertThat(tx.auxData()).isEmpty();
        assertThat(tx.witnessField(0)).isEmpty();

        assertThat(RawTx.lenient(tx("a3" + MIN)).isValid()).isTrue();
        assertThat(RawTx.lenient(HexUtil.decodeHexString("84" + "a3" + MIN + "a0" + "f4" + "f6")).isValid()).isFalse();
        // a repeated key of any kind, and what a tree does not decode, in the body and on the transaction
        for (String bad : List.of("a2" + "6161" + "00" + "6161" + "01", "a4" + MIN + "1864" + "d81e" + "00",
                "a4" + MIN + "1864" + "d826" + "00"))
            assertThatThrownBy(() -> RawTx.lenient(tx(bad))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.lenient(HexUtil.decodeHexString("d81e" + "83" + "a3" + MIN + "a0" + "f6")))
                .isInstanceOf(CborRuntimeException.class);
        assertThat(Optional.of(RawTx.lenient(HexUtil.decodeHexString("83" + "a3" + MIN + "d81e" + "a0" + "f6"))))
                .isPresent();
    }

    @Test
    void aWalkEntersTheTablesOfInputsOutputsValuesWithdrawalsAndMint() {
        String legacy = "82" + ADDR + "01";
        String map = "a2" + "00" + ADDR + "01" + "82" + "02" + "a1" + H28 + "a1" + "41aa" + "03";
        String body = "a5" + "00" + "81" + INPUT + "01" + "82" + legacy + map + "02" + "00"
                + "05" + "a1" + RA + "04" + "09" + "a1" + H28 + "a1" + "41bb" + "20";
        RecordingPolicy policy = new RecordingPolicy();
        TxBodyField.RECORD.walk(span(body), Era.Conway, policy);
        assertThat(policy.events).contains(
                "enter transaction_input " + INPUT,
                "check HASH32 " + H32,
                "check UINT16 05",
                "enter alonzo_transaction_output " + legacy,
                "check ADDRESS " + ADDR,
                "check VALUE 01",
                "check COIN 01",
                "enter babbage_transaction_output " + map,
                "enter value " + "8202a1" + H28 + "a141aa03",
                "check HASH28 " + H28,
                "check ASSET_NAME 41aa",
                "check POSITIVE_COIN 03",
                "check REWARD_ACCOUNT " + RA,
                "check COIN 04",
                "check ASSET_NAME 41bb",
                "check NONZERO_INT64 20");
        // the same keys with another era's rows
        RecordingPolicy babbage = new RecordingPolicy();
        TxBodyField.RECORD.walk(span(body), Era.Babbage, babbage);
        assertThat(babbage.events).contains("check UINT 03", "check INT64 20");
    }

    @Test
    void aWalkReportsAMapOfLikeEntriesAsEntriesOfOneField() {
        String withdrawals = "a2" + RA + "01" + "5f" + RA + "ff" + "02";
        RecordingPolicy policy = new RecordingPolicy();
        RawWithdrawal.RECORD.walk(span(withdrawals), Era.Conway, policy);
        assertThat(policy.events).containsExactly(
                "enter withdrawals " + withdrawals,
                "entry withdrawals 0:COIN key=" + RA + " value=01 occurrence=0",
                "check REWARD_ACCOUNT " + RA,
                "check COIN 01",
                "entry withdrawals 0:COIN key=5f" + RA + "ff value=02 occurrence=1",
                "check REWARD_ACCOUNT 5f" + RA + "ff",
                "check COIN 02");
    }

    @Test
    void aWalkCannotEnterWhatATableHasNoFormFor() {
        for (String bad : List.of("a3" + "0080" + "0181" + "00" + "0200", "a3" + "0080" + "0181" + "82" + ADDR + "6161"
                + "0200", "a4" + MIN + "05" + "00", "a4" + MIN + "09" + "80", "a4" + MIN + "0e" + "81" + "00"))
            assertThatThrownBy(() -> TxBodyField.RECORD.walk(span(bad), Era.Conway, FormatPolicy.ACCEPT_ALL))
                    .isInstanceOf(CborRuntimeException.class);
        // a short input and a long legacy output are the policy's to reject, as any positional record
        RecordingPolicy policy = new RecordingPolicy();
        TxBodyField.RECORD.walk(span("a3" + "00" + "81" + "81" + H32 + "01" + "81" + "84" + ADDR + "01" + H32 + "00"
                + "0200"), Era.Conway, policy);
        assertThat(policy.events).contains("missing transaction_input 1:UINT16",
                "entry alonzo_transaction_output null key=null value=00 occurrence=0");
    }

    @Test
    void aChainFollowerReadsTheLastValueOfARepeatedKey() {
        RawTxBody body = RawTxBody.tolerant(span("a5" + MIN + "02" + "05" + "1802" + "06"), Era.Conway);
        assertThat(body.fee()).contains(BigInteger.valueOf(6));
        // a tagged key is another key
        assertThat(RawTxBody.tolerant(span("a4" + MIN + "c102" + "05"), Era.Conway).fee())
                .contains(BigInteger.valueOf(100));
        assertThatThrownBy(() -> RawTxBody.tolerant(span("00"), Era.Conway)).isInstanceOf(CborRuntimeException.class);

        RawOutput output = RawOutput.tolerant(span("a3" + "00" + ADDR + "01" + "01" + "01" + "02"));
        assertThat(output.amount().orElseThrow().coin()).isEqualTo(BigInteger.TWO);
        RawTxBody outputs = RawTxBody.tolerant(span("a3" + "0080" + "0181" + "a3" + "00" + ADDR + "01" + "01" + "01"
                + "02" + "0200"), Era.Conway);
        assertThat(outputs.outputs().get(0).amount().orElseThrow().coin()).isEqualTo(BigInteger.TWO);
        assertThatThrownBy(() -> RawOutput.tolerant(span("00"))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aChainFollowerRejectsWhatDecodingATreeRejects() {
        assertThatThrownBy(() -> RawTxBody.tolerant(span("a4" + MIN + "18ff" + "d81e" + "00"), Era.Conway))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aChainFollowerReadsAValueArrayOfAtLeastTwoItemsInAnyForm() {
        for (String value : List.of("82" + "05" + "a0", "9f" + "05" + "a0" + "ff", "83" + "05" + "a0" + "00")) {
            RawValue read = RawValue.tolerant(span(value)).orElseThrow();
            assertThat(read.coin()).isEqualTo(BigInteger.valueOf(5));
            assertThat(read.multiAssets()).isEmpty();
        }
        for (String value : List.of("80", "81" + "05", "9f" + "05" + "ff"))
            assertThatThrownBy(() -> RawValue.tolerant(span(value))).isInstanceOf(CborRuntimeException.class);
        // an item at a position of a number that holds something else is null
        assertThat(RawValue.tolerant(span("82" + "6161" + "a0")).orElseThrow().coin()).isNull();
        // anything else is read as a lenient view reads it
        assertThat(RawValue.tolerant(span("05")).orElseThrow().coin()).isEqualTo(BigInteger.valueOf(5));
        assertThat(RawValue.tolerant(span("6161"))).isEmpty();
    }

    @Test
    void aChainFollowerReadsAnyWithdrawalAmount() {
        RawTxBody body = RawTxBody.tolerant(span("a4" + MIN + "05" + "a3" + RA + "20" + "41aa" + "c24101" + "41bb"
                + "6161"), Era.Conway);
        assertThat(body.withdrawals()).extracting(RawWithdrawal::coin)
                .containsExactly(BigInteger.valueOf(-1), BigInteger.ONE, null);
    }

    @Test
    void certificatesAsEncoded() {
        RawTxBody body = RawTxBody.tolerant(span("a4" + MIN + "04" + "d90102" + "81" + "8100"), Era.Conway);
        assertThat(body.certificateItems()).extracting(CborSpan::bytes)
                .containsExactly(HexUtil.decodeHexString("8100"));
        assertThat(RawTxBody.tolerant(span("a3" + MIN), Era.Conway).certificateItems()).isEmpty();
        assertThatThrownBy(() -> RawTxBody.tolerant(span("a4" + MIN + "04" + "00"), Era.Conway).certificateItems())
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aFieldHasItsKeyInTheTable() {
        assertThat(TxBodyField.REQUIRED_SIGNERS.key()).isEqualTo(14);
        assertThat(TxBodyField.INPUTS.spec().record().name()).isEqualTo("transaction_input");
        assertThat(TxOutputField.OUTPUT_SCRIPT_REF.key()).isEqualTo(3);
        assertThat(FieldType.TRANSACTION_OUTPUT.record().shape()).isEqualTo(RecordSpec.Shape.BY_MAJOR_TYPE);
        assertThat(RawWithdrawal.RECORD.keyType()).isEqualTo(FieldType.REWARD_ACCOUNT);
        assertThat(CertKind.RECORD.keyType()).isNull();
    }
}
