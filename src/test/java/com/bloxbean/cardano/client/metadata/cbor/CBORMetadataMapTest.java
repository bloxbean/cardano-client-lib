package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CBORMetadataMapTest {

    @Test
    void testRemove() throws CborException {
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        metadataMap.put("key1", "value1");
        metadataMap.put("key2", BigInteger.valueOf(123));
        metadataMap.put("key3", "value3");
        metadataMap.put(new BigInteger("123456"), "value4");

        assertThat(metadataMap.keys()).hasSize(4);
        metadataMap.remove("key1");
        metadataMap.remove(new BigInteger("123456"));

        assertThat(metadataMap.keys()).hasSize(2);
        assertThat(metadataMap.get("key2")).isEqualTo(BigInteger.valueOf(123));
        assertThat(metadataMap.get("key3")).isEqualTo("value3");

        System.out.println(metadataMap.toJson());
    }

}
