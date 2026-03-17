package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import javax.annotation.processing.Messager;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;

/**
 * Validates metadata field annotations: encoding rules, required/default
 * mutual exclusivity, and encoding compatibility with field types.
 */
class MetadataFieldValidator {

    private final Messager messager;

    MetadataFieldValidator(Messager messager) {
        this.messager = messager;
    }

    // ── Key and encoding resolution ───────────────────────────────────

    MetadataKeyAndEncoding resolveMetadataKeyAndEncoding(VariableElement ve, String fieldName, String typeName,
                                                          boolean hasElementType, boolean isMapType, boolean isNestedType,
                                                          String elementTypeName) {
        String metadataKey = fieldName;
        MetadataFieldType enc = MetadataFieldType.DEFAULT;
        boolean required = false;
        String defaultValue = null;
        MetadataField mf = ve.getAnnotation(MetadataField.class);
        if (mf != null) {
            if (!mf.key().isEmpty()) metadataKey = mf.key();
            enc = mf.enc();
            required = mf.required();
            if (!mf.defaultValue().isEmpty()) defaultValue = mf.defaultValue();
        }

        if (!validateRequiredAndDefault(ve, fieldName, typeName, required, defaultValue,
                hasElementType, isMapType, isNestedType)) {
            return null;
        }

        enc = validateEncoding(ve, fieldName, typeName, enc, hasElementType, isMapType, isNestedType, elementTypeName);
        if (enc == null) return null;

        return new MetadataKeyAndEncoding(metadataKey, enc, required, defaultValue);
    }

    // ── Validation helpers ────────────────────────────────────────────

    private boolean validateRequiredAndDefault(VariableElement ve, String fieldName, String typeName,
                                                boolean required, String defaultValue,
                                                boolean hasElementType, boolean isMapType, boolean isNestedType) {
        if (required && defaultValue != null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': 'required' and 'defaultValue' are mutually exclusive.", ve);
            return false;
        }

        boolean isOptional = OPTIONAL.equals(typeName) || typeName.startsWith(OPTIONAL + "<");
        if (required && isOptional) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': 'required = true' on Optional contradicts Optional semantics.", ve);
        }

        if (defaultValue != null && (hasElementType || isMapType || isNestedType || BYTE_ARRAY.equals(typeName))) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': 'defaultValue' is not supported on collection, map, Optional, nested, or byte[] types.", ve);
            return false;
        }
        return true;
    }

    private MetadataFieldType validateEncoding(VariableElement ve, String fieldName, String typeName,
                                                MetadataFieldType enc,
                                                boolean hasElementType, boolean isMapType, boolean isNestedType,
                                                String elementTypeName) {
        boolean isByteArrayCollectionEnc = hasElementType && !isMapType && !isNestedType
                && BYTE_ARRAY.equals(elementTypeName)
                && (enc == MetadataFieldType.STRING_HEX || enc == MetadataFieldType.STRING_BASE64);

        if ((hasElementType || isMapType || isNestedType) && enc != MetadataFieldType.DEFAULT
                && !isByteArrayCollectionEnc) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': @MetadataField(enc=...) is not supported on this field type; using DEFAULT.", ve);
            enc = MetadataFieldType.DEFAULT;
        }

        if (!isNestedType && !isMapType && !isByteArrayCollectionEnc && !isValidEnc(typeName, enc, ve)) {
            return null;
        }
        return enc;
    }

    private boolean isValidEnc(String typeName, MetadataFieldType enc, VariableElement ve) {
        return switch (enc) {
            case DEFAULT -> true;
            case STRING -> {
                if (typeName.equals(BYTE_ARRAY)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@MetadataField(enc=STRING) is ambiguous for byte[] — " +
                            "use STRING_HEX or STRING_BASE64 to specify the encoding.", ve);
                    yield false;
                }
                yield true;
            }
            case STRING_HEX, STRING_BASE64 -> {
                if (!typeName.equals(BYTE_ARRAY)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@MetadataField(enc=" + enc + ") is only valid for byte[] fields, " +
                            "but field has type '" + typeName + "'.", ve);
                    yield false;
                }
                yield true;
            }
            default -> true;
        };
    }

    // ── Inner record ──────────────────────────────────────────────────

    record MetadataKeyAndEncoding(String metadataKey, MetadataFieldType enc,
                                      boolean required, String defaultValue) {}
}
