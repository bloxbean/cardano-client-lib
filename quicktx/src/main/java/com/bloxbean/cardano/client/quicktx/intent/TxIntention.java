package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;


/**
 * Base interface for capturing transaction intentions in the Plan + Replayer mechanism.
 * Each intention represents a method call that needs to be applied to build a transaction.
 *
 * Intentions are recorded when building a transaction and can be replayed later to
 * reconstruct an equivalent transaction.
 *
 * Works with both Tx and ScriptTx since both extend AbstractTx.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes({
    // Core payment intentions - simplified approach
    @JsonSubTypes.Type(value = PaymentIntention.class, name = "payment"),
    @JsonSubTypes.Type(value = DonationIntention.class, name = "donation"),
    @JsonSubTypes.Type(value = MintingIntention.class, name = "minting"),
    @JsonSubTypes.Type(value = MetadataIntention.class, name = "metadata"),

    // Stake intentions
    @JsonSubTypes.Type(value = StakeRegistrationIntention.class, name = "stake_registration"),
    @JsonSubTypes.Type(value = StakeDeregistrationIntention.class, name = "stake_deregistration"),
    @JsonSubTypes.Type(value = StakeDelegationIntention.class, name = "stake_delegation"),
    @JsonSubTypes.Type(value = StakeWithdrawalIntention.class, name = "stake_withdrawal"),
    @JsonSubTypes.Type(value = PoolRegistrationIntention.class, name = "pool_registration"),
    @JsonSubTypes.Type(value = PoolRegistrationIntention.class, name = "pool_update"),
    @JsonSubTypes.Type(value = PoolRetirementIntention.class, name = "pool_retirement"),

    // Governance intentions
    @JsonSubTypes.Type(value = DRepRegistrationIntention.class, name = "drep_registration"),
    @JsonSubTypes.Type(value = DRepDeregistrationIntention.class, name = "drep_deregistration"),
    @JsonSubTypes.Type(value = DRepUpdateIntention.class, name = "drep_update"),
    @JsonSubTypes.Type(value = GovernanceProposalIntention.class, name = "governance_proposal"),
    @JsonSubTypes.Type(value = VotingIntention.class, name = "voting"),
    @JsonSubTypes.Type(value = VotingDelegationIntention.class, name = "voting_delegation"),

    // ScriptTx-specific intentions
    @JsonSubTypes.Type(value = ScriptCollectFromIntention.class, name = "script_collect_from")
    ,
    @JsonSubTypes.Type(value = ScriptMintingIntention.class, name = "script_minting")
    ,
    @JsonSubTypes.Type(value = ScriptValidatorAttachmentIntention.class, name = "validator")
    ,
    @JsonSubTypes.Type(value = ReferenceInputIntention.class, name = "reference_input")
    ,
    // Base Tx input collection intentions
    @JsonSubTypes.Type(value = CollectFromIntention.class, name = "collect_from")

    // Additional intentions will be added as needed for the simplified approach
})
public interface TxIntention {

    /**
     * Get the type of this intention for serialization.
     * This must match the name in @JsonSubTypes.Type annotation.
     *
     * @return the intention type identifier
     */
    String getType();


    /**
     * Validate that this intention has all required fields.
     * This is called before serialization and after deserialization.
     *
     * @throws IllegalStateException if the intention is invalid
     */
    default void validate() {
        if (getType() == null || getType().isEmpty()) {
            throw new IllegalStateException("Intention type is required");
        }
    }

    /**
     * Self-processing method for output building phase (Phase 1).
     * This phase creates transaction outputs using TxOutputBuilder.
     *
     * Intentions that create outputs (like PaymentIntention, parts of MintingIntention)
     * should return a TxOutputBuilder. Intentions that only transform the transaction
     * (like DonationIntention, StakeRegistrationIntention) should return null.
     *
     * Default implementation returns null (no outputs created).
     * Output-creating intentions should override this method.
     *
     * @param context the execution context with variables and configuration
     * @return TxOutputBuilder for creating outputs, or null if no outputs
     */
    default TxOutputBuilder outputBuilder(IntentContext context) {
        // Default: no outputs created
        return null;
    }

    default LazyUtxoStrategy utxoStrategy() {
        // Default: no inputs created
        return null;
    }

    /**
     * Self-processing method for pre-processing phase.
     * This phase handles validation, variable resolution, and preparation.
     * Returns a TxBuilder function that performs pre-processing operations.
     *
     * Default implementation returns a no-op TxBuilder.
     * Intentions should override this for validation and preparation logic.
     *
     * @param context the execution context with variables and configuration
     * @return TxBuilder function for pre-processing (typically no-op)
     */
    default TxBuilder preApply(IntentContext context) {
        // Default: no-op TxBuilder for pre-processing
        return (ctx, txn) -> {
            // Perform basic validation by default
            validate();
        };
    }

    /**
     * Self-processing method for main execution phase.
     * This phase handles the actual transaction building operations.
     * Returns a TxBuilder function that applies the intention to the transaction.
     *
     * Default implementation throws UnsupportedOperationException.
     * Concrete intention classes should override this method.
     *
     * @param context the execution context with variables and configuration
     * @return TxBuilder function that applies this intention to a transaction
     */
    default TxBuilder apply(IntentContext context) {
        throw new UnsupportedOperationException(
            "apply() method not implemented for intention type: " + getType() +
            ". This intention class needs to be updated for the self-processing architecture."
        );
    }

    /**
     * Resolve variables in this intention and return a new intention with resolved values.
     * This method is called during YAML deserialization to perform early variable resolution.
     *
     * Default implementation returns the same intention (no resolution).
     * Intentions with variable fields should override this method.
     *
     * @param variables the variables map for resolution
     * @return a new intention instance with variables resolved
     */
    default TxIntention resolveVariables(Map<String, Object> variables) {
        // Default: no variable resolution needed
        return this;
    }

    /**
     * Helper method to resolve a variable value.
     * If the value starts with "${" and ends with "}", it's treated as a variable reference.
     *
     * @param value the value that may contain a variable reference
     * @param variables the variables map for resolution
     * @return the resolved value
     * @deprecated Use IntentContext.resolveVariable() instead
     */
    @Deprecated
    default String resolveVariable(String value, Map<String, Object> variables) {
        if (value == null) {
            return null;
        }

        if (value.startsWith("${") && value.endsWith("}") && variables != null) {
            String varName = value.substring(2, value.length() - 1);
            Object varValue = variables.get(varName);
            return varValue != null ? varValue.toString() : value;
        }

        return value;
    }
}
