package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.TxOutputBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
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
public class DonationIntent implements TxIntent {

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

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedCurrentTreasuryValue = VariableResolver.resolve(currentTreasuryValue, variables);
        String resolvedDonationAmount = VariableResolver.resolve(donationAmount, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedCurrentTreasuryValue, currentTreasuryValue) ||
            !java.util.Objects.equals(resolvedDonationAmount, donationAmount)) {
            return this.toBuilder()
                .currentTreasuryValue(resolvedCurrentTreasuryValue)
                .donationAmount(resolvedDonationAmount)
                .build();
        }

        return this;
    }

    // Factory methods

    /**
     * Create a donation intention with BigInteger values.
     */
    public static DonationIntent of(BigInteger currentTreasuryValue, BigInteger donationAmount) {
        return DonationIntent.builder()
            .currentTreasuryValue(currentTreasuryValue.toString())
            .donationAmount(donationAmount.toString())
            .build();
    }

    // Convenience methods


    // Self-processing methods for functional TxBuilder architecture

    @Override
    public TxOutputBuilder outputBuilder(IntentContext context) {
        // Phase 1: Create dummy output with donation amount to trigger input selection
        return (ctx, outputs) -> {
            // Donation amount already resolved during YAML parsing
            if (donationAmount == null || donationAmount.trim().isEmpty()) {
                throw new TxBuildException("Donation amount is required for output builder");
            }

            BigInteger donationValue = new BigInteger(donationAmount);
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
            // Pre-processing: validate (values already resolved during YAML parsing)

            // Validate values
            if (currentTreasuryValue == null || currentTreasuryValue.trim().isEmpty()) {
                throw new TxBuildException("Current treasury value is required");
            }

            if (donationAmount == null || donationAmount.trim().isEmpty()) {
                throw new TxBuildException("Donation amount is required after variable resolution");
            }

            // Validate values are valid BigInteger
            try {
                new BigInteger(currentTreasuryValue);
            } catch (NumberFormatException e) {
                throw new TxBuildException("Invalid current treasury value: " + currentTreasuryValue);
            }

            try {
                BigInteger donation = new BigInteger(donationAmount);
                if (donation.compareTo(BigInteger.ZERO) <= 0) {
                    throw new TxBuildException("Donation amount must be positive: " + donation);
                }
            } catch (NumberFormatException e) {
                throw new TxBuildException("Invalid donation amount: " + donationAmount);
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
                // Convert to BigInteger and set both values in transaction body (values already resolved during YAML parsing)
                BigInteger currentTreasuryValueBigInt = new BigInteger(currentTreasuryValue);
                BigInteger donationValue = new BigInteger(donationAmount);

                txn.getBody().setCurrentTreasuryValue(currentTreasuryValueBigInt);
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
