package com.bloxbean.cardano.client.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error from ledger rule validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    /**
     * The validation phase where the error occurred.
     */
    public enum Phase {
        PHASE_1,  // Structural validation (fees, UTxO balance, validity intervals, etc.)
        PHASE_2   // Script execution validation (Plutus script failures)
    }

    private String rule;
    private String message;
    private Phase phase;
}
