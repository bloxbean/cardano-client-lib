package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleTemporalTypesMetadataConverter}.
 */
class SampleTemporalTypesMetadataConverterTest {

    SampleTemporalTypesMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleTemporalTypesMetadataConverter();
    }

    // =========================================================================
    // Instant — DEFAULT (epoch seconds BigInteger)
    // =========================================================================

    @Nested
    class InstantDefault {

        @Test
        void roundTrip() {
            // truncate to seconds — DEFAULT enc stores epoch seconds only
            Instant now = Instant.ofEpochSecond(Instant.now().getEpochSecond());
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setCreatedAt(now);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(now, restored.getCreatedAt());
        }

        @Test
        void storedAsBigInteger() {
            Instant ts = Instant.ofEpochSecond(1_700_000_000L);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setCreatedAt(ts);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(1_700_000_000L), map.get("createdAt"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setCreatedAt(null);

            assertNull(converter.toMetadataMap(obj).get("createdAt"));
        }
    }

    // =========================================================================
    // Instant — STRING (ISO-8601 text)
    // =========================================================================

    @Nested
    class InstantString {

        @Test
        void roundTrip() {
            Instant ts = Instant.parse("2024-01-15T10:30:00Z");
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setCreatedAtAsString(ts);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(ts, restored.getCreatedAtAsString());
        }

        @Test
        void storedAsIso8601String() {
            Instant ts = Instant.parse("2024-06-01T00:00:00Z");
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setCreatedAtAsString(ts);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("2024-06-01T00:00:00Z", map.get("createdAtStr"));
        }
    }

    // =========================================================================
    // LocalDate — DEFAULT (epoch days BigInteger)
    // =========================================================================

    @Nested
    class LocalDateDefault {

        @Test
        void roundTrip() {
            LocalDate date = LocalDate.of(2024, 3, 15);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setBirthDate(date);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(date, restored.getBirthDate());
        }

        @Test
        void storedAsEpochDays() {
            LocalDate date = LocalDate.of(2024, 1, 1);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setBirthDate(date);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(date.toEpochDay()), map.get("birthDate"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setBirthDate(null);

            assertNull(converter.toMetadataMap(obj).get("birthDate"));
        }
    }

    // =========================================================================
    // LocalDate — STRING (ISO-8601 date text)
    // =========================================================================

    @Nested
    class LocalDateString {

        @Test
        void roundTrip() {
            LocalDate date = LocalDate.of(1990, 7, 4);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setBirthDateAsString(date);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(date, restored.getBirthDateAsString());
        }

        @Test
        void storedAsIso8601DateString() {
            LocalDate date = LocalDate.of(2000, 12, 31);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setBirthDateAsString(date);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("2000-12-31", map.get("birthDateStr"));
        }
    }

    // =========================================================================
    // LocalDateTime — DEFAULT (ISO-8601 text)
    // =========================================================================

    @Nested
    class LocalDateTimeDefault {

        @Test
        void roundTrip() {
            LocalDateTime dt = LocalDateTime.of(2024, 5, 20, 14, 30, 0);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setScheduledAt(dt);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(dt, restored.getScheduledAt());
        }

        @Test
        void storedAsIso8601String() {
            LocalDateTime dt = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setScheduledAt(dt);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("2024-01-01T00:00", map.get("scheduledAt"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setScheduledAt(null);

            assertNull(converter.toMetadataMap(obj).get("scheduledAt"));
        }
    }

    // =========================================================================
    // Date — DEFAULT (epoch millis BigInteger)
    // =========================================================================

    @Nested
    class LegacyDateDefault {

        @Test
        void roundTrip() {
            Date date = new Date(1_700_000_000_000L);
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setLegacyDate(date);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(date, restored.getLegacyDate());
        }

        @Test
        void storedAsEpochMillis() {
            Date date = new Date(0L); // epoch
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setLegacyDate(date);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.ZERO, map.get("legacyDate"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setLegacyDate(null);

            assertNull(converter.toMetadataMap(obj).get("legacyDate"));
        }
    }

    // =========================================================================
    // Date — STRING (ISO-8601 via toInstant())
    // =========================================================================

    @Nested
    class LegacyDateString {

        @Test
        void roundTrip() {
            // Use a millisecond-precision date so Instant.parse round-trip is exact
            Date date = Date.from(Instant.parse("2024-03-10T12:00:00Z"));
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setLegacyDateAsString(date);

            SampleTemporalTypes restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(date, restored.getLegacyDateAsString());
        }

        @Test
        void storedAsIso8601String() {
            Date date = Date.from(Instant.parse("2024-01-01T00:00:00Z"));
            SampleTemporalTypes obj = new SampleTemporalTypes();
            obj.setLegacyDateAsString(date);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("2024-01-01T00:00:00Z", map.get("legacyDateStr"));
        }
    }
}
