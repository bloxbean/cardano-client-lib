package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataDiscriminator;
import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataTypeAdapter;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.LeafClassification;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.CollectionCompositeInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.MapCompositeInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.ElementInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.MapTypeInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo.PolymorphicInfo;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Detects Java field types and classifies them for metadata serialization.
 * Handles scalar, enum, nested, collection, map, optional, polymorphic,
 * and composite type detection.
 */
@RequiredArgsConstructor
@SuppressWarnings({
        "java:S1192", // Repeated warning message prefixes are clearer inline than as constants
        "java:S6541"  // detectFieldType is a dispatch method; complexity is inherent to the FieldTypeResult structure
})
class MetadataTypeDetector {

    private static final Set<String> COLLECTION_PREFIXES = Set.of(
            COLLECTION_LIST, COLLECTION_SET, COLLECTION_SORTED_SET);

    private final ProcessingEnvironment processingEnv;
    private final Messager messager;

    // ── Type detection ────────────────────────────────────────────────

    FieldTypeResult detectFieldType(VariableElement ve, String fieldName, String typeName) {
        Element typeEl = processingEnv.getTypeUtils().asElement(ve.asType());
        boolean isEnum = typeEl != null && typeEl.getKind() == ElementKind.ENUM;

        TypeElement fieldTypeElement = (!isEnum && typeEl instanceof TypeElement te) ? te : null;

        // Polymorphic @MetadataDiscriminator detection
        PolymorphicInfo polymorphicInfo = null;
        if (fieldTypeElement != null
                && fieldTypeElement.getAnnotation(MetadataDiscriminator.class) != null) {
            PolymorphicDetectionResult polyResult = detectPolymorphicType(fieldTypeElement, ve, fieldName);
            if (polyResult != null) {
                polymorphicInfo = new PolymorphicInfo(polyResult.discriminatorKey, polyResult.subtypes);
            }
        }

        // Nested @MetadataType detection
        boolean isNestedType = false;
        String nestedConverterFqn = null;
        if (polymorphicInfo == null && fieldTypeElement != null
                && fieldTypeElement.getAnnotation(MetadataType.class) != null) {
            isNestedType = true;
            String fieldPkg = processingEnv.getElementUtils().getPackageOf(fieldTypeElement).toString();
            nestedConverterFqn = fieldPkg + "." + fieldTypeElement.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
        }

        // Map detection
        MapTypeInfo mapTypeInfo = null;

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
            if (mapResult == null) return null;
            mapTypeInfo = mapResult.toMapTypeInfo();
        }

        ElementDetectionResult elemResult = detectElementType(ve, fieldName, mapTypeInfo != null, declaredType, rawType);
        isCollectionType = elemResult.isCollectionType;
        isOptionalType = elemResult.isOptionalType;
        collectionKind = elemResult.collectionKind;

        if (!isEnum && !isNestedType && polymorphicInfo == null && mapTypeInfo == null && !isSupportedType(ve.asType())
                && !elemResult.hasRecognizedElement()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported type '" + typeName + "' and will be skipped.", ve);
            return null;
        }

        return new FieldTypeResult(isEnum, isNestedType, nestedConverterFqn,
                isCollectionType, isOptionalType, collectionKind,
                elemResult.element, mapTypeInfo, polymorphicInfo);
    }

    // ── Element type detection ────────────────────────────────────────

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

        ElementInfo element = classifyElement(elementTypeName, isOptionalType, ve, fieldName);

        return new ElementDetectionResult(isCollectionType, isOptionalType, collectionKind, element);
    }

    private ElementInfo classifyElement(String elementTypeName, boolean isOptionalType,
                                        VariableElement ve, String fieldName) {
        if (!isOptionalType && !isSupportedScalarType(elementTypeName)) {
            CompositeElementResult compositeResult = detectCompositeElement(ve, fieldName);
            if (compositeResult != null && compositeResult.isCollection) {
                LeafClassification leaf = compositeResult.leafClassification();
                return new ElementInfo(elementTypeName, LeafClassification.NONE,
                        new CollectionCompositeInfo(compositeResult.containerKind,
                                compositeResult.leafTypeName, leaf),
                        null);
            }
            if (compositeResult != null && compositeResult.isMap) {
                LeafClassification leaf = compositeResult.leafClassification();
                return new ElementInfo(elementTypeName, LeafClassification.NONE,
                        null,
                        new MapCompositeInfo(compositeResult.mapKeyTypeName,
                                compositeResult.leafTypeName, leaf));
            }
        }

        if (!isSupportedScalarType(elementTypeName)) {
            LeafClassification leafClass = classifyLeafType(elementTypeName);
            if (leafClass.enumType() || leafClass.nestedType()) {
                return new ElementInfo(elementTypeName, leafClass, null, null);
            }
        }

        return new ElementInfo(elementTypeName, LeafClassification.NONE, null, null);
    }

    // ── Map type detection ────────────────────────────────────────────

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

        LeafClassification leafClass = classifyLeafType(innerElementType);
        if (leafClass.isScalar() || leafClass.enumType() || leafClass.nestedType()) {
            return new MapDetectionResult(keyType, valueType, LeafClassification.NONE,
                    new CollectionCompositeInfo(valueRawType, innerElementType, leafClass),
                    null);
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

        LeafClassification leafClass = classifyLeafType(innerValueType);
        if (leafClass.isScalar() || leafClass.enumType() || leafClass.nestedType()) {
            return new MapDetectionResult(keyType, valueType, LeafClassification.NONE,
                    null,
                    new MapCompositeInfo(innerKeyType, innerValueType, leafClass));
        }
        messager.printMessage(Diagnostic.Kind.WARNING,
                "Field '" + fieldName + "' has unsupported inner Map value type '" + innerValueType + "' and will be skipped.", ve);
        return null;
    }

    private MapDetectionResult detectMapScalarValue(String keyType, String valueType,
                                                      TypeMirror valueMirror,
                                                      VariableElement ve, String fieldName) {
        LeafClassification valueLeaf = LeafClassification.NONE;

        Element valEl = processingEnv.getTypeUtils().asElement(valueMirror);
        if (valEl != null && valEl.getKind() == ElementKind.ENUM) {
            valueLeaf = new LeafClassification(true, false, null);
        } else if (valEl instanceof TypeElement valTypeEl
                && valTypeEl.getAnnotation(MetadataType.class) != null) {
            String valPkg = processingEnv.getElementUtils().getPackageOf(valTypeEl).toString();
            String converterFqn = valPkg + "." + valTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            valueLeaf = new LeafClassification(false, true, converterFqn);
        }

        if (!valueLeaf.enumType() && !valueLeaf.nestedType() && !isSupportedScalarType(valueType)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported Map value type '" + valueType + "' and will be skipped.", ve);
            return null;
        }

        return new MapDetectionResult(keyType, valueType, valueLeaf, null, null);
    }

    // ── Composite element detection ───────────────────────────────────

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

        LeafClassification leafClass = classifyLeafType(innerElementType);
        if (leafClass.isScalar() || leafClass.enumType() || leafClass.nestedType()) {
            return new CompositeElementResult(true, false, elementRawType,
                    null, innerElementType, leafClass);
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

        LeafClassification leafClass = classifyLeafType(innerValueType);
        if (leafClass.isScalar() || leafClass.enumType() || leafClass.nestedType()) {
            return new CompositeElementResult(false, true, null,
                    innerKeyType, innerValueType, leafClass);
        }
        return null;
    }

    // ── Polymorphic type detection ────────────────────────────────────

    PolymorphicDetectionResult detectPolymorphicType(TypeElement fieldTypeEl,
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

    private MetadataSubtypeEntry[] extractSubtypeEntries(TypeElement fieldTypeEl) {
        AnnotationMirror discMirror = findAnnotationMirror(fieldTypeEl, MetadataDiscriminator.class);
        if (discMirror == null) return new MetadataSubtypeEntry[0];

        Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues =
                processingEnv.getElementUtils().getElementValuesWithDefaults(discMirror);

        for (var entry : elementValues.entrySet()) {
            if (!"subtypes".equals(entry.getKey().getSimpleName().toString())) continue;

            @SuppressWarnings("unchecked")
            List<? extends AnnotationValue> values = (List<? extends AnnotationValue>) entry.getValue().getValue();
            return values.stream()
                    .map(av -> parseSubtypeEntry((AnnotationMirror) av.getValue()))
                    .filter(Objects::nonNull)
                    .toArray(MetadataSubtypeEntry[]::new);
        }
        return new MetadataSubtypeEntry[0];
    }

    private MetadataSubtypeEntry parseSubtypeEntry(AnnotationMirror subtypeMirror) {
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
        return (value != null && typeMirror != null) ? new MetadataSubtypeEntry(value, typeMirror) : null;
    }

    // ── Adapter detection ─────────────────────────────────────────────

    @SuppressWarnings("java:S3776") // Complexity is inherent to single-pass extraction + validation
    AdapterDetectionResult detectAdapter(VariableElement ve, String fieldName) {
        AnnotationMirror mfMirror = findAnnotationMirror(ve, MetadataField.class);
        if (mfMirror == null) return null;

        Map<? extends ExecutableElement, ? extends AnnotationValue> values =
                processingEnv.getElementUtils().getElementValuesWithDefaults(mfMirror);

        TypeMirror adapterMirror = null;
        String key = "";
        MetadataFieldType enc = MetadataFieldType.DEFAULT;
        boolean required = false;
        String defaultValue = "";
        for (var entry : values.entrySet()) {
            switch (entry.getKey().getSimpleName().toString()) {
                case "adapter" -> adapterMirror = (TypeMirror) entry.getValue().getValue();
                case "key" -> key = (String) entry.getValue().getValue();
                case "enc" -> {
                    VariableElement encEl = (VariableElement) entry.getValue().getValue();
                    enc = MetadataFieldType.valueOf(encEl.getSimpleName().toString());
                }
                case "required" -> required = (Boolean) entry.getValue().getValue();
                case "defaultValue" -> defaultValue = (String) entry.getValue().getValue();
                default -> { /* ignore other attributes */ }
            }
        }
        if (adapterMirror == null) return null;

        TypeElement noAdapterEl = processingEnv.getElementUtils()
                .getTypeElement(MetadataField.NoAdapter.class.getCanonicalName());
        if (noAdapterEl != null && processingEnv.getTypeUtils().isSameType(adapterMirror, noAdapterEl.asType())) {
            return null;
        }

        String adapterFqn = adapterMirror.toString();
        TypeElement adapterTypeEl = (TypeElement) processingEnv.getTypeUtils().asElement(adapterMirror);
        if (adapterTypeEl == null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': cannot resolve adapter class '" + adapterFqn + "'.", ve);
            return null;
        }

        TypeElement adapterInterfaceEl = processingEnv.getElementUtils()
                .getTypeElement(MetadataTypeAdapter.class.getCanonicalName());
        if (adapterInterfaceEl == null) {
            return null;
        }

        TypeMirror erasedAdapter = processingEnv.getTypeUtils().erasure(adapterMirror);
        TypeMirror erasedInterface = processingEnv.getTypeUtils().erasure(adapterInterfaceEl.asType());
        if (!processingEnv.getTypeUtils().isAssignable(erasedAdapter, erasedInterface)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': adapter '" + adapterFqn
                            + "' must implement MetadataTypeAdapter.", ve);
            return null;
        }

        if (!defaultValue.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': 'adapter' and 'defaultValue' are mutually exclusive.", ve);
            return null;
        }

        if (enc != MetadataFieldType.DEFAULT) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': @MetadataField(enc=...) is ignored when an adapter is specified.", ve);
        }

        String metadataKey = key.isEmpty() ? fieldName : key;
        return new AdapterDetectionResult(adapterFqn,
                new MetadataFieldValidator.MetadataKeyAndEncoding(metadataKey, MetadataFieldType.DEFAULT, required, null));
    }

    // ── Type validation helpers ───────────────────────────────────────

    boolean isSupportedType(TypeMirror typeMirror) {
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

    boolean isSupportedScalarType(String typeName) {
        return switch (typeName) {
            case STRING, BIG_INTEGER, BIG_DECIMAL, LONG, PRIM_LONG,
                 INTEGER, PRIM_INT, SHORT, PRIM_SHORT, BYTE, PRIM_BYTE, BOOLEAN,
                 PRIM_BOOLEAN, DOUBLE, PRIM_DOUBLE, FLOAT, PRIM_FLOAT, CHARACTER, PRIM_CHAR,
                 BYTE_ARRAY, URI, URL, UUID, CURRENCY, LOCALE,
                 INSTANT, LOCAL_DATE, LOCAL_DATETIME, DATE, DURATION -> true;
            default -> false;
        };
    }

    boolean isAllowedMapKeyType(String keyType) {
        return switch (keyType) {
            case STRING, INTEGER, PRIM_INT, LONG, PRIM_LONG, BIG_INTEGER, BYTE_ARRAY -> true;
            default -> false;
        };
    }

    // ── Utility helpers ───────────────────────────────────────────────

    AnnotationMirror findAnnotationMirror(Element element, Class<?> annotationClass) {
        TypeMirror targetType = processingEnv.getElementUtils()
                .getTypeElement(annotationClass.getCanonicalName()).asType();
        for (AnnotationMirror am : element.getAnnotationMirrors()) {
            if (processingEnv.getTypeUtils().isSameType(am.getAnnotationType(), targetType)) {
                return am;
            }
        }
        return null;
    }

    String rawTypeName(DeclaredType declaredType) {
        return ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
    }

    LeafClassification classifyLeafType(String typeName) {
        if (isSupportedScalarType(typeName)) {
            return LeafClassification.SCALAR;
        }
        TypeElement te = processingEnv.getElementUtils().getTypeElement(typeName);
        if (te != null && te.getKind() == ElementKind.ENUM) {
            return new LeafClassification(true, false, null);
        }
        if (te != null && te.getAnnotation(MetadataType.class) != null) {
            String pkg = processingEnv.getElementUtils().getPackageOf(te).toString();
            String fqn = pkg + "." + te.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            return new LeafClassification(false, true, fqn);
        }
        return LeafClassification.NONE;
    }

    private boolean isDoubleNested(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declared) {
            String raw = rawTypeName(declared);
            return COLLECTION_PREFIXES.contains(raw) || MAP.equals(raw);
        }
        return false;
    }

    // ── Inner records ─────────────────────────────────────────────────

    record FieldTypeResult(
            boolean enumType, boolean nestedType, String nestedConverterFqn,
            boolean collectionType, boolean optionalType, String collectionKind,
            ElementInfo element, MapTypeInfo mapType, PolymorphicInfo polymorphic
    ) {
        boolean isMapType() { return mapType != null; }
        boolean isPolymorphicType() { return polymorphic != null; }

        /** Convenience: element type name (for backwards compat in extractors). */
        String elementTypeName() { return element != null ? element.typeName() : null; }
    }

    private record MapDetectionResult(
            String keyTypeName, String valueTypeName,
            LeafClassification valueLeaf,
            CollectionCompositeInfo valueCollection,  // nullable
            MapCompositeInfo valueMap                  // nullable
    ) {
        MapTypeInfo toMapTypeInfo() {
            return new MapTypeInfo(keyTypeName, valueTypeName, valueLeaf, valueCollection, valueMap);
        }
    }

    private record CompositeElementResult(
            boolean isCollection, boolean isMap, String containerKind,
            String mapKeyTypeName,
            String leafTypeName, LeafClassification leafClassification) {}

    private record ElementDetectionResult(
            boolean isCollectionType, boolean isOptionalType, String collectionKind,
            ElementInfo element) {

        static final ElementDetectionResult EMPTY = new ElementDetectionResult(
                false, false, null, ElementInfo.NONE);

        boolean hasRecognizedElement() {
            return element.leaf().enumType() || element.leaf().nestedType()
                    || element.hasCompositeCollection() || element.hasCompositeMap();
        }
    }

    record AdapterDetectionResult(String adapterFqn, MetadataFieldValidator.MetadataKeyAndEncoding keyEnc) {}

    record MetadataSubtypeEntry(String value, TypeMirror typeMirror) {}

    record PolymorphicDetectionResult(
            String discriminatorKey,
            List<MetadataFieldInfo.PolymorphicSubtypeInfo> subtypes) {}
}
