package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.common.ADAConversionUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRetirement;
import com.bloxbean.cardano.client.transaction.spec.cert.StakePoolId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Intention for retiring a stake pool.
 * Captures the pool ID and retirement epoch.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolRetirementIntention implements TxIntention {

    /**
     * Pool ID to retire.
     * Can be Bech32 encoded (pool1...) or hex encoded.
     */
    @JsonProperty("pool_id")
    private String poolId;

    /**
     * Epoch when the pool will be retired.
     * Must be greater than current epoch.
     */
    @JsonProperty("retirement_epoch")
    private int retirementEpoch;

    @Override
    public String getType() {
        return "pool_retirement";
    }


    @Override
    public void validate() {
        TxIntention.super.validate();
        if (poolId == null || poolId.isEmpty()) {
            throw new IllegalStateException("Pool ID is required for pool retirement");
        }
        if (retirementEpoch <= 0) {
            throw new IllegalStateException("Retirement epoch must be positive");
        }
    }

    // Factory methods for clean API

    /**
     * Create a pool retirement intention.
     */
    public static PoolRetirementIntention retire(String poolId, int retirementEpoch) {
        return PoolRetirementIntention.builder()
            .poolId(poolId)
            .retirementEpoch(retirementEpoch)
            .build();
    }

    // Convenience methods

    /**
     * Set retirement epoch.
     */
    public PoolRetirementIntention atEpoch(int epoch) {
        this.retirementEpoch = epoch;
        return this;
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // Add a dummy output (1 ADA) to fromAddress to trigger input selection
        final String from = ic.getFromAddress();
        if (from == null || from.isBlank()) {
            throw new TxBuildException("From address is required for pool retirement");
        }

        // Use helper to create smart dummy output that merges with existing outputs
        return DepositHelper.createDummyOutputBuilder(from, ADAConversionUtil.adaToLovelace(1));
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for pool retirement");
            }
            String resolvedPoolId = ic.resolveVariable(poolId);
            if (resolvedPoolId == null || resolvedPoolId.isBlank()) {
                throw new TxBuildException("Pool ID is required for pool retirement");
            }
            if (retirementEpoch <= 0) {
                throw new TxBuildException("Retirement epoch must be positive");
            }
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            String resolvedPoolId = ic.resolveVariable(poolId);
            StakePoolId stakePoolId = resolvedPoolId.startsWith("pool")
                    ? StakePoolId.fromBech32PoolId(resolvedPoolId)
                    : StakePoolId.fromHexPoolId(resolvedPoolId);

            if (txn.getBody().getCerts() == null) {
                txn.getBody().setCerts(new ArrayList<Certificate>());
            }
            txn.getBody().getCerts().add(new PoolRetirement(stakePoolId.getPoolKeyHash(), retirementEpoch));
        };
    }
}
