package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Special;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Malformed input (ADR 0001 section 6.8), truncation sweep, fuzzing and a differential check of the walker's framing
 * against cbor-java.
 */
class CborSpanWellFormednessTest {

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
            "truncated head argument (1 byte), 18",
            "truncated head argument (2 bytes), 1901",
            "truncated head argument (4 bytes), 1a000000",
            "truncated head argument (8 bytes), 1b00000000",
            "string shorter than declared, 5a000000030102",
            "truncated byte string at end, 4201",
            "truncated text string, 6261",
            "truncated array, 8201",
            "truncated map value, a101",
            "truncated nested container, 8281",
            "indefinite array without BREAK, 9f01",
            "indefinite string without BREAK, 5f4101",
            "tag without item, c1",
            "tag at end of array, 81c1",
            "reserved info 28 (uint), 1c",
            "reserved info 29 (nint), 3d",
            "reserved info 30 (bytes), 5e",
            "reserved info 28 (array), 9c",
            "reserved info 29 (map), bd",
            "reserved info 30 (tag), de",
            "reserved info 28 (simple), fc",
            "reserved info 29 (simple), fd",
            "reserved info 30 (simple), fe",
            "indefinite unsigned, 1f",
            "indefinite negative, 3f",
            "indefinite tag, df01",
            "BREAK at top level, ff",
            "BREAK in a definite array, 82ff01",
            "BREAK as definite map key, a1ff01",
            "BREAK as map value, bf01ffff",
            "BREAK after tag, 9fc1ffff",
            "BREAK after tag in definite array, 81c1ff",
            "text chunk in byte string, 5f6161ff",
            "byte chunk in text string, 7f4161ff",
            "nested indefinite chunk, 5f5fffff",
            "tagged chunk, 5fc14100ff",
            "integer chunk, 5f01ff",
            "string length beyond input, 5b0000000100000000",
            "string length not fitting int, 5bffffffffffffffff",
            "array count beyond input, 9b000000010000000000",
            "array count not fitting int, 9bffffffffffffffff",
            "array count 2^31-1, 9a7fffffff00",
            "map count beyond input, ba4000000000",
            "map pairs beyond input, a3000102",
            "trailing byte, 0102",
            "trailing item, 82010203",
    })
    void malformedInputIsRejectedWithItsOffset(String scenario, String hex) {
        byte[] bytes = decodeHexString(hex);
        assertThatThrownBy(() -> CborSpan.of(bytes))
                .as(scenario)
                .isInstanceOf(CborRuntimeException.class)
                .hasMessageContaining("at offset");
    }

    @Test
    void twoByteSimpleValuesBelow32AreRejected() {
        // RFC 8949 section 3.3: f8 00 to f8 1f are not well-formed (simple values below 32 take one byte)
        for (int value = 0; value < 32; value++) {
            byte[] bytes = {(byte) 0xf8, (byte) value};
            assertThatThrownBy(() -> CborSpan.of(bytes)).as("f8 %02x", value)
                    .isInstanceOf(CborRuntimeException.class).hasMessageContaining("at offset 0");
            byte[] inArray = {(byte) 0x81, (byte) 0xf8, (byte) value};
            assertThatThrownBy(() -> CborSpan.of(inArray)).as("[f8 %02x]", value)
                    .isInstanceOf(CborRuntimeException.class).hasMessageContaining("at offset 1");
        }
        assertThat(CborSpan.of(decodeHexString("f820")).length()).isEqualTo(2);
        assertThat(CborSpan.of(decodeHexString("f8ff")).length()).isEqualTo(2);
    }

    @Test
    void emptyInputIsRejected() {
        assertThatThrownBy(() -> CborSpan.of(new byte[0])).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> CborSpan.skip(new byte[0], 0, 0)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void nonMinimalAndUnusualButWellFormedInputIsAccepted() {
        for (String hex : List.of("1800", "190000", "1a00000000", "1b0000000000000000", "3800",
                "9800", "990000", "9a00000000", "9b0000000000000000", "b800", "5800", "7800",
                "d80001", "d9000001", "da0000000001", "db000000000000000001", // tag 0 at every width
                "f820", "f8ff", "f90000", "fa00000000", "fb0000000000000000", "e0", "f7",
                "5fff", "7fff", "9fff", "bfff", "5f40ff", "a201000100")) {
            byte[] bytes = decodeHexString(hex);
            assertThat(CborSpan.of(bytes).length()).as(hex).isEqualTo(bytes.length);
        }
    }

    @Test
    void declaredSizesAreCheckedBeforeAllocating() {
        assertAllocationIsFlat(decodeHexString("9bffffffffffffffff"));
        assertAllocationIsFlat(decodeHexString("9a7fffffff00"));
        assertAllocationIsFlat(decodeHexString("bb7fffffffffffffff"));
        assertAllocationIsFlat(decodeHexString("5a7fffffff00"));
        assertAllocationIsFlat(decodeHexString("9f9a7fffffff"));
    }

    private static void assertAllocationIsFlat(byte[] bytes) {
        for (int warmup = 0; warmup < 1_000; warmup++)
            rejects(bytes);
        long before = allocatedBytes();
        rejects(bytes);
        long allocated = allocatedBytes() - before;
        if (before >= 0)
            assertThat(allocated).as(encodeHexString(bytes)).isLessThan(16 * 1024);
    }

    private static void rejects(byte[] bytes) {
        try {
            CborSpan.of(bytes);
            fail("accepted " + encodeHexString(bytes));
        } catch (CborRuntimeException expected) {
            // expected
        }
    }

    @Test
    void everyProperPrefixIsRejected() {
        List<byte[]> items = new ArrayList<>();
        RandomCbor generator = new RandomCbor(681);
        for (int i = 0; i < 300; i++)
            items.add(generator.next(5));
        items.add(decodeHexString("9804a200814101" + "0109" + "9f0102ff" + "d81843820102" + "f6"));
        items.add(decodeHexString("d9010282d8185f4182420102ffbf01c249010000000000000000ff"));

        for (byte[] item : items) {
            assertThat(CborSpan.of(item).length()).isEqualTo(item.length);
            for (int cut = 0; cut < item.length; cut++) {
                byte[] prefix = Arrays.copyOf(item, cut);
                assertThatThrownBy(() -> CborSpan.of(prefix))
                        .as("prefix %d of %s", cut, encodeHexString(item))
                        .isInstanceOf(CborRuntimeException.class);
            }
        }
    }

    @Test
    void framingAgreesWithCborJavaOnGeneratedInput() {
        RandomCbor generator = new RandomCbor(20261001);
        for (int i = 0; i < 20_000; i++) {
            byte[] item = generator.next(6);
            assertThat(CborSpan.skip(item, 0, item.length)).as(encodeHexString(item)).isEqualTo(item.length);
            assertThat(cborJavaEnd(item)).as(encodeHexString(item)).isEqualTo(item.length);
        }
    }

    /**
     * Mutated and random inputs: the walker returns or throws {@link CborRuntimeException}, never anything else, in
     * bounded time and memory. Whenever it accepts, cbor-java frames the same item.
     */
    @Test
    void fuzzedInputTerminatesCleanlyAndAgreesWithCborJava() {
        Random random = new Random(681_682);
        RandomCbor generator = new RandomCbor(682);
        long deadline = System.nanoTime() + 60_000_000_000L;
        List<byte[]> inputs = new ArrayList<>();
        for (int i = 0; i < 100_000; i++)
            inputs.add(i % 4 == 0 ? randomBytes(random) : mutate(generator.next(5), random));
        // a first pass runs every code path once (linking string concatenation in error messages, for example), so the
        // measured pass sees steady-state allocation
        for (byte[] input : inputs) {
            try {
                CborSpan.skip(input, 0, input.length);
            } catch (CborRuntimeException e) {
                // rejected
            }
        }
        int accepted = 0;
        for (byte[] input : inputs) {
            long before = allocatedBytes();
            int end;
            try {
                end = CborSpan.skip(input, 0, input.length);
            } catch (CborRuntimeException e) {
                end = -1;
            }
            long allocated = allocatedBytes() - before;
            if (before >= 0)
                assertThat(allocated).as(encodeHexString(input)).isLessThan(64L * input.length + 16 * 1024);
            if (end >= 0) {
                accepted++;
                Integer cborJavaEnd = cborJavaEndIgnoringSemanticTags(Arrays.copyOf(input, end));
                if (cborJavaEnd != null)
                    assertThat(cborJavaEnd).as(encodeHexString(input)).isEqualTo(end);
            }
            assertThat(System.nanoTime()).as("fuzz loop exceeded its time budget").isLessThan(deadline);
        }
        assertThat(accepted).isGreaterThan(1_000);
    }

    private static byte[] randomBytes(Random random) {
        byte[] bytes = new byte[1 + random.nextInt(64)];
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] mutate(byte[] item, Random random) {
        byte[] bytes = item.clone();
        int mutations = 1 + random.nextInt(3);
        for (int m = 0; m < mutations && bytes.length > 0; m++) {
            int at = random.nextInt(bytes.length);
            switch (random.nextInt(4)) {
                case 0:
                    bytes[at] = (byte) random.nextInt(256);
                    break;
                case 1:
                    bytes[at] ^= (byte) (1 << random.nextInt(8));
                    break;
                case 2:
                    bytes = Arrays.copyOf(bytes, at);
                    break;
                default:
                    byte[] longer = new byte[bytes.length + 1];
                    System.arraycopy(bytes, 0, longer, 0, at);
                    longer[at] = (byte) random.nextInt(256);
                    System.arraycopy(bytes, at, longer, at + 1, bytes.length - at);
                    bytes = longer;
                    break;
            }
        }
        return bytes;
    }

    private static int cborJavaEnd(byte[] bytes) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            new CborDecoder(in).decodeNext();
            return bytes.length - in.available();
        } catch (CborException e) {
            throw new AssertionError("cbor-java rejected " + encodeHexString(bytes), e);
        } finally {
            untagSingletons();
        }
    }

    // cbor-java decodes tags 30 and 38 semantically and rejects well-formed items it cannot convert.
    private static Integer cborJavaEndIgnoringSemanticTags(byte[] bytes) {
        try {
            return cborJavaEnd(bytes);
        } catch (AssertionError e) {
            String message = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : null);
            if (message.contains("RationalNumber") || message.contains("LanguageTaggedString")
                    || message.contains("Denominator"))
                return null;
            throw e;
        }
    }

    // cbor-java attaches a tag to its shared singleton when it decodes a tagged simple value; undo it.
    private static void untagSingletons() {
        SimpleValue.FALSE.removeTag();
        SimpleValue.TRUE.removeTag();
        SimpleValue.NULL.removeTag();
        SimpleValue.UNDEFINED.removeTag();
        Special.BREAK.removeTag();
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean)
            return ((com.sun.management.ThreadMXBean) bean).getCurrentThreadAllocatedBytes();
        return -1;
    }
}
