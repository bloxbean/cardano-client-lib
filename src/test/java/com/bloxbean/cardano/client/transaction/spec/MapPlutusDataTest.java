package com.bloxbean.cardano.client.transaction.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapPlutusDataTest {

    @Test
    void testEquals() {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BytesPlutusData.of("key1"), BytesPlutusData.of("value1"));
        mapPlutusData.put(BytesPlutusData.of("key2"), BigIntPlutusData.of(300000));

        MapPlutusData mapPlutusData2 = new MapPlutusData();
        mapPlutusData2.put(BytesPlutusData.of("key1"), BytesPlutusData.of("value1"));
        mapPlutusData2.put(BytesPlutusData.of("key2"), BigIntPlutusData.of(300000));

        assertThat(mapPlutusData).isEqualTo(mapPlutusData2);
    }

    @Test
    void testNotEquals() {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BytesPlutusData.of("key1"), BytesPlutusData.of("value1"));
        mapPlutusData.put(BytesPlutusData.of("key2"), BigIntPlutusData.of(300000));

        MapPlutusData mapPlutusData2 = new MapPlutusData();
        mapPlutusData2.put(BytesPlutusData.of("key1"), BytesPlutusData.of("value1"));
        mapPlutusData2.put(BytesPlutusData.of("key2"), BigIntPlutusData.of(222));

        assertThat(mapPlutusData).isNotEqualTo(mapPlutusData2);
    }
}
