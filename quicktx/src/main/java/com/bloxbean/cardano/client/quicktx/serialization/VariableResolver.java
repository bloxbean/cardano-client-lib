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

}
