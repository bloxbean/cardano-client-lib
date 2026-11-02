package com.bloxbean.cardano.client.plutus.spec;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlutusDataMap} behaves as the {@link LinkedHashMap} it replaces.
 */
class PlutusDataMapTest {
    private static final PlutusData ONE = BigIntPlutusData.of(1);
    private static final PlutusData TWO = BigIntPlutusData.of(2);
    private static final PlutusData LIST_KEY = ListPlutusData.of(BigIntPlutusData.of(7));

    @Test
    void behavesAsALinkedHashMap() {
        PlutusDataMap map = new PlutusDataMap();
        Map<PlutusData, PlutusData> expected = new LinkedHashMap<>();
        for (Map<PlutusData, PlutusData> m : List.<Map<PlutusData, PlutusData>>of(map, expected)) {
            assertThat(m.put(ONE, TWO)).isNull();
            assertThat(m.put(LIST_KEY, ONE)).isNull();
            assertThat(m.put(null, TWO)).isNull();
            // a repeated key keeps its first position and takes the last value
            assertThat(m.put(BigIntPlutusData.of(BigInteger.ONE), ONE)).isEqualTo(TWO);
        }
        assertThat(new ArrayList<>(map.keySet())).containsExactlyElementsOf(expected.keySet());
        assertThat(new ArrayList<>(map.values())).containsExactlyElementsOf(expected.values());
        assertThat(map).isEqualTo(expected);
        assertThat(expected).isEqualTo(map);
        assertThat(map.hashCode()).isEqualTo(expected.hashCode());

        assertThat(map.get(ListPlutusData.of(BigIntPlutusData.of(7)))).isEqualTo(ONE);
        assertThat(map.containsKey(null)).isTrue();
        assertThat(map.get("not plutus data")).isNull();
        assertThat(map.remove(ONE)).isEqualTo(ONE);
        assertThat(map).hasSize(2);
    }

    @Test
    void entriesWriteThrough() {
        PlutusDataMap map = new PlutusDataMap();
        map.put(ONE, ONE);
        map.put(TWO, TWO);
        Iterator<Map.Entry<PlutusData, PlutusData>> entries = map.entrySet().iterator();
        entries.next().setValue(TWO);
        entries.next();
        entries.remove();
        assertThat(map).containsExactly(Map.entry(ONE, TWO));
    }

    @Test
    void mapPlutusDataUsesItAndHashesAlikeWithAPlainMap() {
        MapPlutusData cached = new MapPlutusData();
        cached.put(LIST_KEY, ONE);
        cached.put(ONE, TWO);
        assertThat(cached.getMap()).isInstanceOf(PlutusDataMap.class);
        Map<PlutusData, PlutusData> plain = new LinkedHashMap<>();
        plain.put(LIST_KEY, ONE);
        plain.put(ONE, TWO);
        MapPlutusData uncached = new MapPlutusData(plain);
        assertThat(cached).isEqualTo(uncached);
        assertThat(cached.hashCode()).isEqualTo(uncached.hashCode());
    }
}
