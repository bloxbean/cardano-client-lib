package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * Intention for treasury donation operations.
 * Maps to AbstractTx.donateToTreasury(currentTreasuryValue, donationAmount)
 *
 * Note: In the actual implementation, donation is often stored as an attribute
 * rather than applied directly, but for consistency with the intention pattern,
 * we represent it as an intention here.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DonationIntention implements TxIntention {

    /**
     * Dummy address used for treasury donation output during input selection phase.
     * This output gets removed in the apply phase after setting the actual donation.
     */
    private static final String DUMMY_TREASURY_ADDRESS = "_TREASURY_DONATION_";

    /**
     * Current treasury value in lovelace.
     * Stored as string for precision in JSON/YAML.
     */
    @JsonProperty("current_treasury_value")
    private String currentTreasuryValue;

    /**
     * Amount to donate to treasury in lovelace.
     * Stored as string for precision in JSON/YAML.
     */
    @JsonProperty("donation_amount")
    private String donationAmount;

    @Override
    public String getType() {
        return "donation";
    }


    @Override
    public void validate() {
        if (currentTreasuryValue == null || currentTreasuryValue.isEmpty()) {
            throw new IllegalStateException("Current treasury value is required for donation");
        }
        if (donationAmount == null || donationAmount.isEmpty()) {
            throw new IllegalStateException("Donation amount is required");
        }

        // Validate that values are valid numbers
        try {
            new BigInteger(currentTreasuryValue);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid current treasury value: " + currentTreasuryValue);
        }

        try {
            BigInteger donation = new BigInteger(donationAmount);
            if (donation.compareTo(BigInteger.ZERO) <= 0) {
                throw new IllegalStateException("Donation amount must be positive");
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid donation amount: " + donationAmount);
        }
    }

    // Factory methods

    /**
     * Create a donation intention with BigInteger values.
     */
    public static DonationIntention of(BigInteger currentTreasuryValue, BigInteger donationAmount) {
        return DonationIntention.builder()
            .currentTreasuryValue(currentTreasuryValue.toString())
            .donationAmount(donationAmount.toString())
            .build();
    }

    // Convenience methods

    /**
     * Get current treasury value as BigInteger.
     */
    public BigInteger getCurrentTreasuryValueAsBigInt() {
        return currentTreasuryValue != null ? new BigInteger(currentTreasuryValue) : null;
    }

    /**
     * Get donation amount as BigInteger.
     */
    public BigInteger getDonationAmountAsBigInt() {
        return donationAmount != null ? new BigInteger(donationAmount) : null;
    }

    // Self-processing methods for functional TxBuilder architecture

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        // Phase 1: Create dummy output with donation amount to trigger input selection
        return (ctx, outputs) -> {
            String resolvedDonationAmount = context.resolveVariable(donationAmount);
            if (resolvedDonationAmount == null || resolvedDonationAmount.trim().isEmpty()) {
                throw new TxBuildException("Donation amount is required for output builder");
            }

            BigInteger donationValue = new BigInteger(resolvedDonationAmount);
            TransactionOutput dummyOutput = new TransactionOutput(
                DUMMY_TREASURY_ADDRESS, 
                Value.builder().coin(donationValue).build()
            );
            outputs.add(dummyOutput);
        };
    }

    @Override
    public TxBuilder preApply(IntentContext context) {
        return (ctx, txn) -> {
            // Pre-processing: resolve variables and validate
            String resolvedCurrentTreasuryValue = context.resolveVariable(currentTreasuryValue);
            String resolvedDonationAmount = context.resolveVariable(donationAmount);

            // Validate resolved values
            if (resolvedCurrentTreasuryValue == null || resolvedCurrentTreasuryValue.trim().isEmpty()) {
                throw new TxBuildException("Current treasury value is required after variable resolution");
            }

            if (resolvedDonationAmount == null || resolvedDonationAmount.trim().isEmpty()) {
                throw new TxBuildException("Donation amount is required after variable resolution");
            }

            // Validate values are valid BigInteger
            try {
                new BigInteger(resolvedCurrentTreasuryValue);
            } catch (NumberFormatException e) {
                throw new TxBuildException("Invalid current treasury value after resolution: " + resolvedCurrentTreasuryValue);
            }

            try {
                BigInteger donation = new BigInteger(resolvedDonationAmount);
                if (donation.compareTo(BigInteger.ZERO) <= 0) {
                    throw new TxBuildException("Donation amount must be positive: " + donation);
                }
            } catch (NumberFormatException e) {
                throw new TxBuildException("Invalid donation amount after resolution: " + resolvedDonationAmount);
            }

            // Check if donation is already set (can't donate multiple times)
            if (txn.getBody().getDonation() != null) {
                throw new TxBuildException("Can't donate to treasury multiple times in a single transaction");
            }

            // Perform standard validation
            validate();
        };
    }

    @Override
    public TxBuilder apply(IntentContext context) {
        return (ctx, txn) -> {
            try {
                // Resolve variables
                String resolvedCurrentTreasuryValue = context.resolveVariable(currentTreasuryValue);
                String resolvedDonationAmount = context.resolveVariable(donationAmount);

                // Convert to BigInteger and set both values in transaction body
                BigInteger currentTreasuryValue = new BigInteger(resolvedCurrentTreasuryValue);
                BigInteger donationValue = new BigInteger(resolvedDonationAmount);
                
                txn.getBody().setCurrentTreasuryValue(currentTreasuryValue);
                txn.getBody().setDonation(donationValue);

                // Remove the dummy treasury output that was created during input selection
                txn.getBody().getOutputs().removeIf(output -> 
                    DUMMY_TREASURY_ADDRESS.equals(output.getAddress()));

            } catch (Exception e) {
                throw new TxBuildException("Failed to apply DonationIntention: " + e.getMessage(), e);
            }
        };
    }
}
