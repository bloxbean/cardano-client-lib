package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for polymorphic field support via {@code @MetadataDiscriminator}.
 */
class SamplePolymorphicMetadataConverterTest {

    SamplePolymorphicMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SamplePolymorphicMetadataConverter();
    }

    @Nested
    class ImageMediaRoundTrip {

        @Test
        void roundTrip() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setName("NFT-001");
            obj.setMedia(new SampleImageMedia("https://example.com/img.png", 1920, 1080));

            SamplePolymorphic restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("NFT-001", restored.getName());
            assertInstanceOf(SampleImageMedia.class, restored.getMedia());
            SampleImageMedia img = (SampleImageMedia) restored.getMedia();
            assertEquals("https://example.com/img.png", img.getUrl());
            assertEquals(1920, img.getWidth());
            assertEquals(1080, img.getHeight());
        }

        @Test
        void discriminatorKeyPresent() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setMedia(new SampleImageMedia("url", 100, 200));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataMap mediaMap = (MetadataMap) map.get("media");

            assertNotNull(mediaMap);
            assertEquals("image", mediaMap.get("type"));
        }
    }

    @Nested
    class AudioMediaRoundTrip {

        @Test
        void roundTrip() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setName("Song-001");
            obj.setMedia(new SampleAudioMedia("https://example.com/audio.mp3", 240));

            SamplePolymorphic restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("Song-001", restored.getName());
            assertInstanceOf(SampleAudioMedia.class, restored.getMedia());
            SampleAudioMedia audio = (SampleAudioMedia) restored.getMedia();
            assertEquals("https://example.com/audio.mp3", audio.getUrl());
            assertEquals(240, audio.getDuration());
        }

        @Test
        void discriminatorKeyPresent() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setMedia(new SampleAudioMedia("url", 60));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataMap mediaMap = (MetadataMap) map.get("media");

            assertNotNull(mediaMap);
            assertEquals("audio", mediaMap.get("type"));
        }
    }

    @Nested
    class NullAndUnknown {

        @Test
        void nullMedia_keyAbsent() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setName("NoMedia");
            obj.setMedia(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("media"));
            assertEquals("NoMedia", map.get("name"));
        }

        @Test
        void nullMedia_deserializedAsNull() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setName("NoMedia");

            MetadataMap map = converter.toMetadataMap(obj);
            SamplePolymorphic restored = converter.fromMetadataMap(map);

            assertEquals("NoMedia", restored.getName());
            assertNull(restored.getMedia());
        }

        @Test
        void unknownDiscriminator_mediaRemainsNull() {
            SamplePolymorphic obj = new SamplePolymorphic();
            obj.setMedia(new SampleImageMedia("url", 10, 20));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataMap mediaMap = (MetadataMap) map.get("media");
            // Replace with unknown discriminator
            mediaMap.put("type", "video");

            SamplePolymorphic restored = converter.fromMetadataMap(map);

            assertNull(restored.getMedia(), "Unknown discriminator should leave field null");
        }
    }
}
