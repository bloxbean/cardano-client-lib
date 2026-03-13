package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDiscriminator;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Extracts {@link MetadataFieldInfo} from {@code @MetadataType}-annotated classes.
 * Handles type detection, validation, accessor resolution, and hierarchy walking.
 */
@SuppressWarnings({
        "java:S1192", // Repeated warning message prefixes are clearer inline than as constants
        "java:S6541"  // detectFieldType is a dispatch method; complexity is inherent to the FieldTypeResult structure
})
public class MetadataFieldExtractor {

    private static final Set<String> COLLECTION_PREFIXES = Set.of(
            COLLECTION_LIST, COLLECTION_SET, COLLECTION_SORTED_SET);

    private final ProcessingEnvironment processingEnv;
    private final Messager messager;

    MetadataFieldExtractor(ProcessingEnvironment processingEnv, Messager messager) {
        this.processingEnv = processingEnv;
        this.messager = messager;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the given type element is a Java record.
     */
    public boolean isRecord(TypeElement typeElement) {
        return typeElement.getKind() == ElementKind.RECORD;
    }

    /**
     * Result of record field extraction, containing both the serializable fields
     * and the full component list (needed for constructor call generation).
     */
    public record RecordExtractionResult(
            List<MetadataFieldInfo> fields,
            List<MetadataConverterGenerator.RecordComponentInfo> allComponents
    ) {}

    /**
     * Extracts fields from a Java record's components, preserving declaration order
     * (critical for canonical constructor parameter matching).
     *
     * @return both the serializable fields and the full component list (including ignored)
     */
    public RecordExtractionResult extractRecordFields(TypeElement typeElement) {
        List<MetadataFieldInfo> fields = new ArrayList<>();
        List<MetadataConverterGenerator.RecordComponentInfo> allComponents = new ArrayList<>();
        Set<String> seenFieldNames = new LinkedHashSet<>();

        // Map from field name to VariableElement for annotation lookup
        Map<String, VariableElement> fieldElements = new LinkedHashMap<>();
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof VariableElement ve && enclosed.getKind() == ElementKind.FIELD) {
                fieldElements.put(ve.getSimpleName().toString(), ve);
            }
        }

        for (RecordComponentElement component : typeElement.getRecordComponents()) {
            String fieldName = component.getSimpleName().toString();
            String typeName = component.asType().toString();
            VariableElement ve = fieldElements.get(fieldName);

            // Always add to allComponents (needed for constructor)
            allComponents.add(new MetadataConverterGenerator.RecordComponentInfo(fieldName, typeName));

            if (ve == null) continue;
            if (shouldSkipField(ve, seenFieldNames)) continue;

            FieldTypeResult typeResult = detectFieldType(ve, fieldName, typeName);
            if (typeResult == null) continue;

            MetadataKeyAndEncoding keyEnc = resolveMetadataKeyAndEncoding(ve, fieldName, typeName,
                    typeResult.elementTypeName != null, typeResult.mapType,
                    typeResult.nestedType || typeResult.polymorphicType,
                    typeResult.elementTypeName);
            if (keyEnc == null) continue;

            // Record accessors: name() not getName(), no setter
            MetadataFieldInfo info = buildFieldInfo(fieldName, typeName, keyEnc,
                    new AccessorResult(fieldName, null), typeResult);
            info.setRecordMode(true);
            fields.add(info);
        }

        return new RecordExtractionResult(fields, allComponents);
    }

    /**
     * Extracts fields from the type and all its superclasses (inheritance).
     * Fields are collected bottom-up; shadowed names (child overrides parent) are skipped.
     */
    @SuppressWarnings("java:S135") // Multiple breaks for early-exit on hierarchy traversal is cleaner than alternatives
    public List<MetadataFieldInfo> extractFields(TypeElement typeElement, boolean hasLombok) {
        List<MetadataFieldInfo> fields = new ArrayList<>();
        Set<String> seenFieldNames = new LinkedHashSet<>();

        TypeElement current = typeElement;
        while (current != null) {
            List<MetadataFieldInfo> fieldsForType = extractFieldsForType(current, typeElement, hasLombok, seenFieldNames);
            fields.addAll(fieldsForType);

            TypeMirror superclass = current.getSuperclass();
            if (superclass.getKind() == TypeKind.NONE) break;
            Element superEl = processingEnv.getTypeUtils().asElement(superclass);
            if (!(superEl instanceof TypeElement)) break;
            current = (TypeElement) superEl;
            if (current.getQualifiedName().toString().equals(OBJECT)) break;
        }

        return fields;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean detectLombok(TypeElement typeElement) {
        try {
            Class lombokData = Class.forName("lombok.Data");
            Class lombokGetter = Class.forName("lombok.Getter");
            Class lombokSetter = Class.forName("lombok.Setter");

            return typeElement.getAnnotation(lombokData) != null ||
                    (typeElement.getAnnotation(lombokGetter) != null && typeElement.getAnnotation(lombokSetter) != null);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void validateNoArgConstructor(TypeElement typeElement, boolean hasLombok) {
        for (Element e : typeElement.getEnclosedElements()) {
            if (!(e instanceof ExecutableElement executableElement)) continue;
            if (executableElement.getKind() == ElementKind.CONSTRUCTOR
                    && executableElement.getParameters().isEmpty()
                    && executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                return;
            }
        }
        if (!hasLombok) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No public no-arg constructor found on '" + typeElement.getSimpleName() + "'. " +
                    "The generated fromMetadataMap() calls new " + typeElement.getSimpleName() + "(). " +
                    "Add a public no-arg constructor or use @lombok.NoArgsConstructor.", typeElement);
        }
    }

    // ── Per-class field extraction ──────────────────────────────────────

    @SuppressWarnings("java:S135") // Multiple continues for early-skip patterns is cleaner than deep nesting
    private List<MetadataFieldInfo> extractFieldsForType(TypeElement typeElement, TypeElement leafTypeElement,
                                                          boolean hasLombok, Set<String> seenFieldNames) {
        List<MetadataFieldInfo> fields = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (!(enclosed instanceof VariableElement ve)) continue;

            if (shouldSkipField(ve, seenFieldNames)) continue;

            String fieldName = ve.getSimpleName().toString();
            String typeName = ve.asType().toString();

            FieldTypeResult typeResult = detectFieldType(ve, fieldName, typeName);
            if (typeResult == null) continue; // unsupported type, warning already emitted

            MetadataKeyAndEncoding keyEnc = resolveMetadataKeyAndEncoding(ve, fieldName, typeName,
                    typeResult.elementTypeName != null, typeResult.mapType,
                    typeResult.nestedType || typeResult.polymorphicType,
                    typeResult.elementTypeName);
            if (keyEnc == null) continue; // invalid encoding, error already emitted

            AccessorResult accessors = resolveAccessors(leafTypeElement, ve, fieldName, hasLombok);
            if (accessors == null) continue; // no accessor found, warning already emitted

            fields.add(buildFieldInfo(fieldName, typeName, keyEnc, accessors, typeResult));
        }

        return fields;
    }

    // ── Phase 1: Skip check ────────────────────────────────────────────

    private boolean shouldSkipField(VariableElement ve, Set<String> seenFieldNames) {
        if (ve.getModifiers().contains(Modifier.STATIC)) return true;
        if (ve.getAnnotation(MetadataIgnore.class) != null) return true;
        String fieldName = ve.getSimpleName().toString();
        return !seenFieldNames.add(fieldName); // shadowed field
    }

    // ── Phase 2: Type detection ────────────────────────────────────────

    private FieldTypeResult detectFieldType(VariableElement ve, String fieldName, String typeName) {
        Element typeEl = processingEnv.getTypeUtils().asElement(ve.asType());
        boolean isEnum = typeEl != null && typeEl.getKind() == ElementKind.ENUM;

        // Resolve the field's type element once for annotation checks
        TypeElement fieldTypeElement = (!isEnum && typeEl instanceof TypeElement te) ? te : null;

        // Polymorphic @MetadataDiscriminator detection (must come before nested @MetadataType)
        boolean isPolymorphicType = false;
        String discriminatorKey = null;
        List<MetadataFieldInfo.PolymorphicSubtypeInfo> polymorphicSubtypes = List.of();
        if (fieldTypeElement != null
                && fieldTypeElement.getAnnotation(MetadataDiscriminator.class) != null) {
            PolymorphicDetectionResult polyResult = detectPolymorphicType(fieldTypeElement, ve, fieldName);
            if (polyResult != null) {
                isPolymorphicType = true;
                discriminatorKey = polyResult.discriminatorKey;
                polymorphicSubtypes = polyResult.subtypes;
            }
        }

        // Nested @MetadataType detection
        boolean isNestedType = false;
        String nestedConverterFqn = null;
        if (!isPolymorphicType && fieldTypeElement != null
                && fieldTypeElement.getAnnotation(MetadataType.class) != null) {
            isNestedType = true;
            String fieldPkg = processingEnv.getElementUtils().getPackageOf(fieldTypeElement).toString();
            nestedConverterFqn = fieldPkg + "." + fieldTypeElement.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
        }

        // Map<String, V> detection
        boolean isMapType = false;
        String mapKeyTypeName = null;
        String mapValueTypeName = null;
        boolean mapValueEnumType = false;
        boolean mapValueNestedType = false;
        String mapValueConverterFqn = null;
        // Composite map value fields
        boolean mapValueCollectionType = false;
        String mapValueCollectionKind = null;
        String mapValueElementTypeName = null;
        boolean mapValueElementEnumType = false;
        boolean mapValueElementNestedType = false;
        String mapValueElementConverterFqn = null;
        boolean mapValueMapType = false;
        String mapValueMapKeyTypeName = null;
        String mapValueMapValueTypeName = null;
        boolean mapValueMapValueEnumType = false;
        boolean mapValueMapValueNestedType = false;
        String mapValueMapValueConverterFqn = null;

        boolean isCollectionType = false;
        boolean isOptionalType = false;
        String collectionKind = null;

        TypeMirror fieldType = ve.asType();
        String rawType = null;
        DeclaredType declaredType = null;
        if (fieldType instanceof DeclaredType dt) {
            declaredType = dt;
            rawType = rawTypeName(dt);
        }

        if (MAP.equals(rawType)) {
            MapDetectionResult mapResult = detectMapType(ve, fieldName);
            if (mapResult == null) return null; // error or unsupported, already reported
            isMapType = true;
            mapKeyTypeName = mapResult.keyTypeName;
            mapValueTypeName = mapResult.valueTypeName;
            mapValueEnumType = mapResult.valueEnumType;
            mapValueNestedType = mapResult.valueNestedType;
            mapValueConverterFqn = mapResult.valueConverterFqn;
            mapValueCollectionType = mapResult.valueCollectionType;
            mapValueCollectionKind = mapResult.valueCollectionKind;
            mapValueElementTypeName = mapResult.valueElementTypeName;
            mapValueElementEnumType = mapResult.valueElementEnumType;
            mapValueElementNestedType = mapResult.valueElementNestedType;
            mapValueElementConverterFqn = mapResult.valueElementConverterFqn;
            mapValueMapType = mapResult.valueMapType;
            mapValueMapKeyTypeName = mapResult.valueMapKeyTypeName;
            mapValueMapValueTypeName = mapResult.valueMapValueTypeName;
            mapValueMapValueEnumType = mapResult.valueMapValueEnumType;
            mapValueMapValueNestedType = mapResult.valueMapValueNestedType;
            mapValueMapValueConverterFqn = mapResult.valueMapValueConverterFqn;
        }

        // Collection/Optional element detection
        ElementDetectionResult elemResult = detectElementType(ve, fieldName, isMapType, declaredType, rawType);
        isCollectionType = elemResult.isCollectionType;
        isOptionalType = elemResult.isOptionalType;
        collectionKind = elemResult.collectionKind;

        // Validate supported type
        if (!isEnum && !isNestedType && !isPolymorphicType && !isMapType && !isSupportedType(ve.asType())
                && !elemResult.hasRecognizedElement()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported type '" + typeName + "' and will be skipped.", ve);
            return null;
        }

        return new FieldTypeResult(isEnum, isNestedType, nestedConverterFqn,
                isCollectionType, isOptionalType, collectionKind,
                isMapType, mapKeyTypeName, mapValueTypeName, mapValueEnumType, mapValueNestedType, mapValueConverterFqn,
                mapValueCollectionType, mapValueCollectionKind, mapValueElementTypeName,
                mapValueElementEnumType, mapValueElementNestedType, mapValueElementConverterFqn,
                mapValueMapType, mapValueMapKeyTypeName, mapValueMapValueTypeName,
                mapValueMapValueEnumType, mapValueMapValueNestedType, mapValueMapValueConverterFqn,
                elemResult.elementTypeName,
                elemResult.elementEnumType,
                elemResult.elementNestedType,
                elemResult.elementNestedConverterFqn,
                elemResult.elementCollectionType,
                elemResult.elementCollectionKind,
                elemResult.elementElementTypeName,
                elemResult.elementElementEnumType,
                elemResult.elementElementNestedType,
                elemResult.elementElementConverterFqn,
                elemResult.elementMapType,
                elemResult.elementMapKeyTypeName,
                elemResult.elementMapValueTypeName,
                elemResult.elementMapValueEnumType,
                elemResult.elementMapValueNestedType,
                elemResult.elementMapValueConverterFqn,
                isPolymorphicType, discriminatorKey, polymorphicSubtypes);
    }

    /**
     * Detects collection/Optional element types including composite elements and enum/nested classification.
     * Returns {@link ElementDetectionResult#EMPTY} if the field is not a collection or Optional.
     */
    private ElementDetectionResult detectElementType(VariableElement ve, String fieldName,
                                                       boolean isMapType, DeclaredType declaredType, String rawType) {
        boolean isCollectionRawType = rawType != null && COLLECTION_PREFIXES.contains(rawType);
        if (isMapType || declaredType == null
                || (!isCollectionRawType && !OPTIONAL.equals(rawType))
                || declaredType.getTypeArguments().size() != 1) {
            return ElementDetectionResult.EMPTY;
        }

        String elementTypeName = declaredType.getTypeArguments().get(0).toString();
        boolean isCollectionType = isCollectionRawType;
        boolean isOptionalType = !isCollectionRawType;
        String collectionKind = isCollectionType ? rawType : null;

        ElementLeafClassification leaf = classifyElementLeaf(elementTypeName, isOptionalType, ve, fieldName);

        return new ElementDetectionResult(isCollectionType, isOptionalType, collectionKind,
                elementTypeName, leaf.enumType, leaf.nestedType, leaf.nestedConverterFqn,
                leaf.compositeCollection, leaf.compositeCollectionKind, leaf.compositeElementTypeName,
                leaf.compositeElementEnumType, leaf.compositeElementNestedType, leaf.compositeElementConverterFqn,
                leaf.compositeMap, leaf.compositeMapKeyTypeName, leaf.compositeMapValueTypeName,
                leaf.compositeMapValueEnumType, leaf.compositeMapValueNestedType, leaf.compositeMapValueConverterFqn);
    }

    /**
     * Classifies a collection/Optional element type as composite (collection/map), enum, nested, or scalar.
     */
    private ElementLeafClassification classifyElementLeaf(String elementTypeName, boolean isOptionalType,
                                                            VariableElement ve, String fieldName) {
        // Composite element detection (not for Optional)
        if (!isOptionalType && !isSupportedScalarType(elementTypeName)) {
            CompositeElementResult compositeResult = detectCompositeElement(ve, fieldName);
            if (compositeResult != null && compositeResult.isCollection) {
                return new ElementLeafClassification(false, false, null,
                        true, compositeResult.containerKind, compositeResult.leafTypeName,
                        compositeResult.leafEnumType, compositeResult.leafNestedType, compositeResult.leafConverterFqn,
                        false, null, null, false, false, null);
            }
            if (compositeResult != null && compositeResult.isMap) {
                return new ElementLeafClassification(false, false, null,
                        false, null, null, false, false, null,
                        true, compositeResult.mapKeyTypeName, compositeResult.leafTypeName,
                        compositeResult.leafEnumType, compositeResult.leafNestedType, compositeResult.leafConverterFqn);
            }
        }

        // Classify enum/nested element (only if not composite and not a supported scalar)
        if (!isSupportedScalarType(elementTypeName)) {
            LeafTypeClassification leafClass = classifyLeafType(elementTypeName);
            if (leafClass.isEnum || leafClass.isNested) {
                return new ElementLeafClassification(leafClass.isEnum, leafClass.isNested, leafClass.converterFqn,
                        false, null, null, false, false, null,
                        false, null, null, false, false, null);
            }
        }

        return ElementLeafClassification.NONE;
    }

    private MapDetectionResult detectMapType(VariableElement ve, String fieldName) {
        TypeMirror fieldTypeMirror = ve.asType();
        if (!(fieldTypeMirror instanceof DeclaredType declaredType)) return null;

        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs.size() != 2) return null;

        String keyType = typeArgs.get(0).toString();
        String valueType = typeArgs.get(1).toString();

        if (!isAllowedMapKeyType(keyType)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': Map key type must be String, Integer, Long, BigInteger, or byte[], but found '" + keyType + "'.", ve);
            return null;
        }

        // Check for composite value types
        TypeMirror valueMirror = typeArgs.get(1);
        if (valueMirror instanceof DeclaredType valueDeclared) {
            String valueRawType = rawTypeName(valueDeclared);

            if (COLLECTION_PREFIXES.contains(valueRawType)) {
                return detectMapCollectionValue(keyType, valueType, valueRawType, valueDeclared, ve, fieldName);
            }
            if (MAP.equals(valueRawType)) {
                return detectMapMapValue(keyType, valueType, valueDeclared, ve, fieldName);
            }
        }

        // Scalar / enum / nested map value
        return detectMapScalarValue(keyType, valueType, typeArgs.get(1), ve, fieldName);
    }

    private MapDetectionResult detectMapCollectionValue(String keyType, String valueType,
                                                          String valueRawType, DeclaredType valueDeclared,
                                                          VariableElement ve, String fieldName) {
        List<? extends TypeMirror> innerArgs = valueDeclared.getTypeArguments();
        if (innerArgs.size() != 1) return null;

        String innerElementType = innerArgs.get(0).toString();
        if (isDoubleNested(innerArgs.get(0))) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': double-nested composite types are not supported. Field will be skipped.", ve);
            return null;
        }

        LeafTypeClassification leafClass = classifyLeafType(innerElementType);
        if (leafClass.isScalar || leafClass.isEnum || leafClass.isNested) {
            return new MapDetectionResult(keyType, valueType, false, false, null,
                    true, valueRawType, innerElementType,
                    leafClass.isEnum, leafClass.isNested, leafClass.converterFqn,
                    false, null, null, false, false, null);
        }
        messager.printMessage(Diagnostic.Kind.WARNING,
                "Field '" + fieldName + "' has unsupported inner element type '" + innerElementType + "' and will be skipped.", ve);
        return null;
    }

    private MapDetectionResult detectMapMapValue(String keyType, String valueType,
                                                   DeclaredType valueDeclared,
                                                   VariableElement ve, String fieldName) {
        List<? extends TypeMirror> innerArgs = valueDeclared.getTypeArguments();
        if (innerArgs.size() != 2) return null;

        String innerKeyType = innerArgs.get(0).toString();
        if (!isAllowedMapKeyType(innerKeyType)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': inner Map key type must be String, Integer, Long, BigInteger, or byte[], but found '" + innerKeyType + "'.", ve);
            return null;
        }
        String innerValueType = innerArgs.get(1).toString();
        if (isDoubleNested(innerArgs.get(1))) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': double-nested composite types are not supported. Field will be skipped.", ve);
            return null;
        }

        LeafTypeClassification leafClass = classifyLeafType(innerValueType);
        if (leafClass.isScalar || leafClass.isEnum || leafClass.isNested) {
            return new MapDetectionResult(keyType, valueType, false, false, null,
                    false, null, null, false, false, null,
                    true, innerKeyType, innerValueType,
                    leafClass.isEnum, leafClass.isNested, leafClass.converterFqn);
        }
        messager.printMessage(Diagnostic.Kind.WARNING,
                "Field '" + fieldName + "' has unsupported inner Map value type '" + innerValueType + "' and will be skipped.", ve);
        return null;
    }

    private MapDetectionResult detectMapScalarValue(String keyType, String valueType,
                                                      TypeMirror valueMirror,
                                                      VariableElement ve, String fieldName) {
        boolean valueEnumType = false;
        boolean valueNestedType = false;
        String valueConverterFqn = null;

        Element valEl = processingEnv.getTypeUtils().asElement(valueMirror);
        if (valEl != null && valEl.getKind() == ElementKind.ENUM) {
            valueEnumType = true;
        } else if (valEl instanceof TypeElement valTypeEl
                && valTypeEl.getAnnotation(MetadataType.class) != null) {
            valueNestedType = true;
            String valPkg = processingEnv.getElementUtils().getPackageOf(valTypeEl).toString();
            valueConverterFqn = valPkg + "." + valTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
        }

        if (!valueEnumType && !valueNestedType && !isSupportedScalarType(valueType)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported Map value type '" + valueType + "' and will be skipped.", ve);
            return null;
        }

        return new MapDetectionResult(keyType, valueType, valueEnumType, valueNestedType, valueConverterFqn,
                false, null, null, false, false, null,
                false, null, null, false, false, null);
    }

    /**
     * Detects composite element types for collections: List<List<T>> or List<Map<String, V>>.
     * Returns null if the element type is not a composite (will be handled as scalar/enum/nested).
     */
    private CompositeElementResult detectCompositeElement(VariableElement ve, String fieldName) {
        TypeMirror fieldTypeMirror = ve.asType();
        if (!(fieldTypeMirror instanceof DeclaredType outerDeclared)) return null;

        List<? extends TypeMirror> outerArgs = outerDeclared.getTypeArguments();
        if (outerArgs.size() != 1) return null;

        TypeMirror elementMirror = outerArgs.get(0);
        if (!(elementMirror instanceof DeclaredType elementDeclared)) return null;

        String elementRawType = rawTypeName(elementDeclared);

        if (COLLECTION_PREFIXES.contains(elementRawType)) {
            return detectCollectionCompositeElement(elementDeclared, elementRawType, ve, fieldName);
        }
        if (MAP.equals(elementRawType)) {
            return detectMapCompositeElement(elementDeclared, ve, fieldName);
        }
        return null;
    }

    private CompositeElementResult detectCollectionCompositeElement(DeclaredType elementDeclared,
                                                                      String elementRawType,
                                                                      VariableElement ve, String fieldName) {
        List<? extends TypeMirror> innerArgs = elementDeclared.getTypeArguments();
        if (innerArgs.size() != 1) return null;

        String innerElementType = innerArgs.get(0).toString();
        if (isDoubleNested(innerArgs.get(0))) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': double-nested composite types are not supported. Field will be skipped.", ve);
            return null;
        }

        LeafTypeClassification leafClass = classifyLeafType(innerElementType);
        if (leafClass.isScalar || leafClass.isEnum || leafClass.isNested) {
            return new CompositeElementResult(true, false, elementRawType,
                    null, innerElementType, leafClass.isEnum, leafClass.isNested, leafClass.converterFqn);
        }
        return null;
    }

    private CompositeElementResult detectMapCompositeElement(DeclaredType elementDeclared,
                                                               VariableElement ve, String fieldName) {
        List<? extends TypeMirror> innerArgs = elementDeclared.getTypeArguments();
        if (innerArgs.size() != 2) return null;

        String innerKeyType = innerArgs.get(0).toString();
        if (!isAllowedMapKeyType(innerKeyType)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': inner Map key type must be String, Integer, Long, BigInteger, or byte[], but found '" + innerKeyType + "'.", ve);
            return null;
        }
        String innerValueType = innerArgs.get(1).toString();
        if (isDoubleNested(innerArgs.get(1))) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': double-nested composite types are not supported. Field will be skipped.", ve);
            return null;
        }

        LeafTypeClassification leafClass = classifyLeafType(innerValueType);
        if (leafClass.isScalar || leafClass.isEnum || leafClass.isNested) {
            return new CompositeElementResult(false, true, null,
                    innerKeyType, innerValueType, leafClass.isEnum, leafClass.isNested, leafClass.converterFqn);
        }
        return null;
    }

    /**
     * Checks if a type argument is itself a collection or map (double nesting).
     * Returns true if the type is double-nested and should be rejected.
     */
    private boolean isDoubleNested(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declared) {
            String raw = rawTypeName(declared);
            return COLLECTION_PREFIXES.contains(raw) || MAP.equals(raw);
        }
        return false;
    }

    // ── Polymorphic type detection ─────────────────────────────────────

    private PolymorphicDetectionResult detectPolymorphicType(TypeElement fieldTypeEl,
                                                              VariableElement ve, String fieldName) {
        MetadataDiscriminator disc = fieldTypeEl.getAnnotation(MetadataDiscriminator.class);
        String key = disc.key();
        MetadataSubtypeEntry[] entries = extractSubtypeEntries(fieldTypeEl);

        if (entries.length == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': @MetadataDiscriminator on '"
                            + fieldTypeEl.getSimpleName() + "' must declare at least one subtype.", ve);
            return null;
        }

        List<MetadataFieldInfo.PolymorphicSubtypeInfo> subtypes = new ArrayList<>();
        for (MetadataSubtypeEntry entry : entries) {
            TypeElement subtypeEl = (TypeElement) processingEnv.getTypeUtils().asElement(entry.typeMirror);
            if (subtypeEl == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + fieldName + "': cannot resolve subtype class for discriminator value '"
                                + entry.value + "'.", ve);
                return null;
            }
            if (subtypeEl.getAnnotation(MetadataType.class) == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + fieldName + "': subtype '" + subtypeEl.getSimpleName()
                                + "' must be annotated with @MetadataType.", ve);
                return null;
            }
            String pkg = processingEnv.getElementUtils().getPackageOf(subtypeEl).toString();
            String converterFqn = pkg + "." + subtypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            subtypes.add(new MetadataFieldInfo.PolymorphicSubtypeInfo(
                    entry.value, converterFqn, subtypeEl.getQualifiedName().toString()));
        }

        return new PolymorphicDetectionResult(key, subtypes);
    }

    /**
     * Extracts subtype entries from {@code @MetadataDiscriminator} using the
     * {@code AnnotationMirror} API to safely read {@code Class<?>} values as {@code TypeMirror}.
     */
    private MetadataSubtypeEntry[] extractSubtypeEntries(TypeElement fieldTypeEl) {
        AnnotationMirror discMirror = findAnnotationMirror(fieldTypeEl, MetadataDiscriminator.class);
        if (discMirror == null) return new MetadataSubtypeEntry[0];

        Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
                processingEnv.getElementUtils().getElementValuesWithDefaults(discMirror);

        List<MetadataSubtypeEntry> result = new ArrayList<>();
        for (var entry : elementValues.entrySet()) {
            if (!"subtypes".equals(entry.getKey().getSimpleName().toString())) continue;

            @SuppressWarnings("unchecked")
            List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) entry.getValue().getValue();
            for (AnnotationValue av : values) {
                AnnotationMirror subtypeMirror = (AnnotationMirror) av.getValue();
                Map<? extends ExecutableElement, ? extends AnnotationValue> subtypeValues =
                        processingEnv.getElementUtils().getElementValuesWithDefaults(subtypeMirror);

                String value = null;
                TypeMirror typeMirror = null;
                for (var se : subtypeValues.entrySet()) {
                    String name = se.getKey().getSimpleName().toString();
                    if ("value".equals(name)) {
                        value = (String) se.getValue().getValue();
                    } else if ("type".equals(name)) {
                        typeMirror = (TypeMirror) se.getValue().getValue();
                    }
                }
                if (value != null && typeMirror != null) {
                    result.add(new MetadataSubtypeEntry(value, typeMirror));
                }
            }
            break;
        }
        return result.toArray(new MetadataSubtypeEntry[0]);
    }

    /**
     * Finds the {@link AnnotationMirror} for a given annotation class on the element,
     * using type comparison instead of string matching.
     */
    private AnnotationMirror findAnnotationMirror(Element element, Class<?> annotationClass) {
        TypeMirror targetType = processingEnv.getElementUtils()
                .getTypeElement(annotationClass.getCanonicalName()).asType();
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), targetType)) {
                return am;
            }
        }
        return null;
    }

    // ── Leaf type classification helper ────────────────────────────────

    private LeafTypeClassification classifyLeafType(String typeName) {
        if (isSupportedScalarType(typeName)) {
            return new LeafTypeClassification(false, false, null, true);
        }
        TypeElement te = processingEnv.getElementUtils().getTypeElement(typeName);
        if (te != null && te.getKind() == ElementKind.ENUM) {
            return new LeafTypeClassification(true, false, null, false);
        }
        if (te != null && te.getAnnotation(MetadataType.class) != null) {
            String pkg = processingEnv.getElementUtils().getPackageOf(te).toString();
            String fqn = pkg + "." + te.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            return new LeafTypeClassification(false, true, fqn, false);
        }
        return new LeafTypeClassification(false, false, null, false);
    }

    /**
     * Extracts the raw type name from a DeclaredType (e.g. "java.util.List" from "java.util.List<String>").
     */
    private String rawTypeName(DeclaredType declaredType) {
        return ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
    }

    // ── Phase 3: Metadata key and encoding ─────────────────────────────

    private MetadataKeyAndEncoding resolveMetadataKeyAndEncoding(VariableElement ve, String fieldName, String typeName,
                                                                  boolean hasElementType, boolean isMapType, boolean isNestedType,
                                                                  String elementTypeName) {
        String metadataKey = fieldName;
        MetadataFieldType enc = MetadataFieldType.DEFAULT;
        boolean required = false;
        String defaultValue = null;
        MetadataField mf = ve.getAnnotation(MetadataField.class);
        if (mf != null) {
            if (!mf.key().isEmpty()) {
                metadataKey = mf.key();
            }
            enc = mf.enc();
            required = mf.required();
            if (!mf.defaultValue().isEmpty()) {
                defaultValue = mf.defaultValue();
            }
        }

        // Validate required + defaultValue mutual exclusivity
        if (required && defaultValue != null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': 'required' and 'defaultValue' are mutually exclusive.", ve);
            return null;
        }

        // Validate required on Optional (warning)
        boolean isOptional = OPTIONAL.equals(typeName) || typeName.startsWith(OPTIONAL + "<");
        if (required && isOptional) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': 'required = true' on Optional contradicts Optional semantics.", ve);
        }

        // Validate defaultValue only on scalar/enum fields
        if (defaultValue != null) {
            boolean isComplexType = hasElementType || isMapType || isNestedType
                    || BYTE_ARRAY.equals(typeName);
            if (isComplexType) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Field '" + fieldName + "': 'defaultValue' is not supported on collection, map, Optional, nested, or byte[] types.", ve);
                return null;
            }
        }

        // Allow enc=STRING_HEX/STRING_BASE64 on byte[] element collections; warn and reset for all others
        boolean isByteArrayCollectionEnc = hasElementType && !isMapType && !isNestedType
                && BYTE_ARRAY.equals(elementTypeName)
                && (enc == MetadataFieldType.STRING_HEX || enc == MetadataFieldType.STRING_BASE64);

        if ((hasElementType || isMapType || isNestedType) && enc != MetadataFieldType.DEFAULT) {
            if (!isByteArrayCollectionEnc) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "Field '" + fieldName + "': @MetadataField(enc=...) is not supported on this field type; using DEFAULT.", ve);
                enc = MetadataFieldType.DEFAULT;
            }
        }

        if (!isNestedType && !isMapType && !isByteArrayCollectionEnc && !isValidEnc(typeName, enc, ve)) {
            return null;
        }

        return new MetadataKeyAndEncoding(metadataKey, enc, required, defaultValue);
    }

    // ── Phase 4: Accessor resolution ───────────────────────────────────

    private AccessorResult resolveAccessors(TypeElement leafTypeElement, VariableElement ve,
                                             String fieldName, boolean hasLombok) {
        String getterName = null;
        ExecutableElement getter = findGetter(leafTypeElement, ve);
        if (getter != null) {
            getterName = getter.getSimpleName().toString();
        } else if (hasLombok) {
            getterName = "get" + capitalize(fieldName);
        } else if (!isDirectlyAccessible(ve)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No getter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
            return null;
        }

        String setterName = null;
        ExecutableElement setter = findSetter(leafTypeElement, ve);
        if (setter != null) {
            setterName = setter.getSimpleName().toString();
        } else if (hasLombok) {
            setterName = "set" + capitalize(fieldName);
        } else if (!isDirectlyAccessible(ve)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No setter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
            return null;
        }

        return new AccessorResult(getterName, setterName);
    }

    // ── Phase 5: Build MetadataFieldInfo ───────────────────────────────

    private MetadataFieldInfo buildFieldInfo(String fieldName, String typeName,
                                              MetadataKeyAndEncoding keyEnc, AccessorResult accessors,
                                              FieldTypeResult type) {
        return MetadataFieldInfo.builder()
                .javaFieldName(fieldName)
                .metadataKey(keyEnc.metadataKey)
                .javaTypeName(typeName)
                .enc(keyEnc.enc)
                .required(keyEnc.required)
                .defaultValue(keyEnc.defaultValue)
                .getterName(accessors.getterName)
                .setterName(accessors.setterName)
                .elementTypeName(type.elementTypeName)
                .enumType(type.enumType)
                .elementEnumType(type.elementEnumType)
                .nestedType(type.nestedType)
                .collectionType(type.collectionType)
                .optionalType(type.optionalType)
                .collectionKind(type.collectionKind)
                .elementNestedType(type.elementNestedType)
                .nestedConverterFqn(type.nestedType ? type.nestedConverterFqn : type.elementNestedConverterFqn)
                .mapType(type.mapType)
                .mapKeyTypeName(type.mapKeyTypeName)
                .mapValueTypeName(type.mapValueTypeName)
                .mapValueEnumType(type.mapValueEnumType)
                .mapValueNestedType(type.mapValueNestedType)
                .mapValueConverterFqn(type.mapValueConverterFqn)
                // Composite: Map value is collection
                .mapValueCollectionType(type.mapValueCollectionType)
                .mapValueCollectionKind(type.mapValueCollectionKind)
                .mapValueElementTypeName(type.mapValueElementTypeName)
                .mapValueElementEnumType(type.mapValueElementEnumType)
                .mapValueElementNestedType(type.mapValueElementNestedType)
                .mapValueElementConverterFqn(type.mapValueElementConverterFqn)
                // Composite: Map value is map
                .mapValueMapType(type.mapValueMapType)
                .mapValueMapKeyTypeName(type.mapValueMapKeyTypeName)
                .mapValueMapValueTypeName(type.mapValueMapValueTypeName)
                .mapValueMapValueEnumType(type.mapValueMapValueEnumType)
                .mapValueMapValueNestedType(type.mapValueMapValueNestedType)
                .mapValueMapValueConverterFqn(type.mapValueMapValueConverterFqn)
                // Composite: Collection element is collection
                .elementCollectionType(type.elementCollectionType)
                .elementCollectionKind(type.elementCollectionKind)
                .elementElementTypeName(type.elementElementTypeName)
                .elementElementEnumType(type.elementElementEnumType)
                .elementElementNestedType(type.elementElementNestedType)
                .elementElementConverterFqn(type.elementElementConverterFqn)
                // Composite: Collection element is map
                .elementMapType(type.elementMapType)
                .elementMapKeyTypeName(type.elementMapKeyTypeName)
                .elementMapValueTypeName(type.elementMapValueTypeName)
                .elementMapValueEnumType(type.elementMapValueEnumType)
                .elementMapValueNestedType(type.elementMapValueNestedType)
                .elementMapValueConverterFqn(type.elementMapValueConverterFqn)
                // Polymorphic
                .polymorphicType(type.polymorphicType)
                .discriminatorKey(type.discriminatorKey)
                .subtypes(type.polymorphicSubtypes != null ? type.polymorphicSubtypes : List.of())
                .build();
    }

    // ── Accessor helpers ───────────────────────────────────────────────

    private ExecutableElement findGetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String getterMethodName = "get" + capitalize(fieldName);
        String isGetterMethodName = "is" + capitalize(fieldName);
        String fieldTypeName = variableElement.asType().toString();
        boolean isBooleanType = fieldTypeName.equals(PRIM_BOOLEAN) || fieldTypeName.equals(BOOLEAN);

        for (Element enclosedElement : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (!(enclosedElement instanceof ExecutableElement executableElement)) continue;

            String methodName = executableElement.getSimpleName().toString();
            boolean nameMatches = methodName.equals(getterMethodName)
                    || (isBooleanType && methodName.equals(isGetterMethodName));

            if (nameMatches &&
                    executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                    executableElement.getParameters().isEmpty() &&
                    executableElement.getReturnType().toString().equals(fieldTypeName)) {
                return executableElement;
            }
        }

        return null;
    }

    private ExecutableElement findSetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String setterMethodName = "set" + capitalize(fieldName);

        for (Element enclosedElement : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (!(enclosedElement instanceof ExecutableElement executableElement)) continue;

            if (executableElement.getSimpleName().toString().equals(setterMethodName) &&
                    executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                    executableElement.getParameters().size() == 1 &&
                    executableElement.getParameters().get(0).asType().toString()
                            .equals(variableElement.asType().toString()) &&
                    executableElement.getReturnType().getKind().equals(TypeKind.VOID)) {
                return executableElement;
            }
        }

        return null;
    }

    // ── Type validation helpers ────────────────────────────────────────

    private boolean isAllowedMapKeyType(String keyType) {
        return switch (keyType) {
            case STRING, INTEGER, PRIM_INT, LONG, PRIM_LONG, BIG_INTEGER, BYTE_ARRAY -> true;
            default -> false;
        };
    }

    private boolean isDirectlyAccessible(VariableElement ve) {
        return ve.getModifiers().contains(Modifier.PUBLIC) || ve.getModifiers().isEmpty();
    }

    private boolean isSupportedType(TypeMirror typeMirror) {
        if (isSupportedScalarType(typeMirror.toString())) return true;
        if (!(typeMirror instanceof DeclaredType dt)) return false;
        String raw = rawTypeName(dt);
        if (!COLLECTION_PREFIXES.contains(raw) && !OPTIONAL.equals(raw)) return false;
        if (dt.getTypeArguments().size() != 1) return false;
        String elemType = dt.getTypeArguments().get(0).toString();
        if (COLLECTION_SORTED_SET.equals(raw)) {
            return isSupportedScalarType(elemType)
                    && !BYTE_ARRAY.equals(elemType)
                    && !URL.equals(elemType)
                    && !CURRENCY.equals(elemType)
                    && !LOCALE.equals(elemType);
        }
        return isSupportedScalarType(elemType);
    }

    private boolean isSupportedScalarType(String typeName) {
        return switch (typeName) {
            case STRING, BIG_INTEGER, BIG_DECIMAL, LONG, PRIM_LONG,
                 INTEGER, PRIM_INT, SHORT, PRIM_SHORT, BYTE, PRIM_BYTE, BOOLEAN,
                 PRIM_BOOLEAN, DOUBLE, PRIM_DOUBLE, FLOAT, PRIM_FLOAT, CHARACTER, PRIM_CHAR,
                 BYTE_ARRAY, URI, URL, UUID, CURRENCY, LOCALE,
                 INSTANT, LOCAL_DATE, LOCAL_DATETIME, DATE -> true;
            default -> false;
        };
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

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ── Inner records ──────────────────────────────────────────────────

    record FieldTypeResult(
            boolean enumType,
            boolean nestedType,
            String nestedConverterFqn,
            boolean collectionType,
            boolean optionalType,
            String collectionKind,
            boolean mapType,
            String mapKeyTypeName,
            String mapValueTypeName,
            boolean mapValueEnumType,
            boolean mapValueNestedType,
            String mapValueConverterFqn,
            // Composite: Map value is collection
            boolean mapValueCollectionType,
            String mapValueCollectionKind,
            String mapValueElementTypeName,
            boolean mapValueElementEnumType,
            boolean mapValueElementNestedType,
            String mapValueElementConverterFqn,
            // Composite: Map value is map
            boolean mapValueMapType,
            String mapValueMapKeyTypeName,
            String mapValueMapValueTypeName,
            boolean mapValueMapValueEnumType,
            boolean mapValueMapValueNestedType,
            String mapValueMapValueConverterFqn,
            // Element (collection/optional)
            String elementTypeName,
            boolean elementEnumType,
            boolean elementNestedType,
            String elementNestedConverterFqn,
            // Composite: Collection element is collection
            boolean elementCollectionType,
            String elementCollectionKind,
            String elementElementTypeName,
            boolean elementElementEnumType,
            boolean elementElementNestedType,
            String elementElementConverterFqn,
            // Composite: Collection element is map
            boolean elementMapType,
            String elementMapKeyTypeName,
            String elementMapValueTypeName,
            boolean elementMapValueEnumType,
            boolean elementMapValueNestedType,
            String elementMapValueConverterFqn,
            // Polymorphic
            boolean polymorphicType,
            String discriminatorKey,
            List<MetadataFieldInfo.PolymorphicSubtypeInfo> polymorphicSubtypes
    ) {}

    private record MapDetectionResult(
            String keyTypeName,
            String valueTypeName,
            boolean valueEnumType,
            boolean valueNestedType,
            String valueConverterFqn,
            // Composite: value is collection
            boolean valueCollectionType,
            String valueCollectionKind,
            String valueElementTypeName,
            boolean valueElementEnumType,
            boolean valueElementNestedType,
            String valueElementConverterFqn,
            // Composite: value is map
            boolean valueMapType,
            String valueMapKeyTypeName,
            String valueMapValueTypeName,
            boolean valueMapValueEnumType,
            boolean valueMapValueNestedType,
            String valueMapValueConverterFqn
    ) {}

    private record LeafTypeClassification(boolean isEnum, boolean isNested, String converterFqn, boolean isScalar) {}

    private record CompositeElementResult(
            boolean isCollection, boolean isMap, String containerKind,
            String mapKeyTypeName,
            String leafTypeName, boolean leafEnumType, boolean leafNestedType, String leafConverterFqn) {}

    private record ElementDetectionResult(
            boolean isCollectionType, boolean isOptionalType, String collectionKind,
            String elementTypeName, boolean elementEnumType, boolean elementNestedType, String elementNestedConverterFqn,
            boolean elementCollectionType, String elementCollectionKind, String elementElementTypeName,
            boolean elementElementEnumType, boolean elementElementNestedType, String elementElementConverterFqn,
            boolean elementMapType, String elementMapKeyTypeName, String elementMapValueTypeName,
            boolean elementMapValueEnumType, boolean elementMapValueNestedType, String elementMapValueConverterFqn) {

        static final ElementDetectionResult EMPTY = new ElementDetectionResult(
                false, false, null, null, false, false, null,
                false, null, null, false, false, null,
                false, null, null, false, false, null);

        boolean hasRecognizedElement() {
            return elementEnumType || elementNestedType || elementCollectionType || elementMapType;
        }
    }

    private record ElementLeafClassification(
            boolean enumType, boolean nestedType, String nestedConverterFqn,
            boolean compositeCollection, String compositeCollectionKind, String compositeElementTypeName,
            boolean compositeElementEnumType, boolean compositeElementNestedType, String compositeElementConverterFqn,
            boolean compositeMap, String compositeMapKeyTypeName, String compositeMapValueTypeName,
            boolean compositeMapValueEnumType, boolean compositeMapValueNestedType, String compositeMapValueConverterFqn) {

        static final ElementLeafClassification NONE = new ElementLeafClassification(
                false, false, null, false, null, null, false, false, null,
                false, null, null, false, false, null);
    }

    private record MetadataKeyAndEncoding(String metadataKey, MetadataFieldType enc,
                                              boolean required, String defaultValue) {}

    private record AccessorResult(String getterName, String setterName) {}

    private record MetadataSubtypeEntry(String value, TypeMirror typeMirror) {}

    private record PolymorphicDetectionResult(
            String discriminatorKey,
            List<MetadataFieldInfo.PolymorphicSubtypeInfo> subtypes) {}
}
