package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MetadataType(label = 1902)
public class StakeDelegationBatch extends NftBaseMetadata {
    @MetadataField(key = "batch_id", required = true)
    private String batchId;

    @MetadataField(key = "stake_addr", required = true)
    private String stakeAddress;

    private List<DelegationInstruction> delegations;

    @MetadataField(key = "reward_history")
    private Map<String, RewardEntry> rewardsByEpoch;

    @MetadataField(key = "total_delegated")
    private BigInteger totalDelegated;

    @MetadataField(key = "pool_preferences")
    private SortedSet<String> poolPreferences;

    @MetadataField(key = "active_pools", enc = MetadataFieldType.STRING_HEX)
    private List<byte[]> activePoolHashes;

    @MetadataField(adapter = EpochAdapter.class)
    private Instant submittedAt;

    @MetadataField(key = "lock_period")
    private Duration lockPeriod;

    @MetadataField(key = "lock_period_str", enc = MetadataFieldType.STRING)
    private Duration lockPeriodAsString;

    // --- Instant native (no adapter) ---
    @MetadataField(key = "created_at")
    private Instant createdAt;

    @MetadataField(key = "created_at_str", enc = MetadataFieldType.STRING)
    private Instant createdAtAsString;

    // --- LocalDate ---
    @MetadataField(key = "effective_date")
    private LocalDate effectiveDate;

    @MetadataField(key = "effective_date_str", enc = MetadataFieldType.STRING)
    private LocalDate effectiveDateAsString;

    // --- LocalDateTime ---
    @MetadataField(key = "scheduled_at")
    private LocalDateTime scheduledAt;

    // --- java.util.Date ---
    @MetadataField(key = "legacy_date")
    private Date legacyDate;

    @MetadataField(key = "legacy_date_str", enc = MetadataFieldType.STRING)
    private Date legacyDateAsString;

    // --- UUID ---
    @MetadataField(key = "correlation_id")
    private UUID correlationId;

    @MetadataField(defaultValue = "PENDING")
    private String status;

    @MetadataIgnore
    private String memo;
}
