package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code @MetadataType(label = 721)} label support.
 */
class SampleLabeledMetadataConverterTest {

    SampleLabeledMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleLabeledMetadataConverter();
    }

    @Test
    void toMetadata_fromMetadata_roundTrip() {
        SampleLabeled obj = new SampleLabeled();
        obj.setName("CoolNFT");
        obj.setDescription("A very cool NFT");

        Metadata metadata = converter.toMetadata(obj);
        SampleLabeled restored = converter.fromMetadata(metadata);

        assertEquals("CoolNFT", restored.getName());
        assertEquals("A very cool NFT", restored.getDescription());
    }

    @Test
    void toMetadata_labelKeyPresent() {
        SampleLabeled obj = new SampleLabeled();
        obj.setName("TestNFT");

        Metadata metadata = converter.toMetadata(obj);
        Object raw = metadata.get(BigInteger.valueOf(721));

        assertNotNull(raw);
        assertInstanceOf(MetadataMap.class, raw);
        MetadataMap map = (MetadataMap) raw;
        assertEquals("TestNFT", map.get("name"));
    }

    @Test
    void toMetadataMap_stillWorks() {
        SampleLabeled obj = new SampleLabeled();
        obj.setName("DirectMap");

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("DirectMap", map.get("name"));
    }

    @Test
    void fromMetadata_wrongLabel_throws() {
        SampleLabeled obj = new SampleLabeled();
        obj.setName("Test");
        Metadata metadata = converter.toMetadata(obj);
        // Remove label 721 and put under wrong label
        metadata.remove(BigInteger.valueOf(721));
        metadata.put(BigInteger.valueOf(999), "wrong");

        assertThrows(IllegalArgumentException.class, () -> converter.fromMetadata(metadata));
    }
}
