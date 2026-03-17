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

    // ── Shared sub-records ─────────────────────────────────────────────

    /**
     * Classifies a leaf type as enum, nested {@code @MetadataType}, or plain scalar.
     * Replaces the repeated {@code (enumType, nestedType, converterFqn)} triplet.
     */
    public record LeafClassification(boolean enumType, boolean nestedType, String converterFqn) {
        public static final LeafClassification NONE = new LeafClassification(false, false, null);
        /** Sentinel for supported scalar types (not enum, not nested). */
        public static final LeafClassification SCALAR = new LeafClassification(false, false, null);

        /** {@code true} when the type is a supported scalar (neither enum nor nested). */
        public boolean isScalar() { return !enumType && !nestedType; }
    }

    /**
     * Composite inner container for collection values (e.g. the {@code List<T>} in
     * {@code Map<String, List<T>>} or the inner {@code List<T>} in {@code List<List<T>>}).
     */
    public record CollectionCompositeInfo(String collectionKind, String elementTypeName,
                                          LeafClassification elementLeaf) {}

    /**
     * Composite inner container for map values (e.g. the {@code Map<K,V>} in
     * {@code Map<String, Map<K,V>>} or {@code List<Map<K,V>>}).
     */
    public record MapCompositeInfo(String keyTypeName, String valueTypeName,
                                   LeafClassification valueLeaf) {}

    /**
     * Describes the element type of a collection or Optional field (the T in {@code List<T>}
     * or {@code Optional<T>}).
     */
    public record ElementInfo(
            String typeName,
            LeafClassification leaf,
            CollectionCompositeInfo compositeCollection,  // nullable — present for List<List<T>>
            MapCompositeInfo compositeMap                  // nullable — present for List<Map<K,V>>
    ) {
        public static final ElementInfo NONE = new ElementInfo(null, LeafClassification.NONE, null, null);

        public boolean hasCompositeCollection() { return compositeCollection != null; }
        public boolean hasCompositeMap() { return compositeMap != null; }
    }

    /**
     * Describes a {@code Map<K, V>} field's type structure including composite values.
     */
    public record MapTypeInfo(
            String keyTypeName,
            String valueTypeName,
            LeafClassification valueLeaf,
            CollectionCompositeInfo valueCollection,  // nullable — present for Map<K, List<T>>
            MapCompositeInfo valueMap                  // nullable — present for Map<K, Map<K2,V2>>
    ) {
        public boolean hasValueCollection() { return valueCollection != null; }
        public boolean hasValueMap() { return valueMap != null; }
    }

    /**
     * Describes a polymorphic {@code @MetadataDiscriminator} field.
     */
    public record PolymorphicInfo(String discriminatorKey, List<PolymorphicSubtypeInfo> subtypes) {}

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

    // ── Identity (3 fields) ────────────────────────────────────────────

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

    // ── Encoding (3 fields) ────────────────────────────────────────────

    /**
     * How this field should be stored in / read from Cardano metadata.
     * Defaults to {@link MetadataFieldType#DEFAULT}.
     */
    @Builder.Default
    private MetadataFieldType enc = MetadataFieldType.DEFAULT;

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

    // ── Accessor (3 fields) ────────────────────────────────────────────

    /**
     * Getter method name (e.g. "getName"), or null if the field is accessed directly.
     */
    private String getterName;

    /**
     * Setter method name (e.g. "setName"), or null if the field is assigned directly.
     */
    private String setterName;

    /**
     * {@code true} when this field belongs to a Java record.
     * In record mode, deserialization emits local variables instead of setter/direct-field assignments.
     */
    private boolean recordMode;

    // ── Adapter (2 fields) ─────────────────────────────────────────────

    /** {@code true} when the field uses a custom {@code MetadataTypeAdapter}. */
    private boolean adapterType;

    /** Fully qualified name of the adapter class (e.g. {@code "com.example.EpochSecondsAdapter"}). */
    private String adapterFqn;

    // ── Encoder / Decoder (4 fields) ──────────────────────────────────

    /** {@code true} when the field has {@code @MetadataEncoder}. */
    private boolean encoderType;

    /** Fully qualified name of the encoder class. */
    private String encoderFqn;

    /** {@code true} when the field has {@code @MetadataDecoder}. */
    private boolean decoderType;

    /** Fully qualified name of the decoder class. */
    private String decoderFqn;

    // ── Type classification (flat dispatch flags) ──────────────────────

    /**
     * {@code true} when the field type is a Java {@code enum}. The concrete enum class name
     * is stored in {@link #javaTypeName}.
     */
    private boolean enumType;

    /**
     * {@code true} when the field type is another {@code @MetadataType} annotated class.
     * The converter class FQN is stored in {@link #nestedConverterFqn}.
     */
    private boolean nestedType;

    /**
     * Fully qualified name of the nested converter class
     * (e.g. {@code "com.example.CustomerMetadataConverter"}).
     * For scalar nested fields this is the direct converter; for collection/optional element
     * nested types it is the element's converter.
     */
    private String nestedConverterFqn;

    /** {@code true} for List/Set/SortedSet fields. */
    private boolean collectionType;

    /** {@code true} for Optional fields. */
    private boolean optionalType;

    /** Raw collection type: "java.util.List" / "java.util.Set" / "java.util.SortedSet". null for non-collections. */
    private String collectionKind;

    // ── Composed type info ─────────────────────────────────────────────

    /**
     * Element info for collection/optional fields (the T in List&lt;T&gt; or Optional&lt;T&gt;).
     * {@code ElementInfo.NONE} when not applicable.
     */
    @Builder.Default
    private ElementInfo element = ElementInfo.NONE;

    /**
     * Map type info. {@code null} when the field is not a Map.
     */
    private MapTypeInfo mapType;

    /**
     * Polymorphic type info. {@code null} when the field is not polymorphic.
     */
    private PolymorphicInfo polymorphic;

    /** {@code true} when any adapter, encoder, or decoder is configured — triggers resolver codegen. */
    public boolean hasAnyAdapter() { return adapterType || encoderType || decoderType; }

    // ── Convenience accessors for codegen dispatch ─────────────────────

    /** {@code true} when the field type is {@code Map<K, V>}. */
    public boolean isMapType() { return mapType != null; }

    /** {@code true} when the field type carries {@code @MetadataDiscriminator}. */
    public boolean isPolymorphicType() { return polymorphic != null; }

    /** {@code true} when the element type is an enum (collection/optional context). */
    public boolean isElementEnumType() { return element.leaf().enumType(); }

    /** {@code true} when the element type is nested (collection/optional context). */
    public boolean isElementNestedType() { return element.leaf().nestedType(); }

    /** The element type name (collection/optional context). */
    public String getElementTypeName() { return element.typeName(); }

    /** {@code true} when the collection element is itself a collection (List&lt;List&lt;T&gt;&gt;). */
    public boolean isElementCollectionType() { return element.hasCompositeCollection(); }

    /** {@code true} when the collection element is a Map (List&lt;Map&lt;K,V&gt;&gt;). */
    public boolean isElementMapType() { return element.hasCompositeMap(); }

    // ── Map convenience accessors ──────────────────────────────────────

    public String getMapKeyTypeName() { return mapType != null ? mapType.keyTypeName() : null; }
    public String getMapValueTypeName() { return mapType != null ? mapType.valueTypeName() : null; }
    public boolean isMapValueEnumType() { return mapType != null && mapType.valueLeaf().enumType(); }
    public boolean isMapValueNestedType() { return mapType != null && mapType.valueLeaf().nestedType(); }
    public String getMapValueConverterFqn() { return mapType != null ? mapType.valueLeaf().converterFqn() : null; }

    public boolean isMapValueCollectionType() { return mapType != null && mapType.hasValueCollection(); }
    public String getMapValueCollectionKind() { return mapType != null && mapType.valueCollection() != null ? mapType.valueCollection().collectionKind() : null; }
    public String getMapValueElementTypeName() { return mapType != null && mapType.valueCollection() != null ? mapType.valueCollection().elementTypeName() : null; }
    public boolean isMapValueElementEnumType() { return mapType != null && mapType.valueCollection() != null && mapType.valueCollection().elementLeaf().enumType(); }
    public boolean isMapValueElementNestedType() { return mapType != null && mapType.valueCollection() != null && mapType.valueCollection().elementLeaf().nestedType(); }
    public String getMapValueElementConverterFqn() { return mapType != null && mapType.valueCollection() != null ? mapType.valueCollection().elementLeaf().converterFqn() : null; }

    public boolean isMapValueMapType() { return mapType != null && mapType.hasValueMap(); }
    public String getMapValueMapKeyTypeName() { return mapType != null && mapType.valueMap() != null ? mapType.valueMap().keyTypeName() : null; }
    public String getMapValueMapValueTypeName() { return mapType != null && mapType.valueMap() != null ? mapType.valueMap().valueTypeName() : null; }
    public boolean isMapValueMapValueEnumType() { return mapType != null && mapType.valueMap() != null && mapType.valueMap().valueLeaf().enumType(); }
    public boolean isMapValueMapValueNestedType() { return mapType != null && mapType.valueMap() != null && mapType.valueMap().valueLeaf().nestedType(); }
    public String getMapValueMapValueConverterFqn() { return mapType != null && mapType.valueMap() != null ? mapType.valueMap().valueLeaf().converterFqn() : null; }

    // ── Element composite convenience accessors ────────────────────────

    public String getElementCollectionKind() { return element.compositeCollection() != null ? element.compositeCollection().collectionKind() : null; }
    public String getElementElementTypeName() { return element.compositeCollection() != null ? element.compositeCollection().elementTypeName() : null; }
    public boolean isElementElementEnumType() { return element.compositeCollection() != null && element.compositeCollection().elementLeaf().enumType(); }
    public boolean isElementElementNestedType() { return element.compositeCollection() != null && element.compositeCollection().elementLeaf().nestedType(); }
    public String getElementElementConverterFqn() { return element.compositeCollection() != null ? element.compositeCollection().elementLeaf().converterFqn() : null; }

    public String getElementMapKeyTypeName() { return element.compositeMap() != null ? element.compositeMap().keyTypeName() : null; }
    public String getElementMapValueTypeName() { return element.compositeMap() != null ? element.compositeMap().valueTypeName() : null; }
    public boolean isElementMapValueEnumType() { return element.compositeMap() != null && element.compositeMap().valueLeaf().enumType(); }
    public boolean isElementMapValueNestedType() { return element.compositeMap() != null && element.compositeMap().valueLeaf().nestedType(); }
    public String getElementMapValueConverterFqn() { return element.compositeMap() != null ? element.compositeMap().valueLeaf().converterFqn() : null; }

    // ── Polymorphic convenience accessors ──────────────────────────────

    public String getDiscriminatorKey() { return polymorphic != null ? polymorphic.discriminatorKey() : null; }
    public List<PolymorphicSubtypeInfo> getSubtypes() { return polymorphic != null ? polymorphic.subtypes() : Collections.emptyList(); }
}
