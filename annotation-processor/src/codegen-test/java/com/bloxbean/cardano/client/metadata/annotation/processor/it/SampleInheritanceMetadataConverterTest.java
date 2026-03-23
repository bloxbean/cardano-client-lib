package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for inheritance support — superclass fields included in converter.
 */
class SampleInheritanceMetadataConverterTest {

    @Nested
    class ChildOfPlainBase {

        SampleChildMetadataMetadataConverter converter = new SampleChildMetadataMetadataConverter();

        @Test
        void childAndParentFields_roundTrip() {
            SampleChildMetadata obj = new SampleChildMetadata();
            obj.setName("TokenX");
            obj.setDescription("A token");
            obj.setVersion("1.0");
            obj.setAuthor("Alice");

            SampleChildMetadata restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("TokenX", restored.getName());
            assertEquals("A token", restored.getDescription());
            assertEquals("1.0", restored.getVersion());
            assertEquals("Alice", restored.getAuthor());
        }

        @Test
        void parentFieldsPresentInMap() {
            SampleChildMetadata obj = new SampleChildMetadata();
            obj.setName("Test");
            obj.setVersion("2.0");
            obj.setAuthor("Bob");

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("Test", map.get("name"));
            assertEquals("2.0", map.get("version"));
            assertEquals("Bob", map.get("author"));
        }

        @Test
        void nullParentFields_keyAbsent() {
            SampleChildMetadata obj = new SampleChildMetadata();
            obj.setName("Test");
            // version and author left null

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("Test", map.get("name"));
            assertNull(map.get("version"));
            assertNull(map.get("author"));
        }
    }

    @Nested
    class ShadowedField {

        SampleChildShadowMetadataConverter converter = new SampleChildShadowMetadataConverter();

        @Test
        void childFieldTakesPrecedence() {
            SampleChildShadow obj = new SampleChildShadow();
            obj.setVersion("child-v");
            obj.setTitle("Shadow Test");

            SampleChildShadow restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("child-v", restored.getVersion());
            assertEquals("Shadow Test", restored.getTitle());
        }

        @Test
        void parentAuthorFieldIncluded() {
            SampleChildShadow obj = new SampleChildShadow();
            obj.setVersion("1.0");
            obj.setTitle("Test");
            obj.setAuthor("Charlie");

            SampleChildShadow restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("Charlie", restored.getAuthor());
        }
    }
}
