package com.bloxbean.cardano.client.quicktx;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Execution context for intention processing in the self-processing architecture.
 *
 * This context provides the necessary runtime information for intentions to process
 * themselves functionally, including variable resolution and default addresses.
 *
 * IntentContext is different from QuickTxBuilder.TxContext:
 * - IntentContext: Runtime execution data for intention processing
 * - TxContext: Transaction building configuration (fee payer, signers, etc.)
 */
@Data
@Builder
public class IntentContext {

    /**
     * Variables map for resolving ${variable} references in intentions.
     * Used for YAML parameterization and dynamic value resolution.
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * Default from address for intentions that need a source address.
     * Populated from AbstractTx.from() or PlanAttributes.
     */
    private String fromAddress;

    /**
     * Default change address for transaction building.
     * Populated from AbstractTx.withChangeAddress() or PlanAttributes.
     */
    private String changeAddress;

    /**
     * Track which variables have been resolved for debugging and validation.
     */
    @Builder.Default
    private Set<String> resolvedVariables = new HashSet<>();

    /**
     * Resolve a variable reference in the format ${variable}.
     * If the value is not a variable reference, returns the value unchanged.
     *
     * @param value the value that may contain a variable reference
     * @return the resolved value or the original value if not a variable reference
     */
    public String resolveVariable(String value) {
        if (value == null) {
            return null;
        }

        // Check if value is a variable reference: ${variable}
        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            Object varValue = variables.get(varName);

            if (varValue != null) {
                resolvedVariables.add(varName); // Track usage for debugging
                return varValue.toString();
            } else {
                // Variable not found - could throw exception or return placeholder
                // For now, return the original reference for debugging
                return value;
            }
        }

        return value;
    }

    /**
     * Add a variable to the context.
     *
     * @param name the variable name
     * @param value the variable value
     */
    public void addVariable(@NonNull String name, Object value) {
        variables.put(name, value);
    }

    /**
     * Check if a variable exists in the context.
     *
     * @param name the variable name
     * @return true if the variable exists
     */
    public boolean hasVariable(String name) {
        return variables.containsKey(name);
    }

    /**
     * Get the set of variables that have been resolved during processing.
     * Useful for debugging and validation.
     *
     * @return set of resolved variable names
     */
    public Set<String> getResolvedVariables() {
        return new HashSet<>(resolvedVariables);
    }

    /**
     * Factory method to create empty IntentContext.
     *
     * @return new empty IntentContext
     */
    public static IntentContext empty() {
        return IntentContext.builder().build();
    }

    /**
     * Create a copy of this context with additional variables.
     *
     * @param additionalVariables variables to add to the copy
     * @return new IntentContext with merged variables
     */
    public IntentContext withAdditionalVariables(Map<String, Object> additionalVariables) {
        Map<String, Object> mergedVariables = new HashMap<>(this.variables);
        if (additionalVariables != null) {
            mergedVariables.putAll(additionalVariables);
        }

        return IntentContext.builder()
            .variables(mergedVariables)
            .fromAddress(this.fromAddress)
            .changeAddress(this.changeAddress)
            .resolvedVariables(new HashSet<>(this.resolvedVariables))
            .build();
    }
}
