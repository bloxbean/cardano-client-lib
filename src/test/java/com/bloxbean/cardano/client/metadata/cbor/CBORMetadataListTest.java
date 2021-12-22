package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CBORMetadataListTest {

    @Test
    void testRemove() {
        CBORMetadataList list = new CBORMetadataList();
        list.add(BigInteger.valueOf(1));
        list.add(BigInteger.valueOf(4));
        list.add("value3");
        list.add("value4");
        list.add("value5");
        list.add("value6");
        list.add("value7");
        System.out.println(list.toJson());
        assertThat(list.size()).isEqualTo(7);

        list.removeItem("value5");
        list.removeItem(BigInteger.valueOf(4));

        assertThat(list.size()).isEqualTo(5);
        assertThat(list.getValueAt(4)).isNotEqualTo("value5");
        assertThat(list.getValueAt(4)).isEqualTo("value7");
    }

    @Test
    void testReplace() {
        CBORMetadataList list = new CBORMetadataList();
        list.add(BigInteger.valueOf(1));
        list.add(BigInteger.valueOf(4));
        list.add("value3");
        list.add("value4");
        list.add("value5");
        list.add("value6");
        list.add("value7");

        System.out.println(list.toJson());
        assertThat(list.size()).isEqualTo(7);

        list.replaceAt(1, "value44");
        list.replaceAt(4, "value55");

        System.out.println(list.toJson());

        assertThat(list.size()).isEqualTo(7);
        assertThat(list.getValueAt(4)).isEqualTo("value55");
        assertThat(list.getValueAt(1)).isEqualTo("value44");
    }

    @Test
    void testReplace_whenListAndMap() {
        CBORMetadataList list = new CBORMetadataList();
        list.add(BigInteger.valueOf(1));
        list.add(BigInteger.valueOf(4));
        list.add("value3");
        list.add("value4");
        list.add("value5");
        list.add("value6");
        list.add("value7");

        System.out.println(list.toJson());
        assertThat(list.size()).isEqualTo(7);

        CBORMetadataList nestedList = new CBORMetadataList();
        nestedList.add("list-val1");
        nestedList.add("list-val2");

        CBORMetadataMap nestedMap = new CBORMetadataMap();
        nestedMap.put("key1", "val1");
        nestedMap.put("key2", "val2");

        list.replaceAt(1, nestedList);
        list.replaceAt(4, nestedMap);

        System.out.println(list.toJson());

        assertThat(list.size()).isEqualTo(7);
        assertThat(((CBORMetadataList)list.getValueAt(1)).getArray()).isEqualTo(nestedList.getArray());
        assertThat(((CBORMetadataMap)list.getValueAt(4)).getMap()).isEqualTo(nestedMap.getMap());

    }

}
