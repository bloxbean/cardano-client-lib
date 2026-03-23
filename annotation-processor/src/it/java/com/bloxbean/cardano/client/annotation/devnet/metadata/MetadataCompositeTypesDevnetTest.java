package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.it.BaseIT;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.helper.JsonNoSchemaToMetadataConverter;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for composite temporal, UUID, and string-coercible type support.
 * Covers List, Optional, Map value, null handling, and string-coercible scalar codegen paths.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetadataCompositeTypesDevnetTest extends BaseIT {

    private BackendService backendService;
    private MetadataCompositeTypes original;
    private MetadataCompositeTypes restored;
    private JsonNode jsonMeta;

    @SneakyThrows
    @BeforeAll
    void setup() {
        initializeAccounts();
        backendService = getBackendService();
        topupAllTestAccounts();

        original = buildOriginal();

        var converter = new MetadataCompositeTypesMetadataConverter();
        Metadata metadata = converter.toMetadata(original);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(backendService);
        Tx tx = new Tx()
                .payToAddress(address2, Amount.ada(1.5))
                .attachMetadata(metadata)
                .from(address1);

        Result<String> result = quickTxBuilder.compose(tx)
                .withSigner(SignerProviders.signerFrom(account1))
                .completeAndWait(System.out::println);

        assertTrue(result.isSuccessful(), "Transaction should succeed: " + result);
        String txHash = result.getValue();

        waitForTransaction(result);

        var jsonResult = backendService.getMetadataService().getJSONMetadataByTxnHash(txHash);
        assertTrue(jsonResult.isSuccessful(), "JSON metadata retrieval should succeed");
        assertFalse(jsonResult.getValue().isEmpty(), "JSON metadata should have entries");

        jsonMeta = findJsonMetadataForLabel(jsonResult.getValue(), "1903");
        assertNotNull(jsonMeta, "JSON metadata for label 1903 should exist");
        System.out.println("[DIAG] JSON metadata for label 1903: " + jsonMeta);

        MetadataMap chainMap = extractMetadataMap(jsonResult.getValue(), "1903");
        restored = converter.fromMetadataMap(chainMap);
    }

    // =========================================================================
    // List of temporal / UUID types — round-trip
    // =========================================================================

    @Test
    void listInstant_roundTrip() {
        assertNotNull(restored.getTimestamps());
        assertEquals(original.getTimestamps().size(), restored.getTimestamps().size());
        for (int i = 0; i < original.getTimestamps().size(); i++) {
            assertEquals(original.getTimestamps().get(i), restored.getTimestamps().get(i));
        }
    }

    @Test
    void listDuration_roundTrip() {
        assertNotNull(restored.getDurations());
        assertEquals(original.getDurations().size(), restored.getDurations().size());
        for (int i = 0; i < original.getDurations().size(); i++) {
            assertEquals(original.getDurations().get(i), restored.getDurations().get(i));
        }
    }

    @Test
    void listLocalDate_roundTrip() {
        assertNotNull(restored.getDates());
        assertEquals(original.getDates().size(), restored.getDates().size());
        for (int i = 0; i < original.getDates().size(); i++) {
            assertEquals(original.getDates().get(i), restored.getDates().get(i));
        }
    }

    @Test
    void listUuid_roundTrip() {
        assertNotNull(restored.getUuids());
        assertEquals(original.getUuids().size(), restored.getUuids().size());
        for (int i = 0; i < original.getUuids().size(); i++) {
            assertEquals(original.getUuids().get(i), restored.getUuids().get(i));
        }
    }

    // =========================================================================
    // Optional temporal / UUID types — round-trip
    // =========================================================================

    @Test
    void optionalInstant_present_roundTrip() {
        assertTrue(restored.getOptInstant().isPresent());
        assertEquals(original.getOptInstant().get(), restored.getOptInstant().get());
    }

    @Test
    void optionalDuration_present_roundTrip() {
        assertTrue(restored.getOptDuration().isPresent());
        assertEquals(original.getOptDuration().get(), restored.getOptDuration().get());
    }

    @Test
    void optionalUuid_present_roundTrip() {
        assertTrue(restored.getOptUuid().isPresent());
        assertEquals(original.getOptUuid().get(), restored.getOptUuid().get());
    }

    @Test
    void optionalInstant_empty_roundTrip() {
        // Empty Optional should round-trip as empty (or null depending on implementation)
        assertTrue(restored.getOptEmpty() == null || restored.getOptEmpty().isEmpty());
    }

    // =========================================================================
    // Map with temporal values — round-trip
    // =========================================================================

    @Test
    void mapInstant_roundTrip() {
        assertNotNull(restored.getInstantMap());
        assertEquals(original.getInstantMap().size(), restored.getInstantMap().size());
        for (Map.Entry<String, Instant> entry : original.getInstantMap().entrySet()) {
            assertEquals(entry.getValue(), restored.getInstantMap().get(entry.getKey()),
                    "Mismatch for key: " + entry.getKey());
        }
    }

    @Test
    void mapDuration_roundTrip() {
        assertNotNull(restored.getDurationMap());
        assertEquals(original.getDurationMap().size(), restored.getDurationMap().size());
        for (Map.Entry<String, Duration> entry : original.getDurationMap().entrySet()) {
            assertEquals(entry.getValue(), restored.getDurationMap().get(entry.getKey()),
                    "Mismatch for key: " + entry.getKey());
        }
    }

    // =========================================================================
    // String-coercible scalar types — round-trip
    // =========================================================================

    @Test
    void uri_roundTrip() {
        assertEquals(original.getEndpoint(), restored.getEndpoint());
    }

    @Test
    void locale_roundTrip() {
        assertEquals(original.getLanguage(), restored.getLanguage());
    }

    @Test
    void currency_roundTrip() {
        assertEquals(original.getCurrency(), restored.getCurrency());
    }

    // =========================================================================
    // Null handling — fields should be absent from map and restored as null
    // =========================================================================

    @Test
    void nullInstant_restoredAsNull() {
        assertNull(restored.getNullInstant());
    }

    @Test
    void nullDuration_restoredAsNull() {
        assertNull(restored.getNullDuration());
    }

    @Test
    void nullUuid_restoredAsNull() {
        assertNull(restored.getNullUuid());
    }

    @Test
    void nullUri_restoredAsNull() {
        assertNull(restored.getNullUri());
    }

    // =========================================================================
    // Raw JSON assertions — List elements
    // =========================================================================

    @Test
    void jsonRaw_listInstant_elementsAreEpochSeconds() {
        JsonNode arr = jsonMeta.get("timestamps");
        assertNotNull(arr);
        assertTrue(arr.isArray());
        assertEquals(3, arr.size());
        assertEquals(1700000000L, arr.get(0).asLong());
        assertEquals(1700001000L, arr.get(1).asLong());
        assertEquals(1700002000L, arr.get(2).asLong());
    }

    @Test
    void jsonRaw_listDuration_elementsAreTotalSeconds() {
        JsonNode arr = jsonMeta.get("durations");
        assertNotNull(arr);
        assertTrue(arr.isArray());
        assertEquals(2, arr.size());
        assertEquals(3600L, arr.get(0).asLong());
        assertEquals(7200L, arr.get(1).asLong());
    }

    @Test
    void jsonRaw_listLocalDate_elementsAreEpochDays() {
        JsonNode arr = jsonMeta.get("dates");
        assertNotNull(arr);
        assertTrue(arr.isArray());
        assertEquals(2, arr.size());
        assertEquals(LocalDate.of(2024, 1, 1).toEpochDay(), arr.get(0).asLong());
        assertEquals(LocalDate.of(2024, 6, 15).toEpochDay(), arr.get(1).asLong());
    }

    @Test
    void jsonRaw_listUuid_elementsAreStrings() {
        JsonNode arr = jsonMeta.get("uuids");
        assertNotNull(arr);
        assertTrue(arr.isArray());
        assertEquals(2, arr.size());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", arr.get(0).asText());
        assertEquals("6ba7b810-9dad-11d1-80b4-00c04fd430c8", arr.get(1).asText());
    }

    // =========================================================================
    // Raw JSON assertions — Optional present values
    // =========================================================================

    @Test
    void jsonRaw_optInstant_isEpochSeconds() {
        assertEquals(1700000000L, jsonMeta.get("opt_instant").asLong());
    }

    @Test
    void jsonRaw_optDuration_isTotalSeconds() {
        assertEquals(5400L, jsonMeta.get("opt_duration").asLong());
    }

    @Test
    void jsonRaw_optUuid_isString() {
        assertEquals("550e8400-e29b-41d4-a716-446655440000", jsonMeta.get("opt_uuid").asText());
    }

    @Test
    void jsonRaw_optEmpty_isAbsent() {
        assertFalse(jsonMeta.has("opt_empty"), "Empty Optional should not appear in JSON");
    }

    // =========================================================================
    // Raw JSON assertions — Map values
    // =========================================================================

    @Test
    void jsonRaw_mapInstant_valuesAreEpochSeconds() {
        JsonNode map = jsonMeta.get("instant_map");
        assertNotNull(map);
        assertEquals(1700000000L, map.get("start").asLong());
        assertEquals(1700100000L, map.get("end").asLong());
    }

    @Test
    void jsonRaw_mapDuration_valuesAreTotalSeconds() {
        JsonNode map = jsonMeta.get("duration_map");
        assertNotNull(map);
        assertEquals(60L, map.get("short").asLong());
        assertEquals(86400L, map.get("long").asLong());
    }

    // =========================================================================
    // Raw JSON assertions — String-coercible types
    // =========================================================================

    @Test
    void jsonRaw_uri_isString() {
        assertEquals("https://example.com/api/v1", jsonMeta.get("endpoint").asText());
    }

    @Test
    void jsonRaw_locale_isLanguageTag() {
        assertEquals("en-US", jsonMeta.get("language").asText());
    }

    @Test
    void jsonRaw_currency_isCurrencyCode() {
        assertEquals("USD", jsonMeta.get("currency").asText());
    }

    // =========================================================================
    // Raw JSON assertions — Null fields absent
    // =========================================================================

    @Test
    void jsonRaw_nullInstant_absentFromMap() {
        assertFalse(jsonMeta.has("null_instant"), "null Instant should not appear in JSON");
    }

    @Test
    void jsonRaw_nullDuration_absentFromMap() {
        assertFalse(jsonMeta.has("null_duration"), "null Duration should not appear in JSON");
    }

    @Test
    void jsonRaw_nullUuid_absentFromMap() {
        assertFalse(jsonMeta.has("null_uuid"), "null UUID should not appear in JSON");
    }

    @Test
    void jsonRaw_nullUri_absentFromMap() {
        assertFalse(jsonMeta.has("null_uri"), "null URI should not appear in JSON");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private MetadataCompositeTypes buildOriginal() {
        MetadataCompositeTypes obj = new MetadataCompositeTypes();
        obj.setTestId("composite-001");

        // Lists
        obj.setTimestamps(List.of(
                Instant.ofEpochSecond(1700000000L),
                Instant.ofEpochSecond(1700001000L),
                Instant.ofEpochSecond(1700002000L)
        ));
        obj.setDurations(List.of(
                Duration.ofHours(1),
                Duration.ofHours(2)
        ));
        obj.setDates(List.of(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 15)
        ));
        obj.setUuids(List.of(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        ));

        // Optionals
        obj.setOptInstant(Optional.of(Instant.ofEpochSecond(1700000000L)));
        obj.setOptDuration(Optional.of(Duration.ofMinutes(90)));
        obj.setOptUuid(Optional.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")));
        obj.setOptEmpty(Optional.empty());

        // Maps
        obj.setInstantMap(Map.of(
                "start", Instant.ofEpochSecond(1700000000L),
                "end", Instant.ofEpochSecond(1700100000L)
        ));
        obj.setDurationMap(Map.of(
                "short", Duration.ofMinutes(1),
                "long", Duration.ofDays(1)
        ));

        // String-coercible scalars
        obj.setEndpoint(URI.create("https://example.com/api/v1"));
        obj.setLanguage(Locale.forLanguageTag("en-US"));
        obj.setCurrency(Currency.getInstance("USD"));

        // Null fields — explicitly null
        obj.setNullInstant(null);
        obj.setNullDuration(null);
        obj.setNullUuid(null);
        obj.setNullUri(null);

        return obj;
    }

    private JsonNode findJsonMetadataForLabel(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return entry.getJsonMetadata();
            }
        }
        return null;
    }

    private MetadataMap extractMetadataMap(List<MetadataJSONContent> entries, String label) {
        for (MetadataJSONContent entry : entries) {
            if (label.equals(entry.getLabel())) {
                return JsonNoSchemaToMetadataConverter.parseObjectNode(
                        (ObjectNode) entry.getJsonMetadata());
            }
        }
        fail("No metadata found for label " + label);
        return null;
    }
}
