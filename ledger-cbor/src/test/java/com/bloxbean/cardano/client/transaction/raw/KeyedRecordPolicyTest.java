package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0003 I13, M1's keyed-record proof: the structural checks of Yano's strict reader ({@code RawTransaction.readBody}
 * and {@code readWitnessSet} at 22852109e) expressed as a policy over the walk, with no key number in it.
 */
class KeyedRecordPolicyTest {
    private static final String INPUTS = "0080";
    private static final String OUTPUTS = "0180";
    private static final String FEE = "0200";
    private static final String BODY = "a3" + INPUTS + OUTPUTS + FEE;

    /** What Yano's reader rejects before it reads a value. */
    private static final class StructuralPolicy implements FormatPolicy {
        @Override
        public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
            if (record.shape() != RecordSpec.Shape.KEYED)
                return;
            if (field == null || !field.validIn(era))
                throw new IllegalStateException("unknown key in " + record + ": " + key);
            if (occurrence > 0)
                throw new IllegalStateException("repeated key in " + record + ": " + key);
        }

        @Override
        public void missing(RecordSpec record, FieldSpec field, CborSpan container, Era era) {
            throw new IllegalStateException("missing field in " + record + ": " + field.type());
        }
    }

    private static byte[] tx(String body, String witnesses) {
        return HexUtil.decodeHexString("84" + body + witnesses + "f5" + "f6");
    }

    private static void walk(String body, String witnesses, Era era) {
        RawTx.walk(tx(body, witnesses), era, new StructuralPolicy());
    }

    private static void assertRejected(String message, String body, String witnesses, Era era) {
        assertThatThrownBy(() -> walk(body, witnesses, era)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    @Test
    void aBodyWithItsFieldsAndAnEmptyWitnessSetPass() {
        assertThatCode(() -> walk(BODY, "a0", Era.Conway)).doesNotThrowAnyException();
    }

    @Test
    void aBodyWithNoFeeIsRejected() {
        assertRejected("missing field in transaction_body: COIN", "a2" + INPUTS + OUTPUTS, "a0", Era.Conway);
    }

    @Test
    void anUnknownBodyKeyIsRejected() {
        assertRejected("unknown key in transaction_body", "a4" + INPUTS + OUTPUTS + FEE + "0a00", "a0", Era.Conway);
        assertRejected("unknown key in transaction_body", "a4" + INPUTS + OUTPUTS + FEE + "1817" + "00", "a0",
                Era.Conway);
        assertRejected("unknown key in transaction_body", "a4" + INPUTS + OUTPUTS + FEE + "2000", "a0", Era.Conway);
        assertRejected("unknown key in transaction_body", "a4" + INPUTS + OUTPUTS + FEE + "616100", "a0", Era.Conway);
    }

    @Test
    void anUnknownWitnessKeyIsRejected() {
        assertRejected("unknown key in transaction_witness_set", BODY, "a10880", Era.Conway);
    }

    @Test
    void aRepeatedKeyIsRejected() {
        assertRejected("repeated key in transaction_body", "a4" + INPUTS + OUTPUTS + FEE + "0201", "a0", Era.Conway);
        assertRejected("repeated key in transaction_witness_set", BODY, "a200800080", Era.Conway);
    }

    @Test
    void equalKeysEncodedDifferentlyAreRepeated() {
        assertRejected("repeated key in transaction_body", "a4" + INPUTS + OUTPUTS + "0200" + "180201", "a0",
                Era.Conway);
        assertRejected("repeated key in transaction_body", "a4" + INPUTS + OUTPUTS + "180200" + "190002" + "01", "a0",
                Era.Conway);
    }

    @Test
    void theTableDecidesWhatEachEraHas() {
        assertRejected("unknown key", "a4" + INPUTS + OUTPUTS + FEE + "06" + "82a000", "a0", Era.Conway);
        assertThatCode(() -> walk("a4" + INPUTS + OUTPUTS + FEE + "06" + "82a000", "a0", Era.Babbage))
                .doesNotThrowAnyException();

        assertRejected("unknown key", "a4" + INPUTS + OUTPUTS + FEE + "0e80", "a0", Era.Mary);
        for (Era era : new Era[]{Era.Alonzo, Era.Babbage, Era.Conway}) {
            assertThatCode(() -> walk("a4" + INPUTS + OUTPUTS + FEE + "0e80", "a0", era)).doesNotThrowAnyException();
        }

        assertRejected("unknown key", "a4" + INPUTS + OUTPUTS + FEE + "1600", "a0", Era.Babbage);
        assertRejected("unknown key", BODY, "a10780", Era.Babbage);
        assertRejected("unknown key", BODY, "a10680", Era.Alonzo);
        assertRejected("unknown key", BODY, "a10580", Era.Mary);
    }

    @Test
    void aTtlIsRequiredOnlyInShelley() {
        assertRejected("missing field in transaction_body: UINT", BODY, "a0", Era.Shelley);
        assertThatCode(() -> walk("a4" + INPUTS + OUTPUTS + FEE + "0300", "a0", Era.Shelley))
                .doesNotThrowAnyException();
        assertThatCode(() -> walk(BODY, "a0", Era.Allegra)).doesNotThrowAnyException();
    }

    /** The policy sees everything a view hides, and everything that a view rejects when it is created. */
    @Test
    void acceptAllSeesRepeatedAndUnknownKeysInSourceOrder() {
        byte[] tx = tx("a5" + "1802" + "00" + "02" + "01" + "0a" + "02" + INPUTS + OUTPUTS, "a0");
        assertThatThrownBy(() -> RawTx.of(tx)).isInstanceOf(CborRuntimeException.class);

        List<String> events = new ArrayList<>();
        RawTx.walk(tx, Era.Conway, new FormatPolicy() {
            @Override
            public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence,
                              Era era) {
                events.add(RecordingPolicy.hex(key) + ":" + (field != null ? field.type() : null) + ":" + occurrence);
            }
        });
        assertThat(events).containsExactly("1802:COIN:0", "02:COIN:1", "0a:null:0", "00:SET:0", "01:LIST:0");
    }

    @Test
    void aWalkGoesIntoTheCertificatesOfABody() {
        String cert = "82" + "00" + "8200" + "581c" + "11".repeat(28);
        RecordingPolicy conway = new RecordingPolicy();
        RawTx.walk(tx("a4" + INPUTS + OUTPUTS + FEE + "04" + "d9010281" + cert, "a0"), Era.Conway, conway);
        assertThat(conway.events).contains("check NONEMPTY_CERTIFICATE_SET d9010281" + cert,
                "enter certificate " + cert);

        RecordingPolicy babbage = new RecordingPolicy();
        RawTx.walk(tx("a4" + INPUTS + OUTPUTS + FEE + "04" + "81" + cert, "a0"), Era.Babbage, babbage);
        assertThat(babbage.events).contains("check CERTIFICATE_LIST 81" + cert);
    }

    @Test
    void aWalkRejectsWhatItCannotEnter() {
        assertThatThrownBy(() -> RawTx.walk(tx("80", "a0"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.walk(tx(BODY, "80"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.walk(tx("a4" + INPUTS + OUTPUTS + FEE + "0400", "a0"), Era.Conway,
                FormatPolicy.ACCEPT_ALL)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.walk(tx("a4" + INPUTS + OUTPUTS + FEE + "048100", "a0"), Era.Conway,
                FormatPolicy.ACCEPT_ALL)).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.walk(HexUtil.decodeHexString("82a0a0"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawTx.walk(HexUtil.decodeHexString("a0"), Era.Conway, FormatPolicy.ACCEPT_ALL))
                .isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D5: unknown keys, a field outside its era and a missing field are the policy's to reject. */
    @Test
    void acceptAllRejectsNoneOfThem() {
        assertThatCode(() -> RawTx.walk(tx("a2" + "1600" + "63616263" + "00", "a10880"), Era.Mary,
                FormatPolicy.ACCEPT_ALL)).doesNotThrowAnyException();
    }
}
