package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The iterative Plutus data codec against the recursive one it replaced, on every datum, redeemer and inline datum of the
 * real transactions and blocks, and on seeded random Plutus data: equal models, equal serialization, equal datum hash.
 */
class PlutusDataCodecDifferentialTest {

    @Test
    void realDatumsAndRedeemersDecodeAndEncodeAsBefore() throws Exception {
        int compared = 0;
        for (PlutusDataCorpus.Datum datum : PlutusDataCorpus.all()) {
            PlutusData legacy;
            try {
                legacy = LegacyPlutusData.deserialize(CborDecoder.decode(datum.cbor()).get(0));
            } catch (StackOverflowError e) {
                continue;
            }
            assertSame(datum.toString(), datum.cbor(), legacy);
            compared++;
        }
        assertThat(compared).isGreaterThan(50);
    }

    @Test
    void randomPlutusDataDecodesAndEncodesAsBefore() throws Exception {
        RandomPlutusData generator = new RandomPlutusData(20261002);
        for (int i = 0; i < 20_000; i++) {
            byte[] cbor = generator.next(5);
            assertSame(encodeHexString(cbor), cbor, LegacyPlutusData.deserialize(CborDecoder.decode(cbor).get(0)));
        }
    }

    /**
     * Random data under a random chain of 25 to 40 containers, so around the depth where conversion switches from the
     * recursive to the iterative path ({@code PlutusDataCodec.SHALLOW_DEPTH}), on either side and inside the random part.
     */
    @Test
    void randomPlutusDataAroundTheRecursionLimitDecodesAndEncodesAsBefore() throws Exception {
        // prefix and suffix of a container around one item: a list, constructors in the compact and general form, a map
        // value and key, an indefinite list
        String[][] wrappers = {{"81", ""}, {"d87981", ""}, {"d866820081", ""}, {"a100", ""}, {"a1", "00"}, {"9f", "ff"}};
        RandomPlutusData generator = new RandomPlutusData(681_685);
        Random random = new Random(685);
        for (int i = 0; i < 5_000; i++) {
            StringBuilder prefix = new StringBuilder();
            StringBuilder suffix = new StringBuilder();
            int levels = 25 + random.nextInt(16);
            for (int level = 0; level < levels; level++) {
                String[] wrapper = wrappers[random.nextInt(wrappers.length)];
                prefix.append(wrapper[0]);
                suffix.insert(0, wrapper[1]);
            }
            byte[] cbor = decodeHexString(prefix + encodeHexString(generator.next(5)) + suffix);
            assertSame(encodeHexString(cbor), cbor, LegacyPlutusData.deserialize(CborDecoder.decode(cbor).get(0)));
        }
    }

    private static int mapEntries(PlutusData data) {
        if (data instanceof MapPlutusData) {
            int count = ((MapPlutusData) data).getMap().size();
            for (java.util.Map.Entry<PlutusData, PlutusData> entry : ((MapPlutusData) data).getMap().entrySet())
                count += mapEntries(entry.getKey()) + mapEntries(entry.getValue());
            return count;
        }
        if (data instanceof ConstrPlutusData)
            return mapEntries(((ConstrPlutusData) data).getData());
        if (data instanceof ListPlutusData)
            return ((ListPlutusData) data).getPlutusDataList().stream().mapToInt(PlutusDataCodecDifferentialTest::mapEntries).sum();
        return 0;
    }

    private static void assertSame(String what, byte[] cbor, PlutusData legacy) throws Exception {
        PlutusData decoded = PlutusData.deserialize(cbor);
        assertThat(decoded).as(what).isEqualTo(legacy);
        assertThat(legacy).as(what).isEqualTo(decoded);
        assertThat(decoded.hashCode()).as(what).isEqualTo(legacy.hashCode());
        assertThat(PlutusData.deserialize(cbor)).as(what).isEqualTo(decoded);

        // serializing again gives the same bytes
        byte[] bytes = decoded.serializeToBytes();
        assertThat(PlutusData.deserialize(bytes).serializeToBytes()).as(what).isEqualTo(bytes);

        DataItem legacyItem = LegacyPlutusData.serialize(legacy);
        byte[] legacyBytes = CborSerializationUtil.serialize(legacyItem);
        if (!Arrays.equals(bytes, legacyBytes)) {
            // only where the old serialization lost map entries: a cbor-java map treats any two big integers over 64
            // bits of the same sign as one key (ChunkedByteString equality ignores the chunks)
            assertThat(mapEntries(PlutusData.deserialize(legacyBytes))).as(what).isLessThan(mapEntries(legacy));
            assertThat(mapEntries(PlutusData.deserialize(bytes))).as(what).isEqualTo(mapEntries(decoded));
            return;
        }
        assertThat(CborSerializationUtil.serialize(decoded.serialize(), false))
                .as(what).isEqualTo(CborSerializationUtil.serialize(legacyItem, false));
        assertThat(decoded.getDatumHashAsBytes()).as(what).isEqualTo(Blake2bUtil.blake2bHash256(legacyBytes));
    }
}
