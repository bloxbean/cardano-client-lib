package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleStringCoercibleMetadataConverter}.
 */
class SampleStringCoercibleMetadataConverterIT {

    SampleStringCoercibleMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleStringCoercibleMetadataConverter();
    }

    // =========================================================================
    // URI
    // =========================================================================

    @Nested
    class UriField {

        @Test
        void roundTrip() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setWebsite(URI.create("https://cardano.org/path?q=1"));

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(URI.create("https://cardano.org/path?q=1"), restored.getWebsite());
        }

        @Test
        void storedAsString() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setWebsite(URI.create("ipfs://Qm1234"));

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("ipfs://Qm1234", map.get("website"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setWebsite(null);

            assertNull(converter.toMetadataMap(obj).get("website"));
        }
    }

    // =========================================================================
    // URL
    // =========================================================================

    @Nested
    class UrlField {

        @Test
        void roundTrip() throws MalformedURLException {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setEndpoint(new URL("https://api.cardano.org/v1"));

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(new URL("https://api.cardano.org/v1"), restored.getEndpoint());
        }

        @Test
        void storedAsString() throws MalformedURLException {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setEndpoint(new URL("https://example.com"));

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("https://example.com", map.get("endpoint"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setEndpoint(null);

            assertNull(converter.toMetadataMap(obj).get("endpoint"));
        }
    }

    // =========================================================================
    // UUID
    // =========================================================================

    @Nested
    class UuidField {

        @Test
        void roundTrip() {
            UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setId(uuid);

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(uuid, restored.getId());
        }

        @Test
        void storedAsString() {
            UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setId(uuid);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("00000000-0000-0000-0000-000000000001", map.get("id"));
        }

        @Test
        void randomUuid_roundTrip() {
            UUID uuid = UUID.randomUUID();
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setId(uuid);

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(uuid, restored.getId());
        }

        @Test
        void null_keyAbsentInMap() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setId(null);

            assertNull(converter.toMetadataMap(obj).get("id"));
        }
    }

    // =========================================================================
    // Currency
    // =========================================================================

    @Nested
    class CurrencyField {

        @Test
        void roundTrip() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setCurrency(Currency.getInstance("USD"));

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Currency.getInstance("USD"), restored.getCurrency());
        }

        @Test
        void storedAsCurrencyCode() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setCurrency(Currency.getInstance("EUR"));

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("EUR", map.get("currency"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setCurrency(null);

            assertNull(converter.toMetadataMap(obj).get("currency"));
        }
    }

    // =========================================================================
    // Locale
    // =========================================================================

    @Nested
    class LocaleField {

        @Test
        void roundTrip() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setLocale(Locale.forLanguageTag("en-US"));

            SampleStringCoercible restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Locale.forLanguageTag("en-US"), restored.getLocale());
        }

        @Test
        void storedAsLanguageTag() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setLocale(Locale.forLanguageTag("de-DE"));

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("de-DE", map.get("locale"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleStringCoercible obj = new SampleStringCoercible();
            obj.setLocale(null);

            assertNull(converter.toMetadataMap(obj).get("locale"));
        }
    }
}
