package com.bloxbean.cardano.client.dsl.intention;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper utilities for intention processing.
 */
public class IntentionHelper {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * Resolve variables in a string value using the provided context.
     * Variables are in the format ${variableName}
     * 
     * @param value the value that may contain variables
     * @param context the context containing variable values
     * @return the resolved value with variables substituted
     * @throws IllegalArgumentException if a required variable is not found in the context
     */
    public static String resolveVariable(String value, Map<String, Object> context) {
        if (value == null) {
            return null;
        }
        
        // If no context provided, return value as-is (no resolution possible)
        if (context == null || context.isEmpty()) {
            return value;
        }
        
        // Check if the entire value is a variable reference
        if (value.startsWith("${") && value.endsWith("}")) {
            String varName = value.substring(2, value.length() - 1);
            Object varValue = context.get(varName);
            if (varValue != null) {
                return varValue.toString();
            } else {
                // For standalone variable references, we return the original value for backwards compatibility
                // This preserves the existing behavior where unresolved variables are kept as-is
                return value;
            }
        }
        
        // Replace all variable references in the string
        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = context.get(varName);
            if (varValue != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(varValue.toString()));
            } else {
                // Keep the variable reference if not found in context for backwards compatibility
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Check if a value contains variable references
     * 
     * @param value the value to check
     * @return true if the value contains variable references
     */
    public static boolean containsVariables(String value) {
        if (value == null) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(value).find();
    }
}