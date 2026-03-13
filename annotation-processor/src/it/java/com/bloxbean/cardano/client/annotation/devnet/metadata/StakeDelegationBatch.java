package com.bloxbean.cardano.client.annotation.devnet.metadata;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

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

    @MetadataField(defaultValue = "PENDING")
    private String status;

    @MetadataIgnore
    private String memo;
}
