package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds metadata about a single field in a {@code @MetadataType} annotated class,
 * used by the code generator to produce converter methods.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private MetadataFieldType enc = MetadataFieldType.DEFAULT;

    /**
     * Getter method name (e.g. "getName"), or null if the field is accessed directly.
     */
    private String getterName;

    /**
     * Setter method name (e.g. "setName"), or null if the field is assigned directly.
     */
    private String setterName;

    /**
     * For {@code List<T>} fields: the fully-qualified element type (e.g. {@code "java.lang.String"}).
     * {@code null} for scalar fields.
     */
    private String elementTypeName;

    /**
     * {@code true} when the field type is a Java {@code enum}. The concrete enum class name
     * is stored in {@link #javaTypeName}.
     */
    private boolean enumType;

    /**
     * {@code true} when the element type of a collection or Optional field is an enum.
     * The concrete enum class name is stored in {@link #elementTypeName}.
     */
    private boolean elementEnumType;

    /**
     * {@code true} when the field type is another {@code @MetadataType} annotated class.
     * The converter class FQN is stored in {@link #nestedConverterFqn}.
     */
    private boolean nestedType;

    /**
     * {@code true} when the element type of a collection or Optional field is a
     * {@code @MetadataType} annotated class.
     */
    private boolean elementNestedType;

    /**
     * Fully qualified name of the nested converter class
     * (e.g. {@code "com.example.CustomerMetadataConverter"}).
     */
    private String nestedConverterFqn;

    /**
     * {@code true} when the field type is {@code Map<String, V>}.
     */
    private boolean mapType;

    /** Fully qualified key type for Map fields (currently only {@code "java.lang.String"}). */
    private String mapKeyTypeName;

    /** Fully qualified value type for Map fields. */
    private String mapValueTypeName;

    /** {@code true} when the Map value type is an enum. */
    private boolean mapValueEnumType;

    /** {@code true} when the Map value type is a {@code @MetadataType} annotated class. */
    private boolean mapValueNestedType;

    /** Fully qualified converter class name for nested Map values. */
    private String mapValueConverterFqn;
}
