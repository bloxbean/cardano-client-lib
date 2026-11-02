package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

class EncodedKeyMapTest {

    @Test
    void putGetRemoveKeepInsertionOrder() {
        EncodedKeyMap map = new EncodedKeyMap();
        map.put(new UnsignedInteger(2), new UnicodeString("b"));
        map.put(new UnsignedInteger(1), new UnicodeString("a"));
        assertThat(map.getKeys()).containsExactly(new UnsignedInteger(2), new UnsignedInteger(1));
        assertThat(map.getValues()).containsExactly(new UnicodeString("b"), new UnicodeString("a"));
        assertThat(map.get(new UnsignedInteger(1))).isEqualTo(new UnicodeString("a"));
        assertThat(map.get(new UnsignedInteger(3))).isNull();
        assertThat(map.remove(new UnsignedInteger(2))).isEqualTo(new UnicodeString("b"));
        assertThat(map.getKeys()).containsExactly(new UnsignedInteger(1));
    }

    @Test
    void repeatedKeyKeepsFirstPositionAndLastValue() {
        EncodedKeyMap map = new EncodedKeyMap();
        map.put(new UnsignedInteger(1), new UnsignedInteger(10));
        map.put(new UnsignedInteger(2), new UnsignedInteger(20));
        map.put(new UnsignedInteger(1), new UnsignedInteger(11));
        assertThat(map.getKeys()).containsExactly(new UnsignedInteger(1), new UnsignedInteger(2));
        assertThat(map.get(new UnsignedInteger(1))).isEqualTo(new UnsignedInteger(11));
    }

    @Test
    void keysMatchExactlyWhenCborJavaConsidersThemEqual() {
        EncodedKeyMap map = new EncodedKeyMap();
        // a map key matches regardless of entry order, as cbor-java's map equality does
        map.put(CborSerializationUtil.deserialize(decodeHexString("a201020304")), new UnsignedInteger(1));
        assertThat(map.get(CborSerializationUtil.deserialize(decodeHexString("a203040102")))).isEqualTo(new UnsignedInteger(1));
        // arrays are ordered; chunked flags and tags are part of the key
        map.put(CborSerializationUtil.deserialize(decodeHexString("820102")), new UnsignedInteger(2));
        assertThat(map.get(CborSerializationUtil.deserialize(decodeHexString("820201")))).isNull();
        assertThat(map.get(CborSerializationUtil.deserialize(decodeHexString("9f0102ff")))).isNull();
        assertThat(map.get(CborSerializationUtil.deserialize(decodeHexString("c1820102")))).isNull();
        // a number matches whatever its encoded width
        map.put(new UnsignedInteger(0), new UnsignedInteger(3));
        assertThat(map.get(CborSerializationUtil.deserialize(decodeHexString("1b0000000000000000")))).isEqualTo(new UnsignedInteger(3));
    }

    @Test
    void equalsAndHashCodeAgreeWithCborJavaMaps() {
        Map plain = new Map();
        plain.put(new UnsignedInteger(1), new ByteString(new byte[]{1}));
        plain.put(new ByteString(new byte[]{2}), new Array().add(new UnsignedInteger(3)));
        EncodedKeyMap map = new EncodedKeyMap();
        map.put(new ByteString(new byte[]{2}), new Array().add(new UnsignedInteger(3)));
        map.put(new UnsignedInteger(1), new ByteString(new byte[]{1}));

        assertThat(map.equals(plain)).isTrue();
        assertThat(map.hashCode()).isEqualTo(plain.hashCode());
        // a plain cbor-java map compares its private storage, so it never equals this subclass (documented)
        assertThat(plain.equals(map)).isFalse();

        map.setChunked(true);
        assertThat(map.equals(plain)).isFalse();
    }

    @Test
    void deeplyNestedKeysAreStoredAndFound() {
        int depth = 100_000;
        byte[] key = new byte[depth + 1];
        java.util.Arrays.fill(key, 0, depth, (byte) 0x81);
        DataItem deepKey = CborSerializationUtil.deserialize(key);

        EncodedKeyMap map = new EncodedKeyMap();
        map.put(deepKey, new UnsignedInteger(1));
        map.put(new UnsignedInteger(7), new UnsignedInteger(2));
        assertThat(map.get(CborSerializationUtil.deserialize(key))).isEqualTo(new UnsignedInteger(1));
        assertThat(List.copyOf(map.getKeys()).get(0)).isSameAs(deepKey);
    }

    @Test
    void encodesLikeAPlainMapWithTheSameEntries() throws Exception {
        Map plain = new Map();
        EncodedKeyMap map = new EncodedKeyMap();
        for (int i = 30; i >= 0; i -= 3) {
            plain.put(new UnsignedInteger(i), new UnicodeString("v" + i));
            map.put(new UnsignedInteger(i), new UnicodeString("v" + i));
        }
        assertThat(CborSerializationUtil.serialize(map)).isEqualTo(LegacyCborEncoder.encode(plain, true));
        assertThat(CborSerializationUtil.serialize(map, false)).isEqualTo(LegacyCborEncoder.encode(plain, false));
    }

    @Test
    void aRepeatedKeyIsNoted() {
        // {1: 0, 1 (non-minimal): 2}: kept once, with the last value
        EncodedKeyMap repeated = (EncodedKeyMap) CborSerializationUtil.deserialize(decodeHexString("a2" + "0100" + "1801" + "02"));
        assertThat(repeated.hasRepeatedKey()).isTrue();
        assertThat(repeated.getKeys()).hasSize(1);
        EncodedKeyMap distinct = (EncodedKeyMap) CborSerializationUtil.deserialize(decodeHexString("a2" + "0100" + "0201"));
        assertThat(distinct.hasRepeatedKey()).isFalse();
        // a tagged key is another key
        assertThat(((EncodedKeyMap) CborSerializationUtil.deserialize(decodeHexString("a2" + "0100" + "c10100"))).hasRepeatedKey()).isFalse();
    }
}
