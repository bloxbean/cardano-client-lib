package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;

/**
 * Intention for registering or updating a stake pool.
 * Captures the pool registration certificate and whether it's an update.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoolRegistrationIntention implements TxIntention {

    /**
     * Pool registration certificate (runtime object).
     */
    @JsonIgnore
    private PoolRegistration poolRegistration;

    /**
     * Whether this is a pool update (true) or new registration (false).
     */
    @JsonProperty("is_update")
    @Builder.Default
    private boolean isUpdate = false;

    // Serialization fields - TODO: implement proper PoolRegistration serialization

    /**
     * Pool registration data for serialization (placeholder).
     * TODO: Implement proper PoolRegistration to/from JSON conversion.
     */
    @JsonProperty("pool_registration_data")
    private Object poolRegistrationData;

    @Override
    public String getType() {
        return isUpdate ? "pool_update" : "pool_registration";
    }


    @Override
    public void validate() {
        TxIntention.super.validate();
        if (poolRegistration == null && poolRegistrationData == null) {
            throw new IllegalStateException("Pool registration certificate is required");
        }
    }

    // Factory methods for clean API

    /**
     * Create a pool registration intention.
     */
    public static PoolRegistrationIntention register(PoolRegistration poolRegistration) {
        return PoolRegistrationIntention.builder()
            .poolRegistration(poolRegistration)
            .isUpdate(false)
            .build();
    }

    /**
     * Create a pool update intention.
     */
    public static PoolRegistrationIntention update(PoolRegistration poolRegistration) {
        return PoolRegistrationIntention.builder()
            .poolRegistration(poolRegistration)
            .isUpdate(true)
            .build();
    }

    // Convenience methods

    /**
     * Set as pool update instead of registration.
     */
    public PoolRegistrationIntention asUpdate() {
        this.isUpdate = true;
        return this;
    }

    /**
     * Check if this is a pool registration.
     */
    @JsonIgnore
    public boolean isRegistration() {
        return !isUpdate;
    }

    /**
     * Check if this intention has runtime objects available.
     */
    @JsonIgnore
    public boolean hasRuntimeObjects() {
        return poolRegistration != null;
    }

    // ===== Self-processing methods =====

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // For new registrations, add dummy output equal to poolDeposit to trigger input selection
        if (!isUpdate) {
            final String from = ic.getFromAddress();
            if (from == null || from.isBlank()) {
                throw new TxBuildException("From address is required for pool registration");
            }

            return (ctx, outputs) -> {
                String poolDepositStr = ctx.getProtocolParams().getPoolDeposit();
                if (poolDepositStr == null || poolDepositStr.isEmpty())
                    throw new TxBuildException("Protocol parameter poolDeposit not available");
                BigInteger poolDeposit = new BigInteger(poolDepositStr);
                outputs.add(new TransactionOutput(from, Value.builder().coin(poolDeposit).build()));
            };
        }
        // For updates, no outputs needed
        return null;
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> {
            if (ic.getFromAddress() == null || ic.getFromAddress().isBlank()) {
                throw new TxBuildException("From address is required for pool operations");
            }
            if (poolRegistration == null && poolRegistrationData == null) {
                throw new TxBuildException("Pool registration certificate is required");
            }
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            if (poolRegistration == null) {
                throw new TxBuildException("Runtime PoolRegistration is required for apply()");
            }

            // Add certificate
            if (txn.getBody().getCerts() == null) {
                txn.getBody().setCerts(new ArrayList<Certificate>());
            }
            txn.getBody().getCerts().add(poolRegistration);

            if (!isUpdate) {
                // Deduct pool deposit from fromAddress output
                String from = ic.getFromAddress();
                BigInteger poolDeposit = new BigInteger(ctx.getProtocolParams().getPoolDeposit());
                txn.getBody().getOutputs().stream()
                        .filter(to -> to.getAddress().equals(from)
                                && to.getValue() != null && to.getValue().getCoin() != null
                                && to.getValue().getCoin().compareTo(poolDeposit) >= 0)
                        .sorted((o1, o2) -> o2.getValue().getCoin().compareTo(o1.getValue().getCoin()))
                        .findFirst()
                        .ifPresentOrElse(to -> {
                            to.getValue().setCoin(to.getValue().getCoin().subtract(poolDeposit));
                            var ma = to.getValue().getMultiAssets();
                            if (to.getValue().getCoin().equals(BigInteger.ZERO) && (ma == null || ma.isEmpty())) {
                                txn.getBody().getOutputs().remove(to);
                            }
                        }, () -> {
                            throw new TxBuildException("Output for from address not found to remove pool deposit amount: " + from);
                        });
            }
        };
    }
}
