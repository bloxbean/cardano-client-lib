package com.bloxbean.cardano.client.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Result of transaction validation against Cardano ledger rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;

    @Builder.Default
    private List<ValidationError> errors = Collections.emptyList();

    /**
     * Create a successful validation result.
     */
    public static ValidationResult success() {
        return ValidationResult.builder()
                .valid(true)
                .errors(Collections.emptyList())
                .build();
    }

    /**
     * Create a failed validation result with the given errors.
     */
    public static ValidationResult failure(List<ValidationError> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errors(errors != null ? errors : Collections.emptyList())
                .build();
    }

    /**
     * Create a failed validation result with a single error.
     */
    public static ValidationResult failure(ValidationError error) {
        return ValidationResult.builder()
                .valid(false)
                .errors(List.of(error))
                .build();
    }
}
