package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.AbstractFloat;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import com.bloxbean.cardano.client.common.cbor.custom.LegacyCborEncoder;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The iterative codec against the recursive one it replaces: {@link CborSerializationUtil#deserialize(byte[])} must
 * give cbor-java's trees, and {@link CborSerializationUtil#serialize} the old encoder's bytes, on real transactions and
 * blocks, on seeded random CBOR and on fuzzed input.
 */
class DataItemCodecDifferentialTest {

    @Test
    void realTransactionsAndBlocksDecodeAndEncodeAsBefore() throws CborException {
        int compared = 0;
        for (RealCborCorpus.Item item : RealCborCorpus.all()) {
            List<DataItem> decoded = CborSerializationUtil.deserializeAll(item.cbor());
            List<DataItem> expected;
            try {
                expected = CborJava.decodeAll(item.cbor());
            } catch (StackOverflowError e) {
                continue; // the deeply nested trigger: covered by the depth tests
            }
            try {
                assertSameTrees(item.toString(), decoded, expected);
                compared++;
            } finally {
                CborJava.untagSingletons();
            }
        }
        assertThat(compared).isGreaterThan(80);
    }

    @Test
    void randomCborDecodesAndEncodesAsBefore() throws CborException {
        RandomCbor generator = new RandomCbor(20261002, true);
        int semanticFailures = 0;
        for (int i = 0; i < 30_000; i++) {
            byte[] bytes = generator.next(6);
            try {
                List<DataItem> expected;
                try {
                    expected = CborJava.decodeAll(bytes);
                } catch (CborException e) {
                    // cbor-java rejects tag 30/38 with the wrong payload; so must we
                    assertRejected(bytes);
                    semanticFailures++;
                    continue;
                }
                assertSameTrees(encodeHexString(bytes), CborSerializationUtil.deserializeAll(bytes), expected);
            } finally {
                CborJava.untagSingletons();
            }
        }
        assertThat(semanticFailures).isPositive();
    }

    /**
     * Mutated and random input: the decoder rejects whatever the walker rejects (malformed CBOR); on well-formed input it
     * gives cbor-java's trees, and rejects only what cbor-java rejects too (tag 30/38 with the wrong payload). cbor-java
     * only sees input the walker accepted, since it preallocates from declared sizes.
     */
    @Test
    void fuzzedInputIsDecodedOnlyWhenWellFormedAndThenAsBefore() throws CborException {
        Random random = new Random(681_683);
        RandomCbor generator = new RandomCbor(683, true);
        long deadline = System.nanoTime() + 60_000_000_000L;
        int accepted = 0;
        for (int i = 0; i < 50_000; i++) {
            byte[] input = i % 4 == 0 ? randomBytes(random) : mutate(generator.next(5), random);
            List<DataItem> decoded;
            try {
                decoded = CborSerializationUtil.deserializeAll(input);
            } catch (CborRuntimeException e) {
                decoded = null;
            }
            if (!walkerAccepts(input)) {
                assertThat(decoded).as("accepted malformed input %s", encodeHexString(input)).isNull();
                continue;
            }
            try {
                List<DataItem> expected;
                try {
                    expected = CborJava.decodeAll(input);
                } catch (CborException | RuntimeException e) {
                    expected = null;
                }
                if (decoded != null) {
                    accepted++;
                    assertThat(expected).as("cbor-java rejects %s", encodeHexString(input)).isNotNull();
                    assertSameTrees(encodeHexString(input), decoded, expected);
                } else {
                    assertThat(expected).as("rejected well-formed input that cbor-java decodes: %s", encodeHexString(input)).isNull();
                }
            } finally {
                CborJava.untagSingletons();
            }
            assertThat(System.nanoTime()).as("fuzz loop exceeded its time budget").isLessThan(deadline);
        }
        assertThat(accepted).isGreaterThan(1_000);
    }

    /**
     * Same trees: {@code equals} with the new tree as the receiver (a plain cbor-java map cannot equal the map subclass),
     * and the same encodings, which also checks map entry order. The new encoder writes the same bytes as the old one,
     * canonical and not, for both trees.
     */
    static void assertSameTrees(String what, List<DataItem> decoded, List<DataItem> expected) throws CborException {
        assertThat(decoded).as(what).hasSameSizeAs(expected);
        for (int i = 0; i < decoded.size(); i++) {
            DataItem actual = decoded.get(i);
            DataItem oracle = expected.get(i);
            if (!cborJavaTreeIsReliable(actual)) {
                // only the encoder can be compared
                for (boolean canonical : new boolean[]{false, true})
                    assertThat(CborSerializationUtil.serialize(actual, canonical)).as("%s item %d new encoder", what, i)
                            .isEqualTo(LegacyCborEncoder.encode(actual, canonical));
                continue;
            }
            assertThat(actual.equals(oracle)).as("%s item %d equals cbor-java's", what, i).isTrue();
            for (boolean canonical : new boolean[]{false, true}) {
                byte[] legacy = LegacyCborEncoder.encode(oracle, canonical);
                assertThat(LegacyCborEncoder.encode(actual, canonical)).as("%s item %d encoding", what, i).isEqualTo(legacy);
                assertThat(CborSerializationUtil.serialize(oracle, canonical)).as("%s item %d new encoder", what, i).isEqualTo(legacy);
                assertThat(CborSerializationUtil.serialize(actual, canonical)).as("%s item %d new encoder, new tree", what, i).isEqualTo(legacy);
            }
        }
    }

    /**
     * cbor-java's tree cannot be compared when it holds a NaN float (its equals is false for NaN, even between two decodes
     * of the same bytes) or a tagged false/true/null/undefined: cbor-java tags its shared singleton, so equal-looking
     * items change under it. Our decoder returns a fresh instance for a tagged simple value.
     */
    private static boolean cborJavaTreeIsReliable(DataItem item) {
        if (item instanceof AbstractFloat)
            return !Float.isNaN(((AbstractFloat) item).getValue());
        if (item instanceof DoublePrecisionFloat)
            return !Double.isNaN(((DoublePrecisionFloat) item).getValue());
        if (item instanceof SimpleValue && item.hasTag())
            return ((SimpleValue) item).getSimpleValueType() == SimpleValueType.UNALLOCATED
                    || ((SimpleValue) item).getSimpleValueType() == SimpleValueType.RESERVED;
        if (item instanceof Array)
            return ((Array) item).getDataItems().stream().allMatch(DataItemCodecDifferentialTest::cborJavaTreeIsReliable);
        if (item instanceof Map) {
            Map map = (Map) item;
            return map.getKeys().stream().allMatch(DataItemCodecDifferentialTest::cborJavaTreeIsReliable)
                    && map.getValues().stream().allMatch(DataItemCodecDifferentialTest::cborJavaTreeIsReliable);
        }
        return true;
    }

    private static void assertRejected(byte[] bytes) {
        try {
            CborSerializationUtil.deserializeAll(bytes);
            fail("accepted what cbor-java rejects: " + encodeHexString(bytes));
        } catch (CborRuntimeException expected) {
            // expected
        }
    }

    private static boolean walkerAccepts(byte[] input) {
        try {
            int pos = 0;
            while (pos < input.length)
                pos = CborSpan.skip(input, pos, input.length);
            return true;
        } catch (CborRuntimeException e) {
            return false;
        }
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
}
