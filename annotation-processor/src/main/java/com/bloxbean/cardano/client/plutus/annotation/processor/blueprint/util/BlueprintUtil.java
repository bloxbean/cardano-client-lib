package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

public class BlueprintUtil {

    /**
     * Extracts namespace from a blueprint type reference key.
     *
     * <p>Handles both simple module paths and generic types:
     * <ul>
     *   <li>Simple: {@code "cardano/transaction/OutputReference"} → {@code "cardano.transaction"}</li>
     *   <li>Generic: {@code "Option<cardano/address/Credential>"} → {@code "cardano.address"}</li>
     *   <li>Nested: {@code "List<Option<types/order/Action>>"} → {@code "types.order"}</li>
     * </ul>
     *
     * <p>Also handles JSON Pointer escapes per RFC 6901:
     * <ul>
     *   <li>{@code "types~1order~1Action"} → {@code "types.order"} (unescapes ~1 to /)</li>
     *   <li>{@code "data~0field"} → {@code "data~field"} (unescapes ~0 to ~)</li>
     * </ul>
     *
     * <p>For generic types, extracts the namespace from the <b>innermost type parameter</b>,
     * not from the outer generic type. This ensures that {@code Option<cardano/address/Credential>}
     * generates classes in the {@code cardano.address} namespace, not {@code option.cardano.address}.
     *
     * @param key the blueprint type reference key (may contain generics and JSON Pointer escapes)
     * @return the namespace as a dot-separated string, or empty string if no namespace
     */
    public static String getNSFromReferenceKey(String key) {
        if (key == null)
            return "";

        // Unescape JSON Pointer sequences first (RFC 6901: ~1 = /, ~0 = ~)
        // e.g., "types~1order~1Action" -> "types/order/Action"
        key = JavaFileUtil.unescapeJsonPointer(key);

        // Extract innermost type from generics first
        // e.g., "Option<cardano/address/Credential>" -> "cardano/address/Credential"
        // e.g., "List<Option<types/order/Action>>" -> "types/order/Action"
        String typeWithoutGenerics = extractInnermostType(key);

        String[] titleTokens = typeWithoutGenerics.split("\\/");
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
     * Extracts the innermost type from a potentially generic type declaration.
     *
     * <p>Recursively strips outer generic wrappers to find the core type:
     * <ul>
     *   <li>{@code "Option<cardano/address/Credential>"} → {@code "cardano/address/Credential"}</li>
     *   <li>{@code "List<Option<types/order/Action>>"} → {@code "types/order/Action"}</li>
     *   <li>{@code "Tuple<Int,Data>"} → {@code "Data"} (last type parameter)</li>
     *   <li>{@code "cardano/address/Credential"} → {@code "cardano/address/Credential"} (no change)</li>
     * </ul>
     *
     * <p>For tuple types with multiple parameters, returns the last parameter as it's
     * typically the most specific type for namespace determination.
     *
     * @param type the type declaration, possibly with generics
     * @return the innermost type without generic wrappers
     */
    private static String extractInnermostType(String type) {
        if (type == null || !type.contains("<")) {
            return type;
        }

        // Find the content between angle brackets
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');

        if (start == -1 || end == -1 || start >= end) {
            return type;
        }

        String innerContent = type.substring(start + 1, end);

        // Handle multiple type parameters (e.g., "Tuple<Int,Data>")
        // Use the last parameter as it's typically the most specific for namespace
        if (innerContent.contains(",")) {
            String[] params = innerContent.split(",");
            innerContent = params[params.length - 1].trim();
        }

        // Recursively extract if still contains generics
        return extractInnermostType(innerContent);
    }

    //Exp: #/definitions/types~1automatic_payments~1AutomatedPayment
    //Output: types/automatic_payments
    public static String getNSFromReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JavaFileUtil.unescapeJsonPointer(ref);

        return getNSFromReferenceKey(ref);
    }

    public static String normalizedReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JavaFileUtil.unescapeJsonPointer(ref);
        return ref;
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
        if (schema == null) {
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
