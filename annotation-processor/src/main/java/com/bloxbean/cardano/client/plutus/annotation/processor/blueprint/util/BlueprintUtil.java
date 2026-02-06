package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.JsonPointerUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

public class BlueprintUtil {

    /**
     * Extracts the namespace (package path) from a blueprint definition reference key.
     *
     * <p>This method converts a blueprint reference key into a Java package namespace by:</p>
     * <ol>
     *   <li>Detecting and handling generic type instantiations (e.g., {@code Option<types/order/Action>})</li>
     *   <li>Extracting the innermost concrete type from nested generics</li>
     *   <li>Unescaping JSON Pointer sequences ({@code ~1} → {@code /})</li>
     *   <li>Splitting the path by {@code /} and taking all segments except the last (type name)</li>
     *   <li>Converting to lowercase dot-separated package notation</li>
     * </ol>
     *
     * <p><b>Examples for concrete types:</b></p>
     * <ul>
     *   <li>{@code "cardano/transaction/OutputReference"} → {@code "cardano.transaction"}</li>
     *   <li>{@code "types/order/Action"} → {@code "types.order"}</li>
     *   <li>{@code "types~1order~1Action"} → {@code "types.order"} (after unescaping)</li>
     *   <li>{@code "Int"} → {@code ""} (no namespace)</li>
     * </ul>
     *
     * <p><b>Examples for generic types (extracts namespace from innermost concrete type):</b></p>
     * <ul>
     *   <li>{@code "Option<types/order/Action>"} → {@code "types.order"}</li>
     *   <li>{@code "List<Option<types/order/Action>>"} → {@code "types.order"}</li>
     *   <li>{@code "Option<cardano/address/Credential>"} → {@code "cardano.address"}</li>
     *   <li>{@code "Option<Int>"} → {@code ""} (primitive, no namespace)</li>
     *   <li>{@code "Option$types~1order~1Action"} → {@code "types.order"} (dollar sign syntax)</li>
     * </ul>
     *
     * <p><b>Generic Type Handling:</b></p>
     * <p>When the key contains generic syntax ({@code <} or {@code $}), the method extracts
     * the innermost concrete type before namespace extraction. This prevents splitting at
     * {@code /} characters that appear inside generic brackets, which would produce invalid
     * package names like {@code "list<option<types.order"}.</p>
     *
     * @param key the blueprint definition reference key (may be null)
     * @return the namespace in dot-separated lowercase format, or empty string if no namespace exists
     */
    public static String getNamespaceFromReferenceKey(String key) {
        if (key == null)
            return "";

        // NEW: For generic types, extract innermost type first
        String typeToProcess = key;
        if (key.contains("<") || key.contains("$")) {
            typeToProcess = extractInnermostType(key);
            if (typeToProcess.isEmpty()) {
                return ""; // All primitives, no namespace
            }
        }

        // Unescape JSON Pointer sequences (types~1order~1Action → types/order/Action)
        typeToProcess = JsonPointerUtil.unescape(typeToProcess);

        String[] titleTokens = typeToProcess.split("\\/");

        StringBuilder ns = new StringBuilder();
        if (titleTokens.length > 1) {
            //Iterate titleTokens and create ns and remove last dot
            for (int i = 0; i < titleTokens.length - 1; i++) {
                ns.append(titleTokens[i]).append(".");
            }
            ns = new StringBuilder(ns.substring(0, ns.length() - 1));
        }

        if (ns.length() > 0) {
            ns = new StringBuilder(ns.toString().toLowerCase());
        }

        return ns.toString();
    }

    /**
     * Extracts the innermost concrete type from a generic type instantiation.
     * <p>
     * This method handles both angle bracket syntax (e.g., "Option&lt;types/order/Action&gt;")
     * and dollar sign syntax (e.g., "Option$types~1order~1Action").
     * </p>
     * <p>Examples:</p>
     * <ul>
     *   <li>"Option&lt;types/order/Action&gt;" → "types/order/Action"</li>
     *   <li>"List&lt;Option&lt;types/order/Action&gt;&gt;" → "types/order/Action" (recursive)</li>
     *   <li>"Option&lt;Int&gt;" → "" (primitive, no namespace)</li>
     *   <li>"Option$types~1order~1Action" → "types~1order~1Action"</li>
     *   <li>"Tuple&lt;&lt;types/order/Action,Int&gt;&gt;" → "types/order/Action"</li>
     * </ul>
     *
     * @param type the generic type string to process
     * @return the innermost concrete type with module path (contains /), or empty string if all primitives
     */
    private static String extractInnermostType(String type) {
        if (type == null || type.isEmpty()) {
            return "";
        }

        // Handle dollar sign syntax (Option$Int, Option$types~1order~1Action)
        if (type.contains("$")) {
            int dollarIndex = type.indexOf('$');
            String typeParam = type.substring(dollarIndex + 1);

            // If the type parameter itself contains generics, recurse
            if (typeParam.contains("<") || typeParam.contains("$")) {
                return extractInnermostType(typeParam);
            }

            // Return type parameter if it contains a module path, otherwise empty
            return typeParam.contains("/") || typeParam.contains("~1") ? typeParam : "";
        }

        // Handle angle bracket syntax
        if (!type.contains("<")) {
            // No generics, return type if it has module path
            return type.contains("/") || type.contains("~1") ? type : "";
        }

        // Extract content inside angle brackets
        int firstBracket = type.indexOf('<');
        int lastBracket = type.lastIndexOf('>');

        if (firstBracket == -1 || lastBracket == -1 || firstBracket >= lastBracket) {
            // Malformed, return original if it has module path
            return type.contains("/") || type.contains("~1") ? type : "";
        }

        String innerContent = type.substring(firstBracket + 1, lastBracket);

        // Handle nested generics and comma-separated type parameters
        // Find all type parameters at the current level
        java.util.List<String> typeParams = splitTypeParameters(innerContent);

        // Recursively process each type parameter and return the first one with a module path
        for (String param : typeParams) {
            String result = extractInnermostType(param.trim());
            if (!result.isEmpty()) {
                return result; // Return first type with module path
            }
        }

        return ""; // All primitives
    }

    /**
     * Splits comma-separated type parameters while respecting nested generics.
     * <p>
     * Example: "&lt;types/order/Action,Int&gt;" → ["types/order/Action", "Int"]
     * Example: "Option&lt;types/order/Action&gt;,Int" → ["Option&lt;types/order/Action&gt;", "Int"]
     * </p>
     *
     * @param content the content inside angle brackets
     * @return list of type parameters
     */
    private static java.util.List<String> splitTypeParameters(String content) {
        java.util.List<String> params = new java.util.ArrayList<>();
        if (content == null || content.isEmpty()) {
            return params;
        }

        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '<') {
                depth++;
                current.append(c);
            } else if (c == '>') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                // Comma at depth 0 is a separator
                if (current.length() > 0) {
                    params.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        // Add final parameter
        if (current.length() > 0) {
            params.add(current.toString());
        }

        return params;
    }

    /**
     * Extracts the namespace from a blueprint JSON reference.
     *
     * <p>This method processes a JSON reference string (typically from {@code $ref} fields in
     * blueprint schemas) by removing the {@code #/definitions/} prefix, unescaping JSON Pointer
     * sequences, and extracting the namespace using {@link #getNamespaceFromReferenceKey(String)}.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code "#/definitions/types~1order~1Action"} → {@code "types.order"}</li>
     *   <li>{@code "#/definitions/cardano~1transaction~1OutputReference"} → {@code "cardano.transaction"}</li>
     *   <li>{@code "#/definitions/Option<types~1order~1Action>"} → {@code "types.order"} (extracts from generic)</li>
     *   <li>{@code "#/definitions/Int"} → {@code ""} (no namespace)</li>
     * </ul>
     *
     * @param ref the JSON reference string (e.g., {@code "#/definitions/types~1order~1Action"})
     * @return the namespace in dot-separated lowercase format, or empty string if no namespace exists
     * @see #getNamespaceFromReferenceKey(String)
     */
    public static String getNamespaceFromReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JsonPointerUtil.unescape(ref);

        return getNamespaceFromReferenceKey(ref);
    }

    /**
     * Normalizes a blueprint JSON reference by removing the prefix and unescaping.
     *
     * <p>This method processes a JSON reference string by:</p>
     * <ol>
     *   <li>Removing the {@code #/definitions/} prefix</li>
     *   <li>Unescaping JSON Pointer sequences ({@code ~1} → {@code /}, {@code ~0} → {@code ~})</li>
     * </ol>
     *
     * <p>Unlike {@link #getNamespaceFromReference(String)}, this method returns the full
     * definition key including the type name, not just the namespace.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code "#/definitions/types~1order~1Action"} → {@code "types/order/Action"}</li>
     *   <li>{@code "#/definitions/cardano~1transaction~1OutputReference"} → {@code "cardano/transaction/OutputReference"}</li>
     *   <li>{@code "#/definitions/Option<types~1order~1Action>"} → {@code "Option<types/order/Action>"}</li>
     *   <li>{@code "#/definitions/Int"} → {@code "Int"}</li>
     * </ul>
     *
     * @param ref the JSON reference string (e.g., {@code "#/definitions/types~1order~1Action"})
     * @return the normalized definition key with JSON Pointer sequences unescaped
     */
    public static String normalizedReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JsonPointerUtil.unescape(ref);

        return ref;
    }

    /**
     * Extracts the class name (last segment) from a blueprint definition reference key.
     *
     * <p>This method extracts the final segment after the last slash in a definition key,
     * which represents the type name to be used for Java class generation.</p>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>{@code "types/custom/Data"} → {@code "Data"}</li>
     *   <li>{@code "cardano/transaction/OutputReference"} → {@code "OutputReference"}</li>
     *   <li>{@code "types~1order~1Action"} → {@code "Action"} (after unescaping)</li>
     *   <li>{@code "Int"} → {@code "Int"} (no namespace)</li>
     * </ul>
     *
     * @param key the blueprint definition reference key (may be null)
     * @return the class name (last segment), or empty string if key is null/empty
     */
    public static String getClassNameFromReferenceKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        // Unescape JSON Pointer sequences (types~1order~1Action → types/order/Action)
        String unescapedKey = JsonPointerUtil.unescape(key);

        // Split by forward slash and take the last segment
        String[] segments = unescapedKey.split("/");
        if (segments.length == 0) {
            return "";
        }

        return segments[segments.length - 1];
    }

    /**
     * Checks if a schema represents an opaque Plutus Data type according to CIP-57.
     *
     * <p>From CIP-57 specification:</p>
     * <blockquote>
     * "The dataType keyword is optional. When missing, the instance is implicitly typed
     * as an opaque Plutus Data."
     * </blockquote>
     *
     * <p>This method identifies schemas that should be mapped to the generic
     * {@code com.bloxbean.cardano.client.plutus.spec.PlutusData} type rather than
     * generating specific Java classes.</p>
     *
     * @param schema the schema to check
     * @return true if the schema represents opaque PlutusData (no dataType and no structure)
     * @see <a href="https://cips.cardano.org/cip/CIP-57">CIP-57 Plutus Contract Blueprints</a>
     */
    public static boolean isAbstractPlutusDataType(BlueprintSchema schema) {
        if (schema == null) { // if schema is available it means it a valid type
            return false;
        }

        // Per CIP-57: When dataType is missing, the instance is implicitly typed as opaque Plutus Data
        boolean hasNoDataType = schema.getDataType() == null;

        // Check if schema has no structural definition
        // If there's structure (anyOf, fields, etc.), it's a defined type, not opaque
        boolean hasNoStructure = schema.getAnyOf() == null &&
                schema.getFields() == null &&
                schema.getItems() == null &&
                schema.getKeys() == null &&
                schema.getValues() == null &&
                schema.getLeft() == null &&
                schema.getRight() == null;

        // Opaque Plutus Data = no dataType AND no structure
        return hasNoDataType && hasNoStructure;
    }
}
