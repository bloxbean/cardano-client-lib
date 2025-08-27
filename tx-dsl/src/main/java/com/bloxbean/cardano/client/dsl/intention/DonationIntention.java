package com.bloxbean.cardano.client.dsl.intention;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.Map;

/**
 * Intention for donating to treasury operations.
 * Represents a donation from the current treasury value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DonationIntention implements TxIntention {
    
    @JsonProperty("current_treasury_value")
    private BigInteger currentTreasuryValue;
    
    @JsonProperty("donation_amount")
    private BigInteger donationAmount;
    
    @Override
    public String getType() {
        return "donation";
    }
    
    @Override
    public void apply(Tx tx, Map<String, Object> variables) {
        // Resolve values from variables if they're variables
        BigInteger resolvedCurrentValue = resolveAmount(currentTreasuryValue, variables);
        BigInteger resolvedDonationAmount = resolveAmount(donationAmount, variables);
        
        // Call tx method directly
        tx.donateToTreasury(resolvedCurrentValue, resolvedDonationAmount);
    }
    
    /**
     * Resolve BigInteger values that might be stored as variables.
     * For now, we just return the value as-is, but this could be extended
     * to support variable resolution for BigInteger values.
     */
    private BigInteger resolveAmount(BigInteger amount, Map<String, Object> context) {
        // Future enhancement: support variable resolution for amounts
        // e.g., if amount is null, try to resolve from context
        return amount;
    }
}