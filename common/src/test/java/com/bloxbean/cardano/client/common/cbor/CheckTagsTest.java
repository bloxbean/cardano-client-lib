package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Special;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CborSerializationUtil#checkTags(CborSpan)}: with the walker of a span, it rejects exactly what decoding to a
 * tree rejects, on hand-made items and on random mutations of them.
 */
class CheckTagsTest {
    private static final List<String> ITEMS = List.of(
            "d81e820102", "d81e9f0102ff", "d81e82c10102", "d81e8201c102", "d81ed81e820102", "d81ec1820102",
            "c1d81e820102", "d81e822001", "d81e820120", "d81e8201181f", "d81e82011b0000000000000002",
            "d81e820100", "d81e82011800", "d81e8201c100", "d81e82011b0000000000000000",
            "d81e00", "d81e80", "d81e8101", "d81e83010203", "d81ea0", "d81e4100", "d81ef6",
            "d81e82c2410101", "d81e8201c24101", "d81e8201f6", "d81e82016161", "d81e8201d81e820102",
            "d8268261616162", "d8269f61616162ff", "d826827f6161ff6162", "d82682c161616162", "d826d826826161616162",
            "d82600", "d82680", "d826816161", "d826820161", "d82682616101", "d81ed8268261616162", "d826d81e820102",
            "8200d81e820102", "81d81e00", "a1d81e00d81e820102", "a1d81e82010200", "a100d81e00", "a1d81e00",
            "9fd81e00ff", "bf00d81e00ff", "5f4100ff", "7f6161ff", "4100", "6161", "dbffffffffffffffff00", "dbffffffffffffffffd81e00",
            "d81edbffffffffffffffff820102", "c0c1c2820102", "f6", "f93e00");

    private static boolean decodes(byte[] bytes) {
        try {
            CborSerializationUtil.deserialize(bytes);
            return true;
        } catch (CborRuntimeException e) {
            return false;
        }
    }

    private static boolean walksAndChecks(byte[] bytes) {
        try {
            CborSerializationUtil.checkTags(CborSpan.of(bytes));
            return true;
        } catch (CborRuntimeException e) {
            return false;
        }
    }

    @Test
    void handMadeItems() {
        for (String item : ITEMS) {
            byte[] bytes = decodeHexString(item);
            assertThat(walksAndChecks(bytes)).as(item).isEqualTo(decodes(bytes));
        }
        assertThat(walksAndChecks(decodeHexString("d81e820102"))).isTrue();
        assertThat(walksAndChecks(decodeHexString("d81e00"))).isFalse();
        assertThat(walksAndChecks(decodeHexString("d82600"))).isFalse();
        assertThat(walksAndChecks(decodeHexString("d81e820100"))).isFalse();
    }

    @Test
    void randomMutations() {
        Random random = new Random(20261106);
        int rejected = 0;
        for (int i = 0; i < 200_000; i++) {
            byte[] bytes = decodeHexString(ITEMS.get(random.nextInt(ITEMS.size())));
            for (int change = random.nextInt(3); change >= 0; change--)
                bytes[random.nextInt(bytes.length)] = (byte) random.nextInt(256);
            // bytes after the first item are outside a span
            int end;
            try {
                end = CborSpan.skip(bytes, 0, bytes.length);
            } catch (CborRuntimeException e) {
                continue;
            }
            byte[] item = Arrays.copyOf(bytes, end);
            boolean decodes = decodes(item);
            assertThat(walksAndChecks(item)).as(encodeHexString(item)).isEqualTo(decodes);
            rejected += decodes ? 0 : 1;
        }
        assertThat(rejected).isGreaterThan(1000);
    }

    @Test
    void aSpanDecodesWithoutACopyAsItsBytesDo() throws Exception {
        CborSpan array = CborSpan.of(decodeHexString("82" + "d81e820102" + "a10001"));
        for (CborSpan item : array.items()) {
            DataItem decoded = CborSerializationUtil.deserialize(item);
            assertThat(CborSerializationUtil.serialize(decoded))
                    .isEqualTo(CborSerializationUtil.serialize(CborSerializationUtil.deserialize(item.bytes())));
        }
        assertThatThrownBy(() -> CborSerializationUtil.deserialize(CborSpan.of(decodeHexString("d81e00"))))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aTagNumberOfAllOnesIsATag() {
        CborSpan tagged = CborSpan.of(decodeHexString("dbffffffffffffffff00"));
        assertThat(tagged.isTagged()).isTrue();
        assertThat(tagged.tag()).isEqualTo(-1);
        assertThat(CborSpan.of(decodeHexString("00")).isTagged()).isFalse();
    }

    @Test
    void aTagNumberOfAllOnesIsWrittenAsIs() throws Exception {
        byte[] tagged = decodeHexString("dbffffffffffffffff4100");
        assertThat(CborSerializationUtil.serialize(CborSerializationUtil.deserialize(tagged), false)).isEqualTo(tagged);
    }

    /**
     * cbor-java's decoder sets a tag on its shared null when it reads a tagged one, for the whole JVM. What such a tag
     * is on is no item being written, and a tree of nulls stays a tree of nulls.
     */
    @Test
    void aTagCborJavaLeftOnASharedValueIsNotWritten() throws Exception {
        try {
            for (String tagged : List.of("d818f6", "d818f5", "d818f4", "d818f7"))
                new CborDecoder(new ByteArrayInputStream(decodeHexString(tagged))).decodeNext();
            assertThat(SimpleValue.NULL.hasTag()).isTrue();
            byte[] items = decodeHexString("84f6f5f4f7");
            Array decoded = (Array) CborSerializationUtil.deserialize(items);
            assertThat(CborSerializationUtil.serialize(decoded, false)).isEqualTo(items);
            assertThat(CborSerializationUtil.serialize(decoded)).isEqualTo(items);
            // a tag on a null of its own stays
            byte[] own = decodeHexString("81d818f6");
            assertThat(CborSerializationUtil.serialize(CborSerializationUtil.deserialize(own), false)).isEqualTo(own);
        } finally {
            SimpleValue.NULL.removeTag();
            SimpleValue.TRUE.removeTag();
            SimpleValue.FALSE.removeTag();
            SimpleValue.UNDEFINED.removeTag();
            Special.BREAK.removeTag();
        }
    }
}
