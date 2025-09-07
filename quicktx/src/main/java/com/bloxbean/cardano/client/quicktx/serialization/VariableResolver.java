package com.bloxbean.cardano.client.quicktx.serialization;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving variable placeholders in YAML content.
 * Supports ${variable} syntax for variable substitution.
 */
public class VariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * Resolve variables in the given template string.
     * 
     * @param template the template string containing ${variable} placeholders
     * @param variables the variables map for substitution
     * @return resolved string with variables substituted
     * @throws IllegalArgumentException if a required variable is not found
     */
    public static String resolve(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            
            if (value == null) {
                throw new IllegalArgumentException("Variable not found: " + variableName);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Resolve variables in the given template string with optional default values.
     * 
     * @param template the template string containing ${variable} or ${variable:default} placeholders
     * @param variables the variables map for substitution
     * @return resolved string with variables substituted
     */
    public static String resolveWithDefaults(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        
        if (variables == null || variables.isEmpty()) {
            // Check if template has variables that need defaults
            if (template.contains("${")) {
                throw new IllegalArgumentException("Variables required but none provided");
            }
            return template;
        }
        
        Pattern patternWithDefault = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
        Matcher matcher = patternWithDefault.matcher(template);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String defaultValue = matcher.group(2);
            Object value = variables.get(variableName);
            
            if (value == null) {
                if (defaultValue != null) {
                    value = defaultValue;
                } else {
                    throw new IllegalArgumentException("Variable not found: " + variableName);
                }
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }
        
        matcher.appendTail(result);
        return result.toString();
    }
    
    /**
     * Check if a string contains variable placeholders.
     * 
     * @param text the text to check
     * @return true if the text contains ${variable} placeholders
     */
    public static boolean containsVariables(String text) {
        return text != null && VARIABLE_PATTERN.matcher(text).find();
    }
    
    /**
     * Extract all variable names from a template string.
     * 
     * @param template the template string
     * @return array of variable names found in the template
     */
    public static String[] extractVariables(String template) {
        if (template == null) {
            return new String[0];
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        java.util.List<String> variables = new java.util.ArrayList<>();
        
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!variables.contains(variableName)) {
                variables.add(variableName);
            }
        }
        
        return variables.toArray(new String[0]);
    }
}