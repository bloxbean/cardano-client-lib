package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.Collection;

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

    @Test
    void testLexicographicOrderForKeyInMap_whenIntKeys() throws Exception {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BigIntPlutusData.of(148218514), BytesPlutusData.of("value1"));
        mapPlutusData.put(BigIntPlutusData.of(160376066), BytesPlutusData.of("value2"));
        mapPlutusData.put(BigIntPlutusData.of(158897178), BytesPlutusData.of("value3"));

        String hex = mapPlutusData.serializeToHex();
        System.out.println(hex);

        //Deserialize and check the keys order
        Map map = (Map)CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex));
        Collection<DataItem> keys = map.getKeys();
        assertThat(keys).hasSize(3);
        assertThat(keys).containsExactly(new UnsignedInteger(148218514), new UnsignedInteger(158897178),
                new UnsignedInteger(160376066));
    }

    @Test
    void testLexicographicOrderForKeyInMap_whenByteKeys() {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BytesPlutusData.of("148218514"), BytesPlutusData.of("value1"));
        mapPlutusData.put(BytesPlutusData.of("160376066"), BytesPlutusData.of("value2"));
        mapPlutusData.put(BytesPlutusData.of("158897178"), BytesPlutusData.of("value3"));

        String hex = mapPlutusData.serializeToHex();
        System.out.println(hex);

        //Deserialize and check the keys order
        Map map = (Map)CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex));
        Collection<DataItem> keys = map.getKeys();
        assertThat(keys).hasSize(3);
        assertThat(keys).containsExactly(new ByteString("148218514".getBytes()), new ByteString("158897178".getBytes()),
                new ByteString("160376066".getBytes()));
    }

    @Test
    void testLexicographicOrderForKeyInMap_whenNegativeKeys() {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BigIntPlutusData.of(148218514), BytesPlutusData.of("value1"));
        mapPlutusData.put(BigIntPlutusData.of(-160376066), BytesPlutusData.of("value2"));
        mapPlutusData.put(BigIntPlutusData.of(-158897178), BytesPlutusData.of("value3"));

        String hex = mapPlutusData.serializeToHex();
        System.out.println(hex);

        //Deserialize and check the keys order
        Map map = (Map)CborSerializationUtil.deserialize(HexUtil.decodeHexString(hex));
        Collection<DataItem> keys = map.getKeys();
        assertThat(keys).hasSize(3);
        assertThat(keys).containsExactly(new UnsignedInteger(148218514), new NegativeInteger(-158897178), new NegativeInteger(-160376066));
    }
}

