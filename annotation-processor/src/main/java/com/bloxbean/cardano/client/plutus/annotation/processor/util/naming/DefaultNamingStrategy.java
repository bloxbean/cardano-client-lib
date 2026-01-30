package com.bloxbean.cardano.client.plutus.annotation.processor.util.naming;

/**
 * Default implementation of NamingStrategy that handles all CIP-53 blueprint naming conventions.
 *
 * This implementation is designed to work with ANY blueprint naming style, including:
 * - Legacy style with $ and _: List$ByteArray, Tuple$Int_Int
 * - Modern style with angle brackets: List<Int>, Option<Data>
 * - Module paths with slashes: aiken/crypto/Hash, cardano/address/Credential
 * - Mixed styles: List<aiken/crypto/Hash>, Option<types~1order~1Action>
 *
 * All inputs are sanitized to produce valid Java identifiers that JavaPoet will accept.
 */
public class DefaultNamingStrategy implements NamingStrategy {

    @Override
    public String toClassName(String value) {
        return firstUpperCase(toCamelCase(value));
    }

    @Override
    public String toCamelCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Handle CIP-53 patterns first (angle brackets, slashes, tildes)
        String processed = preprocessForCamelCase(value);

        // Convert to camelCase by processing delimiters
        String camelCased = convertToCamelCase(processed, false);

        // Remove any remaining invalid characters
        return finalizeIdentifier(camelCased);
    }

    /**
     * Preprocesses a value for camel case conversion by handling CIP-53 patterns.
     * Handles tildes, angle brackets, and forward slashes, but preserves delimiters.
     *
     * @param value the input string
     * @return preprocessed string ready for camel case conversion
     */
    private String preprocessForCamelCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String result = value;

        // Unescape JSON Pointer sequences that may appear in blueprint $ref paths
        result = unescapeJsonPointer(result);

        // Handle angle brackets and generics: List<Int> -> ListOfInt
        result = sanitizeAngleBrackets(result);

        // Handle forward slashes (module paths): aiken/crypto/Hash -> AikenCryptoHash
        result = sanitizeForwardSlashes(result);

        return result;
    }

    /**
     * Finalizes an identifier by removing invalid characters and ensuring it's valid.
     *
     * @param value the input string
     * @return a valid Java identifier
     */
    private String finalizeIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Remove any remaining invalid characters
        String result = removeInvalidCharacters(value);

        // Ensure it's not empty after sanitization
        if (result.isEmpty()) {
            return "_";
        }

        // Ensure it starts with a valid Java identifier start character
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            result = "_" + result;
        }

        return result;
    }

    /**
     * Converts a string to camelCase or PascalCase by processing delimiters.
     *
     * @param value the input string
     * @param capitalizeFirst true for PascalCase, false for camelCase
     * @return the converted string
     */
    private String convertToCamelCase(String value, boolean capitalizeFirst) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Handle dollar signs specially - preserve them but process parts separately
        if (value.contains("$")) {
            String[] parts = value.split("\\$", -1);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    result.append("$");
                }
                if (!parts[i].isEmpty()) {
                    // First part respects capitalizeFirst, subsequent parts are PascalCase
                    boolean shouldCapitalize = (i == 0) ? capitalizeFirst : true;
                    result.append(convertToCamelCase(parts[i], shouldCapitalize));
                }
            }
            return result.toString();
        }

        // Check if the value contains any delimiters
        boolean hasDelimiters = value.contains("_") || value.contains("-") || value.contains(" ");

        // If no delimiters, just adjust first character if needed
        if (!hasDelimiters) {
            if (value.length() == 1) {
                return capitalizeFirst
                        ? value.toUpperCase()
                        : value.toLowerCase();
            }
            // Preserve the rest of the string, only change first character if needed
            char first = value.charAt(0);
            char correctFirst = capitalizeFirst
                    ? Character.toUpperCase(first)
                    : Character.toLowerCase(first);
            if (first == correctFirst) {
                return value;  // Already correct
            }
            return correctFirst + value.substring(1);
        }

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = capitalizeFirst;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            // Check if current char is a delimiter
            if (c == '_' || c == '-' || c == ' ') {
                // Skip delimiter and capitalize next letter
                capitalizeNext = true;
            } else {
                // Append character, capitalizing if needed
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            }
        }

        return result.toString();
    }

    @Override
    public String firstUpperCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    @Override
    public String firstLowerCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase() + value.substring(1);
    }

    @Override
    public String sanitizeIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String result = value;

        // Unescape JSON Pointer sequences that may appear in blueprint $ref paths
        result = unescapeJsonPointer(result);

        // Handle angle brackets and generics: List<Int> -> ListOfInt
        result = sanitizeAngleBrackets(result);

        // Handle forward slashes (module paths): aiken/crypto/Hash -> AikenCryptoHash
        result = sanitizeForwardSlashes(result);

        // Dollar signs are valid in Java identifiers, keep them as-is
        // Underscores are valid in Java identifiers, keep them as-is

        // Remove any remaining invalid characters
        result = removeInvalidCharacters(result);

        // Ensure it's not empty after sanitization
        if (result.isEmpty()) {
            return "_";
        }

        // Ensure it starts with a valid Java identifier start character
        if (!Character.isJavaIdentifierStart(result.charAt(0))) {
            result = "_" + result;
        }

        return result;
    }

    @Override
    public String toPackageNameFormat(String packageName) {
        if (packageName == null) {
            return null;
        }

        // Package names: lowercase, no hyphens, no underscores, no special chars
        return packageName.toLowerCase()
                .replace("-", "")
                .replace("_", "")
                .replace("/", "")
                .replace("~", "")
                .replace("<", "")
                .replace(">", "")
                .replace(",", "")
                .replace("$", "");
    }

    /**
     * Unescapes JSON Pointer escape sequences according to RFC 6901.
     *
     * <p>JSON Pointer (RFC 6901) is used in JSON Schema $ref paths to reference definitions.
     * When definition keys contain special characters, they must be escaped in $ref paths:
     * <ul>
     *   <li><b>~0</b> represents a literal tilde (~) character</li>
     *   <li><b>~1</b> represents a literal forward slash (/) character</li>
     * </ul>
     *
     * <p>These are the ONLY two escape sequences defined in JSON Pointer.
     * No other sequences like ~2, ~3, etc. exist in the specification.
     *
     * <p><b>Why only these two?</b>
     * <ul>
     *   <li>~ needs escaping because it's the escape character itself</li>
     *   <li>/ needs escaping because it's the path separator in JSON Pointer</li>
     * </ul>
     *
     * <p><b>Example from CIP-53 blueprints:</b>
     * <pre>
     * Blueprint definition key: "types/order/Action"
     * JSON $ref path:          "#/definitions/types~1order~1Action"
     * After unescaping:        "types/order/Action"
     * </pre>
     *
     * <p><b>Another example with tildes:</b>
     * <pre>
     * Blueprint definition key: "some~key"
     * JSON $ref path:          "#/definitions/some~0key"
     * After unescaping:        "some~key"
     * </pre>
     *
     * @param value the string potentially containing JSON Pointer escape sequences
     * @return the unescaped string
     * @see <a href="https://tools.ietf.org/html/rfc6901">RFC 6901 - JSON Pointer</a>
     */
    private String unescapeJsonPointer(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // IMPORTANT: Order matters! Must unescape ~1 before ~0 to avoid double-processing
        // Example: "~01" should become "/", not "~1"
        String result = value.replace("~1", "/");
        result = result.replace("~0", "~");

        return result;
    }

    /**
     * Converts angle bracket notation to descriptive format.
     *
     * Examples:
     * - List<Int> -> ListOfInt
     * - Option<Data> -> OptionOfData
     * - Tuple<Int,Int> -> TupleOfIntAndInt
     * - List<Tuple<Int,Option<Data>,Int>> -> ListOfTupleOfIntAndOptionOfDataAndInt
     *
     * @param value the input string with angle brackets
     * @return string with angle brackets converted to descriptive format
     */
    private String sanitizeAngleBrackets(String value) {
        if (!value.contains("<")) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        int depth = 0;
        boolean afterOpenBracket = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (c == '<') {
                depth++;
                // Every opening bracket adds "Of"
                result.append("Of");
                afterOpenBracket = true;
            } else if (c == '>') {
                depth--;
                afterOpenBracket = false;
                // Skip closing brackets
            } else if (c == ',' && depth > 0) {
                // Comma inside generics - use as separator
                result.append("And");
                afterOpenBracket = true;
            } else {
                // Regular character
                if (afterOpenBracket) {
                    // Capitalize first char after bracket/comma if it's lowercase
                    if (Character.isLowerCase(c)) {
                        result.append(Character.toUpperCase(c));
                    } else {
                        result.append(c);
                    }
                    afterOpenBracket = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    /**
     * Converts forward slashes to camel case.
     *
     * Examples:
     * - aiken/crypto/Hash -> AikenCryptoHash
     * - types/order/Action -> TypesOrderAction
     * - cardano/address/Credential -> CardanoAddressCredential
     *
     * @param value the input string with forward slashes
     * @return string with forward slashes converted to camel case
     */
    private String sanitizeForwardSlashes(String value) {
        if (!value.contains("/")) {
            return value;
        }

        String[] parts = value.split("/");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                // Capitalize first character of each part
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }

        return result.toString();
    }

    /**
     * Removes any remaining characters that are not valid in Java identifiers.
     * Keeps: letters, digits, $, _
     * Note: This keeps all valid identifier parts, including leading digits.
     * The finalizeIdentifier method will add underscore prefix if needed.
     *
     * @param value the input string
     * @return string with only valid Java identifier characters
     */
    private String removeInvalidCharacters(String value) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // Keep all valid identifier parts (letters, digits, $, _)
            // Even if they can't be at the start - finalizeIdentifier will fix that
            if (Character.isJavaIdentifierPart(c) || Character.isJavaIdentifierStart(c)) {
                result.append(c);
            }
        }

        return result.toString();
    }
}
