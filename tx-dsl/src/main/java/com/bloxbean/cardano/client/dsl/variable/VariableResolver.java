package com.bloxbean.cardano.client.dsl.variable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving variable substitutions in YAML content.
 * Handles ${VAR} syntax replacement with actual values.
 */
public class VariableResolver {
    
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    
    /**
     * Resolves all variable placeholders in the given content.
     * 
     * @param content the content containing variable placeholders
     * @param variables map of variable names to values
     * @return content with variables resolved
     */
    public static String resolveVariables(String content, Map<String, Object> variables) {
        if (content == null || variables == null || variables.isEmpty()) {
            return content;
        }
        
        String result = content;
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        
        // Replace all ${VAR} patterns with actual values
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object varValue = variables.get(varName);
            
            if (varValue != null) {
                String placeholder = "${" + varName + "}";
                result = result.replace(placeholder, String.valueOf(varValue));
            }
        }
        
        return result;
    }
    
    /**
     * Extracts variables from the YAML content.
     * This is a simple implementation that looks for the variables section.
     * 
     * @param yamlContent the YAML content
     * @return map of extracted variables
     */
    public static Map<String, Object> extractVariables(String yamlContent) {
        Map<String, Object> variables = new HashMap<>();
        
        if (yamlContent == null) {
            return variables;
        }
        
        // Simple extraction of variables section
        // This is a basic implementation - could be enhanced for more complex YAML structures
        String[] lines = yamlContent.split("\n");
        boolean inVariablesSection = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.equals("variables:")) {
                inVariablesSection = true;
                continue;
            }
            
            if (inVariablesSection) {
                // Check if we've moved to a different section
                if (trimmed.endsWith(":") && !trimmed.startsWith(" ") && !trimmed.startsWith("\t")) {
                    break;
                }
                
                // Parse variable line: "  VAR_NAME: \"value\""
                if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    if (parts.length == 2) {
                        String varName = parts[0].trim();
                        String varValue = parts[1].trim();
                        
                        // Remove quotes if present
                        if ((varValue.startsWith("\"") && varValue.endsWith("\"")) ||
                            (varValue.startsWith("'") && varValue.endsWith("'"))) {
                            varValue = varValue.substring(1, varValue.length() - 1);
                        }
                        
                        variables.put(varName, varValue);
                    }
                }
            }
        }
        
        return variables;
    }
}