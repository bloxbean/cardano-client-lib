package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.JsonPointerUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

public class BlueprintUtil {

    //Exp: cardano/transaction/OutputReference
    //Output: cardano/transaction
    public static String getNSFromReferenceKey(String key) {
        if (key == null)
            return "";
        String[] titleTokens = key.split("\\/");
        String ns = "";

        if (titleTokens.length > 1) {
            //Iterate titleTokens and create ns and remove last dot
            for (int i = 0; i < titleTokens.length - 1; i++) {
                ns += titleTokens[i] + ".";
            }
            ns = ns.substring(0, ns.length() - 1);
        }

        if (ns != null && !ns.isEmpty()) {
            ns = ns.toLowerCase();
        }
        return ns;
    }

    //Exp: #/definitions/types~1automatic_payments~1AutomatedPayment
    //Output: types/automatic_payments
    public static String getNSFromReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JsonPointerUtil.unescape(ref);

        return getNSFromReferenceKey(ref);
    }

    public static String normalizedReference(String ref) {
        if (ref == null)
            return "";
        ref = ref.replace("#/definitions/", "");
        ref = JsonPointerUtil.unescape(ref);
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
