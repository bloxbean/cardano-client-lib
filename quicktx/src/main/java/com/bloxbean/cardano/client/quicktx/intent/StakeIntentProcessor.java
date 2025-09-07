package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.Tx;

import java.util.Map;

/**
 * Specialized processor for stake-related intentions.
 * Handles stake registration, delegation, withdrawal, and pool operations.
 *
 * This processor is part of the domain-specific processor architecture
 * to avoid monolithic IntentProcessor complexity.
 */
public class StakeIntentProcessor {

    /**
     * Process a stake-related intention on the given transaction with variable resolution.
     *
     * @param intention the stake intention to process
     * @param tx the transaction to apply the intention to
     * @param variables the variables map for resolving variable references (may be null)
     * @throws IllegalArgumentException if the intention cannot be processed
     * @throws UnsupportedOperationException if the intention is not supported on the tx type
     */
    public void process(TxIntention intention, AbstractTx<?> tx, Map<String, Object> variables) {
        if (!(tx instanceof Tx)) {
            throw new UnsupportedOperationException("Stake operations are only supported on Tx instances");
        }

        switch (intention.getType()) {
            case "stake_registration":
                processStakeRegistration((StakeRegistrationIntention) intention, (Tx) tx, variables);
                break;
            case "stake_deregistration":
                processStakeDeregistration((StakeDeregistrationIntention) intention, (Tx) tx, variables);
                break;
            case "stake_delegation":
                processStakeDelegation((StakeDelegationIntention) intention, (Tx) tx, variables);
                break;
            case "stake_withdrawal":
                processStakeWithdrawal((StakeWithdrawalIntention) intention, (Tx) tx, variables);
                break;
            case "pool_registration":
                processPoolRegistration((PoolRegistrationIntention) intention, (Tx) tx, variables);
                break;
            case "pool_update":
                processPoolUpdate((PoolRegistrationIntention) intention, (Tx) tx, variables);
                break;
            case "pool_retirement":
                processPoolRetirement((PoolRetirementIntention) intention, (Tx) tx, variables);
                break;
            default:
                throw new IllegalArgumentException("Unknown stake intention type: " + intention.getType());
        }
    }

    /**
     * Process stake registration intention with variable resolution.
     */
    private void processStakeRegistration(StakeRegistrationIntention intention, Tx tx, Map<String, Object> variables) {
        String resolvedStakeAddress = resolveVariable(intention.getStakeAddress(), variables);
        tx.registerStakeAddress(resolvedStakeAddress);
    }

    /**
     * Process stake deregistration intention with variable resolution.
     */
    private void processStakeDeregistration(StakeDeregistrationIntention intention, Tx tx, Map<String, Object> variables) {
        String resolvedStakeAddress = resolveVariable(intention.getStakeAddress(), variables);
        String resolvedRefundAddress = resolveVariable(intention.getRefundAddress(), variables);

        if (resolvedRefundAddress != null) {
            tx.deregisterStakeAddress(resolvedStakeAddress, resolvedRefundAddress);
        } else {
            tx.deregisterStakeAddress(resolvedStakeAddress);
        }
    }

    /**
     * Process stake delegation intention with variable resolution.
     */
    private void processStakeDelegation(StakeDelegationIntention intention, Tx tx, Map<String, Object> variables) {
        String resolvedStakeAddress = resolveVariable(intention.getStakeAddress(), variables);
        String resolvedPoolId = resolveVariable(intention.getPoolId(), variables);
        tx.delegateTo(resolvedStakeAddress, resolvedPoolId);
    }

    /**
     * Process stake withdrawal intention with variable resolution.
     */
    private void processStakeWithdrawal(StakeWithdrawalIntention intention, Tx tx, Map<String, Object> variables) {
        String resolvedRewardAddress = resolveVariable(intention.getRewardAddress(), variables);
        String resolvedReceiver = resolveVariable(intention.getReceiver(), variables);

        if (resolvedReceiver != null) {
            tx.withdraw(resolvedRewardAddress, intention.getAmount(), resolvedReceiver);
        } else {
            tx.withdraw(resolvedRewardAddress, intention.getAmount());
        }
    }

    /**
     * Process pool registration intention with variable resolution.
     */
    private void processPoolRegistration(PoolRegistrationIntention intention, Tx tx, Map<String, Object> variables) {
        if (intention.getPoolRegistration() == null) {
            throw new IllegalArgumentException("Pool registration certificate is required");
        }
        // Note: PoolRegistration is a complex object that doesn't support simple variable resolution
        // Variable resolution for pool registration data would need custom logic if needed
        tx.registerPool(intention.getPoolRegistration());
    }

    /**
     * Process pool update intention with variable resolution.
     */
    private void processPoolUpdate(PoolRegistrationIntention intention, Tx tx, Map<String, Object> variables) {
        if (intention.getPoolRegistration() == null) {
            throw new IllegalArgumentException("Pool registration certificate is required for update");
        }
        // Note: PoolRegistration is a complex object that doesn't support simple variable resolution
        // Variable resolution for pool registration data would need custom logic if needed
        tx.updatePool(intention.getPoolRegistration());
    }

    /**
     * Process pool retirement intention with variable resolution.
     */
    private void processPoolRetirement(PoolRetirementIntention intention, Tx tx, Map<String, Object> variables) {
        String resolvedPoolId = resolveVariable(intention.getPoolId(), variables);
        tx.retirePool(resolvedPoolId, intention.getRetirementEpoch());
    }

    /**
     * Check if this processor can handle the given intention type.
     */
    public boolean canProcess(String intentionType) {
        switch (intentionType) {
            case "stake_registration":
            case "stake_deregistration":
            case "stake_delegation":
            case "stake_withdrawal":
            case "pool_registration":
            case "pool_update":
            case "pool_retirement":
                return true;
            default:
                return false;
        }
    }

    /**
     * Helper method to resolve a variable value.
     * If the value starts with "${" and ends with "}", it's treated as a variable reference.
     *
     * @param value the value that may contain a variable reference
     * @param variables the variables map for resolution
     * @return the resolved value
     */
    private String resolveVariable(String value, Map<String, Object> variables) {
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
