package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
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
    @JsonSubTypes.Type(value = PaymentIntent.class, name = "payment"),
    @JsonSubTypes.Type(value = DonationIntent.class, name = "donation"),
    @JsonSubTypes.Type(value = MintingIntent.class, name = "minting"),
    @JsonSubTypes.Type(value = MetadataIntent.class, name = "metadata"),

    // Stake intentions
    @JsonSubTypes.Type(value = StakeRegistrationIntent.class, name = "stake_registration"),
    @JsonSubTypes.Type(value = StakeDeregistrationIntent.class, name = "stake_deregistration"),
    @JsonSubTypes.Type(value = StakeDelegationIntent.class, name = "stake_delegation"),
    @JsonSubTypes.Type(value = StakeWithdrawalIntent.class, name = "stake_withdrawal"),
    @JsonSubTypes.Type(value = PoolRegistrationIntent.class, name = "pool_registration"),
    @JsonSubTypes.Type(value = PoolRegistrationIntent.class, name = "pool_update"),
    @JsonSubTypes.Type(value = PoolRetirementIntent.class, name = "pool_retirement"),

    // Governance intentions
    @JsonSubTypes.Type(value = DRepRegistrationIntent.class, name = "drep_registration"),
    @JsonSubTypes.Type(value = DRepDeregistrationIntent.class, name = "drep_deregistration"),
    @JsonSubTypes.Type(value = DRepUpdateIntent.class, name = "drep_update"),
    @JsonSubTypes.Type(value = GovernanceProposalIntent.class, name = "governance_proposal"),
    @JsonSubTypes.Type(value = VotingIntent.class, name = "voting"),
    @JsonSubTypes.Type(value = VotingDelegationIntent.class, name = "voting_delegation"),

    // ScriptTx-specific intentions
    @JsonSubTypes.Type(value = ScriptCollectFromIntent.class, name = "script_collect_from")
    ,
    @JsonSubTypes.Type(value = ScriptMintingIntent.class, name = "script_minting")
    ,
    @JsonSubTypes.Type(value = ScriptValidatorAttachmentIntent.class, name = "validator")
    ,
    @JsonSubTypes.Type(value = NativeScriptAttachmentIntent.class, name = "native_script")
    ,
    @JsonSubTypes.Type(value = ReferenceInputIntent.class, name = "reference_input")
    ,
    // Base Tx input collection intentions
    @JsonSubTypes.Type(value = CollectFromIntent.class, name = "collect_from")

    // Additional intentions will be added as needed for the simplified approach
})
public interface TxIntent {

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
    default TxIntent resolveVariables(Map<String, Object> variables) {
        // Default: no variable resolution needed
        return this;
    }
}
