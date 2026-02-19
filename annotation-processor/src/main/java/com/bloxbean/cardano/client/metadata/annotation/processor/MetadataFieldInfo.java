package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import lombok.Data;

/**
 * Holds metadata about a single field in a {@code @MetadataType} annotated class,
 * used by the code generator to produce converter methods.
 */
@Data
public class MetadataFieldInfo {

    /** Java field name */
    private String javaFieldName;

    /** Key to use in MetadataMap (from @MetadataField(key=...) or the field name) */
    private String metadataKey;

    /**
     * Fully qualified Java type name.
     * Supported values: "java.lang.String", "java.math.BigInteger",
     * "java.lang.Long", "long", "java.lang.Integer", "int", "byte[]"
     */
    private String javaTypeName;

    /**
     * How this field should be stored in / read from Cardano metadata.
     * Defaults to {@link MetadataFieldType#DEFAULT}.
     */
    private MetadataFieldType as = MetadataFieldType.DEFAULT;

    /**
     * Getter method name (e.g. "getName"), or null if the field is accessed directly.
     */
    private String getterName;

    /**
     * Setter method name (e.g. "setName"), or null if the field is assigned directly.
     */
    private String setterName;
}
