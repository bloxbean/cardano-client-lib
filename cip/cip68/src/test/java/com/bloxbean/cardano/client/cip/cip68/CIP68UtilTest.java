package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip68.common.CIP68Util;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CIP68UtilTest {

    @Test
    void addSingleOrMultipleValue() {
        var mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BytesPlutusData.of("key1"), BytesPlutusData.of("value1"));
        mapPlutusData.put(BytesPlutusData.of("key2"), BytesPlutusData.of("value2"));

        CIP68Util.addSingleOrMultipleValues(mapPlutusData, "key3", "value3");
        CIP68Util.addSingleOrMultipleValues(mapPlutusData, "key2", "value22");
        CIP68Util.addSingleOrMultipleValues(mapPlutusData, "key2", "value222");
        CIP68Util.addSingleOrMultipleValues(mapPlutusData, "key3", "value3");

        CIP68Util.addSingleOrMultipleValues(mapPlutusData, "key4", "value4");

        assertEquals(4, mapPlutusData.getMap().size());
        assertThat(mapPlutusData.getMap().get(BytesPlutusData.of("key1"))).isInstanceOf(BytesPlutusData.class);
        assertThat(mapPlutusData.getMap().get(BytesPlutusData.of("key2"))).isInstanceOf(ListPlutusData.class);
        assertThat(((ListPlutusData) mapPlutusData.getMap().get(BytesPlutusData.of("key2"))).getPlutusDataList()).hasSize(3);
        assertThat(mapPlutusData.getMap().get(BytesPlutusData.of("key3"))).isInstanceOf(ListPlutusData.class);
        assertThat(((ListPlutusData) mapPlutusData.getMap().get(BytesPlutusData.of("key3"))).getPlutusDataList()).hasSize(2);
        assertThat(mapPlutusData.getMap().get(BytesPlutusData.of("key4"))).isInstanceOf(BytesPlutusData.class);
    }

    @Test
    void toByteString_fromByteString_utf8() {
        var value = "Hello World";
        var bytesPlutusData = CIP68Util.toByteString(value);
        assertEquals(value, CIP68Util.fromByteString(bytesPlutusData));
    }

    @Test
    void toByteString_fromByteString_nonutf8() {
        var value = HexUtil.decodeHexString("0x1234567890abcdef");
        var bytesPlutusData = BytesPlutusData.of(value);

        var value2 = CIP68Util.fromByteString(bytesPlutusData);
        assertThat(value2).isEqualTo(HexUtil.encodeHexString(value, true));
    }
}
