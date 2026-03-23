package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Integration test POJO covering composite temporal/UUID/string-coercible types.
 * <p>
 * Exercises codegen paths NOT covered by scalar fields:
 * <ul>
 *   <li>List&lt;Instant&gt;, List&lt;Duration&gt;, List&lt;LocalDate&gt;, List&lt;UUID&gt;</li>
 *   <li>Optional&lt;Instant&gt;, Optional&lt;Duration&gt;, Optional&lt;UUID&gt;</li>
 *   <li>Map&lt;String, Instant&gt;, Map&lt;String, Duration&gt;</li>
 *   <li>URI, Locale, Currency (string-coercible scalars)</li>
 *   <li>Null handling for temporal/UUID fields</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@MetadataType(label = 1903)
public class MetadataCompositeTypes {

    // ── List of temporal / UUID types ────────────────────────────────────

    @MetadataField(key = "timestamps")
    private List<Instant> timestamps;

    @MetadataField(key = "durations")
    private List<Duration> durations;

    @MetadataField(key = "dates")
    private List<LocalDate> dates;

    @MetadataField(key = "uuids")
    private List<UUID> uuids;

    // ── Optional temporal / UUID types ───────────────────────────────────

    @MetadataField(key = "opt_instant")
    private Optional<Instant> optInstant;

    @MetadataField(key = "opt_duration")
    private Optional<Duration> optDuration;

    @MetadataField(key = "opt_uuid")
    private Optional<UUID> optUuid;

    @MetadataField(key = "opt_empty")
    private Optional<Instant> optEmpty;

    // ── Map with temporal values ─────────────────────────────────────────

    @MetadataField(key = "instant_map")
    private Map<String, Instant> instantMap;

    @MetadataField(key = "duration_map")
    private Map<String, Duration> durationMap;

    // ── String-coercible scalar types ────────────────────────────────────

    private URI endpoint;

    private Locale language;

    private Currency currency;

    // ── Null handling ────────────────────────────────────────────────────

    @MetadataField(key = "null_instant")
    private Instant nullInstant;

    @MetadataField(key = "null_duration")
    private Duration nullDuration;

    @MetadataField(key = "null_uuid")
    private UUID nullUuid;

    @MetadataField(key = "null_uri")
    private URI nullUri;

    // ── Identifier ───────────────────────────────────────────────────────

    @MetadataField(key = "test_id", required = true)
    private String testId;
}
