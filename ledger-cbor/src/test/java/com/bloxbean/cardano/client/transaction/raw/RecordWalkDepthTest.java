package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0003 I9: a walk and the block views stay stack-safe at any nesting, in the style of {@code RawViewDepthTest}: the
 * nesting of a value at every kind of position, far beyond the largest transaction, on a platform thread with the
 * default stack and on a virtual thread. No test sets {@code -Xss}.
 */
class RecordWalkDepthTest {
    private static final int LEVELS = 200_000;
    private static final String DEEP_LIST = "81".repeat(LEVELS) + "00";
    private static final String DEEP_MAP = "a100".repeat(LEVELS) + "00";
    private static final String DEEP_NATIVE_SCRIPT = "820181".repeat(LEVELS) + "8200" + "581c" + "00".repeat(28);
    private static final String DEEP_TAGS = "d818".repeat(LEVELS);
    private static final String CRED = "8200" + "581c" + "00".repeat(28);

    private static void runFixtures() {
        // the unbounded types, at their places in a witness set
        walk("a3008001800200", "a10181" + DEEP_NATIVE_SCRIPT);
        walk("a3008001800200", "a10481" + DEEP_LIST);
        walk("a3008001800200", "a105818400" + "00" + DEEP_LIST + "820000");
        // a value in a leaf of the body, under a known and an unknown key, as a key, and repeated
        walk("a3" + "0080" + "0180" + "02" + DEEP_LIST, "a0");
        walk("a4008001800200" + "18" + "63" + DEEP_LIST, "a0");
        walk("a4008001800200" + DEEP_LIST + "00", "a1" + DEEP_MAP + "00");
        walk("a4" + "1800" + "80" + "0080" + "0180" + "02" + DEEP_MAP, "a0");
        // a run of tags on a record and on a key
        walk(DEEP_TAGS + "a3008001800200", DEEP_TAGS + "a0");
        walk("a4008001800200" + DEEP_TAGS + "00" + "00", "a0");
        // a value where a table expects a record, and tags on the items of a set
        walk("a4008001800200" + "04" + DEEP_TAGS + "81" + DEEP_TAGS + "82" + DEEP_TAGS + "00" + DEEP_TAGS + CRED, "a0");
        walk("a4008001800200" + "04" + "81" + "8300" + DEEP_LIST + "00", "a0");
        // a deep value at each kind of table of a body: the walk reports it or cannot enter it
        rejected("a3" + "00" + DEEP_MAP + "0180" + "0200");
        rejected("a3" + "0080" + "01" + DEEP_MAP + "0200");
        rejected("a3" + "0080" + "0181" + DEEP_TAGS + "00" + "0200");
        rejected("a3" + "00" + "81" + DEEP_TAGS + "00" + "0180" + "0200");
        walk("a3" + "0081" + "82" + DEEP_LIST + DEEP_MAP + "0180" + "0200", "a0");
        rejected("a3" + "0080" + "018182" + DEEP_LIST + DEEP_MAP + "0200");
        walk("a3" + "0080" + "018182" + DEEP_LIST + "82" + DEEP_LIST + DEEP_MAP + "0200", "a0");
        walk("a3" + "0080" + "0181a2" + "00" + DEEP_LIST + "02" + "82" + "00" + DEEP_LIST + "0200", "a0");
        walk("a3" + "0080" + "01818240" + "82" + DEEP_LIST + "a1" + "40" + "a1" + "40" + DEEP_LIST + "0200", "a0");
        walk("a4" + "0080" + "0180" + "0200" + "05" + "a1" + DEEP_LIST + DEEP_MAP, "a0");
        walk("a4" + "0080" + "0180" + "0200" + "09" + "a1" + DEEP_TAGS + "40" + "a1" + DEEP_LIST + DEEP_MAP, "a0");
        rejected("a4" + "0080" + "0180" + "0200" + "10" + DEEP_TAGS + "00");
        // a certificate alone: a deep value at each kind of table
        cert("83" + "02" + DEEP_LIST + "00");
        cert("82" + "00" + "82" + DEEP_LIST + "00");
        cert("84" + "10" + CRED + DEEP_MAP + DEEP_LIST);
        cert("84" + "10" + CRED + "00" + "82" + DEEP_LIST + DEEP_MAP);
        cert("83" + "09" + CRED + "82" + DEEP_LIST + "00");
        cert("8a" + "03" + DEEP_LIST + DEEP_MAP + DEEP_LIST + DEEP_MAP + DEEP_LIST + DEEP_MAP + DEEP_LIST + "81"
                + DEEP_LIST.substring(2) + DEEP_MAP);
        cert("82" + "06" + "82" + DEEP_LIST + DEEP_MAP);
        // the views of a body, on the same values
        view("a3" + "00" + DEEP_LIST + "0180" + "0200");
        view("a3" + "0081" + "82" + DEEP_LIST + "00" + "0180" + "0200");
        view("a3" + "0080" + "01" + DEEP_MAP + "0200");
        view("a3" + "0080" + "0181" + "82" + DEEP_LIST + "00" + "0200");
        view("a3" + "0080" + "0181" + "8240" + DEEP_LIST + "0200");
        view("a3" + "0080" + "0181" + "82" + "40" + "8200" + DEEP_MAP + "0200");
        view("a3" + "0080" + "0181" + "82" + "40" + "8200" + "a1" + "40" + DEEP_MAP + "0200");
        view("a3" + "0080" + "0181" + "a2" + "00" + "40" + "01" + DEEP_LIST + "0200");
        view("a3" + "0080" + "0181" + "a3" + "00" + "40" + "0100" + "02" + DEEP_LIST + "0200");
        view("a3" + "0080" + "0181" + "a3" + "00" + "40" + "0100" + "03" + DEEP_LIST + "0200");
        view("a4" + "0080" + "0180" + "0200" + "05" + DEEP_MAP);
        view("a4" + "0080" + "0180" + "0200" + "05" + "a1" + DEEP_LIST + "00");
        view("a4" + "0080" + "0180" + "0200" + "09" + "a1" + DEEP_TAGS + "40" + "a1" + "40" + DEEP_LIST);
        view("a4" + "0080" + "0180" + "0200" + "04" + DEEP_LIST);
        view("a4" + "0080" + "0180" + "0200" + "1864" + DEEP_LIST);
        view("a4" + "0080" + "0180" + "0200" + DEEP_LIST + "00");
        view(DEEP_TAGS + "a3" + "0080" + "0180" + "02" + DEEP_TAGS + "00");
        // the accessors, on the same values
        assertThat(RawCertificate.lenient(CertKind.REG_CERT, span("83" + "07" + CRED + DEEP_LIST)).coin()).isNull();
        assertThatThrownBy(() -> RawCertificate.of(span("83" + "07" + CRED + DEEP_LIST)).coin())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawCertificate.of(span("83" + "07" + DEEP_LIST + "00")).credential())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawCertificate.of(span("84" + "10" + CRED + "00" + DEEP_LIST)).anchor())
                .isInstanceOf(CborRuntimeException.class);
        witnessAndAux();
        header();
        block();
    }

    // A deep value at each kind of table of a witness set and of aux data, on both readings of each view.
    private static void witnessAndAux() {
        String units = "820000";
        for (String witnesses : java.util.List.of(
                "a1" + "00" + DEEP_LIST, "a1" + "0081" + DEEP_LIST, "a1" + "0081" + "82" + DEEP_LIST + DEEP_MAP,
                "a1" + "0281" + "84" + DEEP_LIST + DEEP_MAP + DEEP_LIST + DEEP_MAP, "a1" + "0181" + DEEP_NATIVE_SCRIPT,
                "a1" + "0381" + DEEP_TAGS + "40", "a1" + "0481" + DEEP_MAP, "a1" + "05" + DEEP_LIST,
                "a1" + "05" + DEEP_MAP, "a1" + "0581" + DEEP_LIST,
                "a1" + "0581" + "84" + DEEP_LIST + "00" + "00" + units,
                "a1" + "0581" + "84" + "00" + DEEP_MAP + DEEP_LIST + units,
                "a1" + "0581" + "84" + "00" + "00" + "00" + DEEP_LIST,
                "a1" + "0581" + "84" + "00" + "00" + "00" + "82" + DEEP_LIST + "00",
                "a1" + "05a1" + DEEP_LIST + units, "a1" + "05a2" + DEEP_LIST + units + DEEP_MAP + units,
                "a1" + "05a1" + "82" + "00" + DEEP_LIST + "82" + "00" + units,
                "a1" + "05a1" + "820000" + "82" + DEEP_MAP + DEEP_LIST, DEEP_TAGS + "a1" + "05" + DEEP_TAGS + "80",
                DEEP_TAGS + "a0")) {
            walkOf(WitnessSetField.RECORD, witnesses);
            read(span(witnesses));
        }
        for (String aux : java.util.List.of(DEEP_MAP, "a100" + DEEP_LIST, DEEP_TAGS + DEEP_MAP,
                DEEP_TAGS + "d90103" + "a1" + "00" + DEEP_MAP, "d90103" + DEEP_TAGS + "a1" + "00" + DEEP_MAP,
                "d90103" + "a1" + "00" + DEEP_LIST, "d90103" + "a1" + "01" + DEEP_LIST,
                "d90103" + "a1" + "0181" + DEEP_NATIVE_SCRIPT, "d90103" + "a1" + "0281" + DEEP_LIST,
                "d90103" + "a1" + DEEP_LIST + "00", "82" + DEEP_MAP + "81" + DEEP_NATIVE_SCRIPT,
                "82" + DEEP_LIST + DEEP_MAP, DEEP_LIST, DEEP_TAGS + "00"))
            readAux(span(aux));
        assertThat(RawRedeemer.lenient(span("84" + DEEP_TAGS + "00" + DEEP_TAGS + "00" + DEEP_LIST + DEEP_MAP)).tag())
                .isZero();
        assertRejected(() -> RawVkeyWitness.of(span(DEEP_LIST)));
        assertRejected(() -> RawBootstrapWitness.of(span("84" + DEEP_LIST + DEEP_MAP + "40" + "40")));
        assertRejected(() -> RawExUnits.of(span("82" + DEEP_LIST + DEEP_MAP)));
        assertThat(RawItems.indefiniteWhenDecoded(span(DEEP_TAGS + "9fff"))).isTrue();
        // the transaction's own array
        AtomicInteger calls = new AtomicInteger();
        RawTx.walk(HexUtil.decodeHexString("84" + "a3008001800200" + "a0" + DEEP_LIST + DEEP_MAP), Era.Conway,
                new CountingPolicy(calls));
        assertThat(calls.get()).isPositive();
    }

    private static void walkOf(RecordSpec record, String value) {
        AtomicInteger calls = new AtomicInteger();
        try {
            record.walk(span(value), Era.Conway, new CountingPolicy(calls));
        } catch (CborRuntimeException e) {
            // the walk could not enter a value: a rejection, as D5 says, not an overflow
        }
        assertThat(calls.get()).isPositive();
    }

    private static void read(CborSpan witnesses) {
        RawWitnessSet set = RawWitnessSet.lenient(witnesses, Era.Conway);
        byte[] tx = HexUtil.decodeHexString("84" + "a3008001800200" + HexUtil.encodeHexString(witnesses.bytes())
                + "f5f6");
        boolean strict = !witnesses.isTagged();
        for (RawWitnessSet view : strict ? java.util.List.of(set, RawTx.of(tx).witnessSet(Era.Conway))
                : java.util.List.of(set)) {
            for (Runnable accessor : java.util.List.<Runnable>of(view::vkeyWitnesses, view::nativeScripts,
                    view::bootstrapWitnesses, view::plutusV1Scripts, view::datums, view::redeemers,
                    view::plutusV2Scripts, view::plutusV3Scripts, view::scripts)) {
                try {
                    accessor.run();
                } catch (CborRuntimeException e) {
                    // a rejection, not an overflow: a stack overflow is an Error and fails the test
                }
            }
            try {
                view.redeemers().ifPresent(redeemers -> redeemers.forEach(RawRedeemer::units));
            } catch (CborRuntimeException e) {
                // the same
            }
        }
    }

    private static void readAux(CborSpan aux) {
        try {
            RawAuxData.RECORD.walk(aux, Era.Conway, FormatPolicy.ACCEPT_ALL);
        } catch (CborRuntimeException e) {
            // the same
        }
        try {
            RawAuxData view = RawAuxData.lenient(aux, Era.Conway);
            view.metadata();
            view.scripts();
        } catch (CborRuntimeException e) {
            // the same
        }
    }

    private static void header() {
        String flat = "8f" + "00" + "00" + "f6" + "40" + "40" + "824040" + "824040" + "00" + "40"
                + "40" + "00" + "00" + "40" + "00" + "00";
        String nested = "8a" + "00" + "00" + "f6" + "40" + "40" + "824040" + "00" + "40" + "8440000040" + "820000";
        // a run of tags on the header, its body and an item, whose hash is that of the bytes
        RawHeader tagged = RawHeader.of(span(DEEP_TAGS + "82" + DEEP_TAGS + nested + DEEP_TAGS + "40"));
        assertThat(tagged.blockNumber()).isZero();
        assertThat(tagged.bodySignature()).isEmpty();
        assertThat(tagged.hash()).hasSize(32);
        // a deep value at each kind of position of a body: an item, a VRF result, a group, in both layouts
        assertRejected(() -> RawHeader.of(span("82" + flat.replace("f6", DEEP_LIST) + "40")).prevHash());
        assertRejected(() -> RawHeader.of(span("82" + flat.replace("824040", DEEP_MAP) + "40")).nonceVrf());
        assertRejected(() -> RawHeader.of(span("82" + nested.replace("824040", "82" + DEEP_LIST + "40") + "40"))
                .vrfResult().orElseThrow().output());
        assertRejected(() -> RawHeader.of(span("82" + nested.replace("8440000040", DEEP_LIST) + "40")).hotVkey());
        assertRejected(() -> RawHeader.of(span("82" + nested.replace("820000", DEEP_MAP) + "40")).protocolMajor());
        assertRejected(() -> RawHeader.of(span("82" + nested.replace("820000", "82" + DEEP_LIST + "00") + "40"))
                .protocolMajor());
        // where the header and its body should be
        assertRejected(() -> RawHeader.of(span(DEEP_LIST)));
        assertRejected(() -> RawHeader.of(span("82" + DEEP_LIST + "40")));
        assertRejected(() -> RawHeader.of(span("82" + DEEP_MAP + "40"), Era.Conway));
    }

    private static void block() {
        String header = "82" + DEEP_TAGS + "80" + DEEP_LIST;
        byte[] deep = HexUtil.decodeHexString("82" + DEEP_TAGS + "07" + "85" + header + DEEP_LIST + DEEP_MAP + DEEP_TAGS
                + "a0" + DEEP_LIST);
        assertThat(CborSpan.of(deep).get(1).length()).isPositive();
        assertRejected(() -> BlockEnvelope.of(deep)); // the index is tagged
        assertThat(HexUtil.encodeHexString(BlockEnvelope.headerOf(deep).bytes())).isEqualTo(header);
        assertThat(BlockEnvelope.eraIndexOf(deep)).isEqualTo(7);

        byte[] enveloped = HexUtil.decodeHexString("82" + "07" + "85" + header + DEEP_LIST + DEEP_MAP + "a0" + "80");
        assertThat(BlockEnvelope.of(enveloped).eraIndex()).isEqualTo(7);
        assertRejected(() -> RawBlock.of(enveloped));
        assertRejected(() -> RawBlock.of(BlockEnvelope.of(enveloped)));
        assertRejected(() -> RawBlock.readAs(BlockEnvelope.of(enveloped), Era.Conway));
        // tags on the arrays on the way to the header, and damage after a deep header
        byte[] damaged = HexUtil.decodeHexString(DEEP_TAGS + "82" + "07" + DEEP_TAGS + "85" + header + "fc");
        assertThat(HexUtil.encodeHexString(BlockEnvelope.headerOf(damaged).bytes())).isEqualTo(header);
        assertRejected(() -> BlockEnvelope.of(damaged));
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable read) {
        assertThatThrownBy(read).isInstanceOf(CborRuntimeException.class);
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static void walk(String body, String witnesses) {
        AtomicInteger calls = new AtomicInteger();
        RawTx.walk(HexUtil.decodeHexString("84" + body + witnesses + "f5f6"), Era.Conway, new CountingPolicy(calls));
        assertThat(calls.get()).isPositive();
    }

    // Each accessor reads its field, or rejects it, on the reader's and the models' readings of the body.
    private static void view(String body) {
        readAll(CborSpan.of(HexUtil.decodeHexString(body)));
    }

    private static void readAll(CborSpan body) {
        for (Runnable read : RawTxBodyTest.accessors(RawTxBody.lenient(body, Era.Conway))) {
            try {
                read.run();
            } catch (RuntimeException e) {
                // a rejection, not an overflow: a stack overflow is an Error and fails the test
                assertThat(e).isNotInstanceOf(IllegalStateException.class);
            }
        }
    }

    private static void rejected(String body) {
        assertRejected(() -> walk(body, "a0"));
    }

    private static void cert(String certificate) {
        AtomicInteger calls = new AtomicInteger();
        try {
            CertKind.RECORD.walk(span(certificate), Era.Conway, new CountingPolicy(calls));
        } catch (CborRuntimeException e) {
            // the walk could not enter a value: a rejection, as D5 says, not an overflow
        }
        assertThat(calls.get()).isPositive();
    }

    private static final class CountingPolicy implements FormatPolicy {
        private final AtomicInteger calls;

        CountingPolicy(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public void enter(RecordSpec record, CborSpan container, Era era) {
            calls.incrementAndGet();
        }

        @Override
        public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence, Era era) {
            calls.incrementAndGet();
        }

        @Override
        public void check(FieldType type, CborSpan value, Era era) {
            assertThat(value.length()).isPositive();
        }
    }

    @Test
    void onPlatformThreadWithDefaultStack() throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> run(failure), "record-walk-depth-default-stack");
        thread.start();
        thread.join();
        rethrow(failure);
    }

    // Created reflectively so the class compiles on Java 17; the JDK 21 CI job runs it.
    @Test
    @EnabledIf("javaAtLeast21")
    void onVirtualThread() throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = (Thread) Thread.class.getMethod("startVirtualThread", Runnable.class)
                .invoke(null, (Runnable) () -> run(failure));
        thread.join();
        rethrow(failure);
    }

    static boolean javaAtLeast21() {
        return Runtime.version().feature() >= 21;
    }

    private static void run(AtomicReference<Throwable> failure) {
        try {
            runFixtures();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static void rethrow(AtomicReference<Throwable> failure) throws Throwable {
        if (failure.get() != null)
            throw failure.get();
    }
}
