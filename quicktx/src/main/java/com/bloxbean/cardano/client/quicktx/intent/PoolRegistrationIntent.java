package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
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
public class PoolRegistrationIntent implements TxIntent {

    /**
     * Pool registration certificate.
     */
    @JsonProperty("pool_registration")
    private PoolRegistration poolRegistration;

    /**
     * Whether this is a pool update (true) or new registration (false).
     */
    @JsonProperty("is_update")
    @Builder.Default
    private boolean isUpdate = false;

    @Override
    public String getType() {
        return isUpdate ? "pool_update" : "pool_registration";
    }

    @Override
    public void validate() {
        TxIntent.super.validate();
        if (poolRegistration == null) {
            throw new IllegalStateException("Pool registration certificate is required");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        // No string fields to resolve in pool registration
        return this;
    }

    /**
     * Infer isUpdate flag from type field during deserialization.
     * Called automatically by Jackson after setting the type.
     */
    @JsonProperty("type")
    private void setTypeFromJson(String type) {
        if ("pool_update".equals(type)) {
            this.isUpdate = true;
        } else if ("pool_registration".equals(type)) {
            this.isUpdate = false;
        }
    }

    /**
     * Create a pool registration intention.
     */
    public static PoolRegistrationIntent register(PoolRegistration poolRegistration) {
        return PoolRegistrationIntent.builder()
            .poolRegistration(poolRegistration)
            .isUpdate(false)
            .build();
    }

    /**
     * Create a pool update intention.
     */
    public static PoolRegistrationIntent update(PoolRegistration poolRegistration) {
        return PoolRegistrationIntent.builder()
            .poolRegistration(poolRegistration)
            .isUpdate(true)
            .build();
    }

    @Override
    public TxOutputBuilder outputBuilder(IntentContext ic) {
        // For new registrations, add dummy output equal to poolDeposit to trigger input selection
        if (!isUpdate) {
            final String from = ic.getFromAddress();
            if (from == null || from.isBlank()) {
                throw new TxBuildException("From address is required for pool registration");
            }

            // Use the deposit helper to create the output builder
            return DepositHelper.createDepositOutputBuilder(from,
                DepositHelper.DepositType.POOL_REGISTRATION);
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
                // Use the deposit helper to deduct the pool deposit
                BigInteger poolDeposit = DepositHelper.getDepositAmount(
                    ctx.getProtocolParams(), DepositHelper.DepositType.POOL_REGISTRATION);
                DepositHelper.deductDepositFromOutputs(txn, ic.getFromAddress(), poolDeposit);
            }
        };
    }
}
