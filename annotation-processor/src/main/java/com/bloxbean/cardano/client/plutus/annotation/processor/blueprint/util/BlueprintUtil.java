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
     * <p><b>IMPORTANT - Correct Logic (Fixed):</b></p>
     * <p>Per CIP-57, definition keys are the technical identifiers, not the "title" field.
     * The namespace is extracted from the <b>BASE TYPE</b> in the key, not from type parameters.</p>
     *
     * <p><b>Examples for types WITH module paths:</b></p>
     * <ul>
     *   <li>{@code "types/order/OrderDatum"} → {@code "types.order"} ✅</li>
     *   <li>{@code "cardano/address/Address"} → {@code "cardano.address"} ✅</li>
     *   <li>{@code "aiken/interval/IntervalBound<Int>"} → {@code "aiken.interval"} ✅ (base type has path)</li>
     * </ul>
     *
     * <p><b>Examples for types WITHOUT module paths:</b></p>
     * <ul>
     *   <li>{@code "Option<cardano/address/StakeCredential>"} → {@code ""} (base "Option" has NO path)</li>
     *   <li>{@code "List<Int>"} → {@code ""} (base "List" has NO path)</li>
     *   <li>{@code "Data"} → {@code ""} (primitive, NO path)</li>
     *   <li>{@code "Bool"} → {@code ""} (root-level ADT, NO path)</li>
     *   <li>{@code "Option$types~1order~1Action"} → {@code ""} (base "Option" has NO path)</li>
     * </ul>
     *
     * @param key the blueprint definition reference key (may be null)
     * @return the namespace in dot-separated lowercase format, or empty string if no namespace exists
     */
    public static String getNamespaceFromReferenceKey(String key) {
        if (key == null || key.isEmpty())
            return "";

        // FIXED: Extract BASE type (strip generics), not type parameter!
        // "Option<cardano/address/StakeCredential>" → "Option" (not "cardano/address/StakeCredential")
        // "aiken/interval/IntervalBound<Int>" → "aiken/interval/IntervalBound"
        // "types/order/OrderDatum" → "types/order/OrderDatum"
        String baseType = extractBaseType(key);

        // Unescape JSON Pointer sequences (types~1order~1Action → types/order/Action)
        baseType = JsonPointerUtil.unescape(baseType);

        // Check if BASE type has module path (contains "/")
        if (!baseType.contains("/")) {
            return ""; // No module path → empty namespace
        }

        // Extract namespace from BASE type
        String[] segments = baseType.split("/");
        if (segments.length <= 1) {
            return ""; // No namespace components
        }

        // Join all segments except the last (the class name)
        StringBuilder ns = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) ns.append(".");
            ns.append(segments[i]);
        }

        return ns.toString().toLowerCase();
    }

    /**
     * Extracts the base type from a definition key, stripping generic type parameters.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code "Option<cardano/address/StakeCredential>"} → {@code "Option"}</li>
     *   <li>{@code "List$Int"} → {@code "List"}</li>
     *   <li>{@code "aiken/interval/IntervalBound<Int>"} → {@code "aiken/interval/IntervalBound"}</li>
     *   <li>{@code "types/order/OrderDatum"} → {@code "types/order/OrderDatum"}</li>
     * </ul>
     *
     * @param key the definition key
     * @return the base type without generic parameters
     */
    private static String extractBaseType(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        // Find first generic delimiter
        int genericStart = key.indexOf('<');
        int dollarStart = key.indexOf('$');

        // Determine which delimiter comes first (if any)
        int delimiterPos = -1;
        if (genericStart != -1 && dollarStart != -1) {
            delimiterPos = Math.min(genericStart, dollarStart);
        } else if (genericStart != -1) {
            delimiterPos = genericStart;
        } else if (dollarStart != -1) {
            delimiterPos = dollarStart;
        }

        // If no generics, return the whole key
        if (delimiterPos == -1) {
            return key;
        }

        // Return everything before the generic delimiter
        return key.substring(0, delimiterPos);
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
     * Checks if a type name represents a built-in generic container that should be skipped
     * during class generation.
     *
     * <p>These types either map directly to Java standard-library types (e.g., {@code List} →
     * {@code java.util.List}) or are handled by specialised processors, so no generated class
     * is needed for them.</p>
     *
     * <p><b>Recognised container names:</b> List, Option, Optional, Tuple, Pair, Map, Dict,
     * Data, Redeemer</p>
     *
     * @param simpleName the simple type name to check (e.g., "List", "Option", "Data")
     * @return {@code true} if the name belongs to a built-in container that should be skipped
     */
    public static boolean isBuiltInGenericContainer(String simpleName) {
        return "List".equals(simpleName)
            || "Option".equals(simpleName)
            || "Optional".equals(simpleName)
            || "Tuple".equals(simpleName)
            || "Pair".equals(simpleName)
            || "Map".equals(simpleName)
            || "Dict".equals(simpleName)
            || "Data".equals(simpleName)
            || "Redeemer".equals(simpleName);
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
