package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enumeration of different naming strategies for generated code.
 * Each strategy provides a transformation function for field and class names.
 */
public enum NamingStrategy {
    
    CAMEL_CASE("camelCase", NamingStrategy::toCamelCase),
    SNAKE_CASE("snake_case", NamingStrategy::toSnakeCase),
    PASCAL_CASE("PascalCase", NamingStrategy::toPascalCase),
    KEBAB_CASE("kebab-case", NamingStrategy::toKebabCase),
    UPPER_SNAKE_CASE("UPPER_SNAKE_CASE", NamingStrategy::toUpperSnakeCase);
    
    private final String displayName;
    private final Function<String, String> transformer;
    
    NamingStrategy(String displayName, Function<String, String> transformer) {
        this.displayName = displayName;
        this.transformer = transformer;
    }
    
    /**
     * Transforms the given name according to this naming strategy
     * 
     * @param name the name to transform
     * @return the transformed name
     */
    public String transform(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return transformer.apply(name);
    }
    
    /**
     * Returns the display name of this naming strategy
     * 
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    // Transformation functions
    private static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        String[] words = splitWords(input);
        if (words.length == 0) return input;
        
        StringBuilder result = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            result.append(capitalize(words[i]));
        }
        return result.toString();
    }
    
    private static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        return Arrays.stream(splitWords(input))
                .map(String::toLowerCase)
                .collect(Collectors.joining("_"));
    }
    
    private static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        return Arrays.stream(splitWords(input))
                .map(NamingStrategy::capitalize)
                .collect(Collectors.joining());
    }
    
    private static String toKebabCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        return Arrays.stream(splitWords(input))
                .map(String::toLowerCase)
                .collect(Collectors.joining("-"));
    }
    
    private static String toUpperSnakeCase(String input) {
        if (input == null || input.isEmpty()) return input;
        
        return Arrays.stream(splitWords(input))
                .map(String::toUpperCase)
                .collect(Collectors.joining("_"));
    }
    
    // Helper methods
    private static String[] splitWords(String input) {
        // Split on various delimiters and camelCase boundaries
        return input
                .replaceAll("([a-z])([A-Z])", "$1 $2") // camelCase boundaries
                .split("[\\s_-]+") // whitespace, underscore, hyphen
                .clone();
    }
    
    private static String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}