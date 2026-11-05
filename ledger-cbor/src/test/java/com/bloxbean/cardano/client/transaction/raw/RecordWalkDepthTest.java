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
 * ADR 0003 I9: a walk stays stack-safe at any nesting, in the style of {@code RawViewDepthTest}: the nesting of a value
 * at every kind of position, far beyond the largest transaction, on a platform thread with the default stack and on a
 * virtual thread. No test sets {@code -Xss}.
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
        walk("a3" + "00" + DEEP_LIST + "01" + DEEP_MAP + "0200", "a0");
        walk("a4008001800200" + "18" + "63" + DEEP_LIST, "a0");
        walk("a4008001800200" + DEEP_LIST + "00", "a1" + DEEP_MAP + "00");
        walk("a4" + "1800" + DEEP_LIST + "0080" + "0180" + "0200", "a0");
        // a run of tags on a record and on a key
        walk(DEEP_TAGS + "a3008001800200", DEEP_TAGS + "a0");
        walk("a4008001800200" + DEEP_TAGS + "00" + "00", "a0");
        // a value where a table expects a record, and tags on the items of a set
        walk("a4008001800200" + "04" + DEEP_TAGS + "81" + DEEP_TAGS + "82" + DEEP_TAGS + "00" + DEEP_TAGS + CRED, "a0");
        walk("a4008001800200" + "04" + "81" + "8300" + DEEP_LIST + "00", "a0");
        // a certificate alone: a deep value at each kind of table
        cert("83" + "02" + DEEP_LIST + "00");
        cert("82" + "00" + "82" + DEEP_LIST + "00");
        cert("84" + "10" + CRED + DEEP_MAP + DEEP_LIST);
        cert("84" + "10" + CRED + "00" + "82" + DEEP_LIST + DEEP_MAP);
        cert("83" + "09" + CRED + "82" + DEEP_LIST + "00");
        cert("8a" + "03" + DEEP_LIST + DEEP_MAP + DEEP_LIST + DEEP_MAP + DEEP_LIST + DEEP_MAP + DEEP_LIST + "81"
                + DEEP_LIST.substring(2) + DEEP_MAP);
        cert("82" + "06" + "82" + DEEP_LIST + DEEP_MAP);
        // the accessors, on the same values
        assertThat(RawCertificate.lenient(CertKind.REG_CERT, span("83" + "07" + CRED + DEEP_LIST)).coin()).isNull();
        assertThatThrownBy(() -> RawCertificate.of(span("83" + "07" + CRED + DEEP_LIST)).coin())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawCertificate.of(span("83" + "07" + DEEP_LIST + "00")).credential())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> RawCertificate.of(span("84" + "10" + CRED + "00" + DEEP_LIST)).anchor())
                .isInstanceOf(CborRuntimeException.class);
    }

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static void walk(String body, String witnesses) {
        AtomicInteger calls = new AtomicInteger();
        RawTx.walk(HexUtil.decodeHexString("84" + body + witnesses + "f5f6"), Era.Conway, new CountingPolicy(calls));
        assertThat(calls.get()).isPositive();
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
