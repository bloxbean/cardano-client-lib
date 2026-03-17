package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Integration test POJO covering temporal type support.
 * The annotation processor generates {@code SampleTemporalTypesMetadataConverter} from this class.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>{@code Instant}       DEFAULT → BigInteger (epoch seconds)</li>
 *   <li>{@code Instant}       STRING  → ISO-8601 text</li>
 *   <li>{@code LocalDate}     DEFAULT → BigInteger (epoch days)</li>
 *   <li>{@code LocalDate}     STRING  → ISO-8601 date text</li>
 *   <li>{@code LocalDateTime} DEFAULT → ISO-8601 text</li>
 *   <li>{@code Date}          DEFAULT → BigInteger (epoch millis)</li>
 *   <li>{@code Date}          STRING  → ISO-8601 text via toInstant()</li>
 *   <li>{@code Duration}     DEFAULT → BigInteger (total seconds)</li>
 *   <li>{@code Duration}     STRING  → ISO-8601 duration text</li>
 * </ul>
 */
@MetadataType
public class SampleTemporalTypes {

    // --- Instant ---
    private Instant createdAt;

    @MetadataField(key = "createdAtStr", enc = MetadataFieldType.STRING)
    private Instant createdAtAsString;

    // --- LocalDate ---
    private LocalDate birthDate;

    @MetadataField(key = "birthDateStr", enc = MetadataFieldType.STRING)
    private LocalDate birthDateAsString;

    // --- LocalDateTime (DEFAULT is ISO-8601 text; STRING routes through same path) ---
    private LocalDateTime scheduledAt;

    // --- Date ---
    private Date legacyDate;

    @MetadataField(key = "legacyDateStr", enc = MetadataFieldType.STRING)
    private Date legacyDateAsString;

    // --- Duration ---
    private Duration ttl;

    @MetadataField(key = "ttlStr", enc = MetadataFieldType.STRING)
    private Duration ttlAsString;

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCreatedAtAsString() { return createdAtAsString; }
    public void setCreatedAtAsString(Instant createdAtAsString) { this.createdAtAsString = createdAtAsString; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public LocalDate getBirthDateAsString() { return birthDateAsString; }
    public void setBirthDateAsString(LocalDate birthDateAsString) { this.birthDateAsString = birthDateAsString; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public Date getLegacyDate() { return legacyDate; }
    public void setLegacyDate(Date legacyDate) { this.legacyDate = legacyDate; }

    public Date getLegacyDateAsString() { return legacyDateAsString; }
    public void setLegacyDateAsString(Date legacyDateAsString) { this.legacyDateAsString = legacyDateAsString; }

    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }

    public Duration getTtlAsString() { return ttlAsString; }
    public void setTtlAsString(Duration ttlAsString) { this.ttlAsString = ttlAsString; }
}
