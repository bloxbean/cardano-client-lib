package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

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

    /** {@code true} for List/Set/SortedSet fields. */
    private boolean collectionType;

    /** {@code true} for Optional fields. */
    private boolean optionalType;

    /** Raw collection type: "java.util.List" / "java.util.Set" / "java.util.SortedSet". null for non-collections. */
    private String collectionKind;

    /**
     * {@code true} when this field belongs to a Java record.
     * In record mode, deserialization emits local variables instead of setter/direct-field assignments.
     */
    private boolean recordMode;

    /**
     * {@code true} when the field is required during deserialization.
     * Missing keys throw {@link IllegalArgumentException}.
     */
    private boolean required;

    /**
     * Default value string for deserialization when the key is absent.
     * {@code null} or empty means no default.
     */
    private String defaultValue;

    /**
     * {@code true} when the field type is {@code Map<String, V>}.
     */
    private boolean mapType;

    /** Fully qualified key type for Map fields ({@code "java.lang.String"}, {@code "java.lang.Integer"}, {@code "java.lang.Long"}, or {@code "java.math.BigInteger"}). */
    private String mapKeyTypeName;

    /** Fully qualified value type for Map fields. */
    private String mapValueTypeName;

    /** {@code true} when the Map value type is an enum. */
    private boolean mapValueEnumType;

    /** {@code true} when the Map value type is a {@code @MetadataType} annotated class. */
    private boolean mapValueNestedType;

    /** Fully qualified converter class name for nested Map values. */
    private String mapValueConverterFqn;

    // ── Composite: Map value is a collection (Map<String, List<T>>) ────

    /** {@code true} when the Map value type is a collection (List/Set/SortedSet). */
    private boolean mapValueCollectionType;

    /** Collection kind FQN for composite map values (e.g. "java.util.List"). */
    private String mapValueCollectionKind;

    /** Element type inside the collection value (the T in List&lt;T&gt;). */
    private String mapValueElementTypeName;

    /** {@code true} when the element of the collection value is an enum. */
    private boolean mapValueElementEnumType;

    /** {@code true} when the element of the collection value is a {@code @MetadataType}. */
    private boolean mapValueElementNestedType;

    /** Converter FQN for nested elements inside the collection value. */
    private String mapValueElementConverterFqn;

    // ── Composite: Map value is a map (Map<String, Map<String, V>>) ───

    /** {@code true} when the Map value type is itself a Map. */
    private boolean mapValueMapType;

    /** Key type of the inner map. */
    private String mapValueMapKeyTypeName;

    /** Value type of the inner map (the V in Map&lt;K, V&gt;). */
    private String mapValueMapValueTypeName;

    /** {@code true} when the inner map value type is an enum. */
    private boolean mapValueMapValueEnumType;

    /** {@code true} when the inner map value type is a {@code @MetadataType}. */
    private boolean mapValueMapValueNestedType;

    /** Converter FQN for nested inner map values. */
    private String mapValueMapValueConverterFqn;

    // ── Composite: Collection element is a collection (List<List<T>>) ─

    /** {@code true} when the collection element type is itself a collection. */
    private boolean elementCollectionType;

    /** Collection kind FQN for the inner collection (e.g. "java.util.List"). */
    private String elementCollectionKind;

    /** Element type inside the inner collection (the T in List&lt;T&gt;). */
    private String elementElementTypeName;

    /** {@code true} when the inner collection element is an enum. */
    private boolean elementElementEnumType;

    /** {@code true} when the inner collection element is a {@code @MetadataType}. */
    private boolean elementElementNestedType;

    /** Converter FQN for nested elements inside the inner collection. */
    private String elementElementConverterFqn;

    // ── Composite: Collection element is a map (List<Map<String, V>>) ─

    /** {@code true} when the collection element type is a Map. */
    private boolean elementMapType;

    /** Key type of the element map. */
    private String elementMapKeyTypeName;

    /** Value type of the element map (the V in Map&lt;K, V&gt;). */
    private String elementMapValueTypeName;

    /** {@code true} when the element map value is an enum. */
    private boolean elementMapValueEnumType;

    /** {@code true} when the element map value is a {@code @MetadataType}. */
    private boolean elementMapValueNestedType;

    /** Converter FQN for nested element map values. */
    private String elementMapValueConverterFqn;

    // ── Polymorphic type support ─────────────────────────────────────

    /**
     * {@code true} when the field type carries {@code @MetadataDiscriminator}
     * (a sealed interface or abstract class with polymorphic subtypes).
     */
    private boolean polymorphicType;

    /** The metadata map key used as discriminator (e.g. {@code "type"}). */
    private String discriminatorKey;

    /** Concrete subtypes for polymorphic dispatch. */
    @Builder.Default
    private List<PolymorphicSubtypeInfo> subtypes = Collections.emptyList();

    /**
     * Describes a single polymorphic subtype mapping.
     */
    public record PolymorphicSubtypeInfo(
            /** Discriminator value (e.g. {@code "image"}). */
            String discriminatorValue,
            /** FQN of the generated converter (e.g. {@code "com.example.ImageMediaMetadataConverter"}). */
            String converterFqn,
            /** FQN of the Java subtype class (e.g. {@code "com.example.ImageMedia"}). */
            String javaTypeFqn
    ) {}
}
