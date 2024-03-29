package com.bloxbean.cardano.client.metadata.cbor;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;


class CBORMetadataTest {

    @Test
    void testRemove() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(1), "value1");
        metadata.put(BigInteger.valueOf(2), "value2");
        metadata.put(BigInteger.valueOf(3), "value3");
        metadata.put(BigInteger.valueOf(4), "value4");

        assertThat(metadata.keys()).hasSize(4);

        metadata.remove(BigInteger.valueOf(3));
        metadata.remove(BigInteger.valueOf(1));

        assertThat(metadata.keys()).hasSize(2);
        assertNull(metadata.get(BigInteger.valueOf(1)));
        assertNull(metadata.get(BigInteger.valueOf(3)));

        assertThat(metadata.get(BigInteger.valueOf(2))).isEqualTo("value2");
        assertThat(metadata.get(BigInteger.valueOf(4))).isEqualTo("value4");
    }

    @Test
    void testMerge() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(1), "value1");
        metadata.put(BigInteger.valueOf(2), "value2");
        metadata.put(BigInteger.valueOf(3), "value3");

        Metadata metadata1 = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(4), "value4");
        metadata.put(BigInteger.valueOf(5), "value5");
        metadata.put(BigInteger.valueOf(6), "value6");

        Metadata mergeMetadata = (Metadata) metadata.merge(metadata1);

        assertThat(mergeMetadata.getData().getKeys()).hasSize(6);
        assertThat(mergeMetadata.get(BigInteger.valueOf(1))).isEqualTo("value1");
        assertThat(mergeMetadata.get(BigInteger.valueOf(2))).isEqualTo("value2");
        assertThat(mergeMetadata.get(BigInteger.valueOf(4))).isEqualTo("value4");
        assertThat(mergeMetadata.get(BigInteger.valueOf(5))).isEqualTo("value5");
    }

}
