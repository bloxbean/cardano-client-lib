package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Extracts {@link MetadataFieldInfo} from {@code @MetadataType}-annotated classes.
 * Orchestrates type detection, validation, and accessor resolution by delegating
 * to {@link MetadataTypeDetector}, {@link MetadataFieldValidator}, and
 * {@link MetadataAccessorResolver}.
 */
public class MetadataFieldExtractor {

    private final ProcessingEnvironment processingEnv;
    private final Messager messager;
    private final MetadataTypeDetector typeDetector;
    private final MetadataFieldValidator validator;
    private final MetadataAccessorResolver accessorResolver;

    MetadataFieldExtractor(ProcessingEnvironment processingEnv, Messager messager) {
        this.processingEnv = processingEnv;
        this.messager = messager;
        this.typeDetector = new MetadataTypeDetector(processingEnv, messager);
        this.validator = new MetadataFieldValidator(messager);
        this.accessorResolver = new MetadataAccessorResolver(processingEnv, messager);
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

        Map<String, VariableElement> fieldElements = buildFieldElementMap(typeElement);

        for (RecordComponentElement component : typeElement.getRecordComponents()) {
            String fieldName = component.getSimpleName().toString();
            String typeName = component.asType().toString();
            allComponents.add(new MetadataConverterGenerator.RecordComponentInfo(fieldName, typeName));

            VariableElement ve = fieldElements.get(fieldName);
            if (ve == null || shouldSkipField(ve, seenFieldNames)) continue;

            MetadataFieldInfo info = extractRecordComponent(ve, fieldName, typeName);
            if (info != null) fields.add(info);
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
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "No public no-arg constructor found on '" + typeElement.getSimpleName() + "'. " +
                    "The generated fromMetadataMap() calls new " + typeElement.getSimpleName() + "(). " +
                    "Add a public no-arg constructor or use @lombok.NoArgsConstructor.", typeElement);
        }
    }

    // ── Per-class field extraction ──────────────────────────────────────

    private List<MetadataFieldInfo> extractFieldsForType(TypeElement typeElement, TypeElement leafTypeElement,
                                                          boolean hasLombok, Set<String> seenFieldNames) {
        List<MetadataFieldInfo> fields = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (!(enclosed instanceof VariableElement ve)) continue;
            if (shouldSkipField(ve, seenFieldNames)) continue;

            MetadataFieldInfo info = extractClassField(ve, leafTypeElement, hasLombok);
            if (info != null) fields.add(info);
        }

        return fields;
    }

    // ── Single-field extraction ─────────────────────────────────────────

    private MetadataFieldInfo extractRecordComponent(VariableElement ve, String fieldName, String typeName) {
        MetadataAccessorResolver.AccessorResult recordAccessor = new MetadataAccessorResolver.AccessorResult(fieldName, null);

        MetadataTypeDetector.AdapterDetectionResult adapterResult = typeDetector.detectAdapter(ve, fieldName);
        if (adapterResult != null) {
            MetadataFieldInfo info = buildAdapterFieldInfo(fieldName, typeName, adapterResult.keyEnc(),
                    recordAccessor, adapterResult.adapterFqn());
            info.setRecordMode(true);
            return info;
        }

        MetadataTypeDetector.FieldTypeResult typeResult = typeDetector.detectFieldType(ve, fieldName, typeName);
        if (typeResult == null) return null;

        MetadataFieldValidator.MetadataKeyAndEncoding keyEnc = validator.resolveMetadataKeyAndEncoding(ve, fieldName, typeName,
                typeResult.elementTypeName() != null, typeResult.mapType(),
                typeResult.nestedType() || typeResult.polymorphicType(),
                typeResult.elementTypeName());
        if (keyEnc == null) return null;

        MetadataFieldInfo info = buildFieldInfo(fieldName, typeName, keyEnc, recordAccessor, typeResult);
        info.setRecordMode(true);
        return info;
    }

    private MetadataFieldInfo extractClassField(VariableElement ve, TypeElement leafTypeElement, boolean hasLombok) {
        String fieldName = ve.getSimpleName().toString();
        String typeName = ve.asType().toString();

        MetadataTypeDetector.AdapterDetectionResult adapterResult = typeDetector.detectAdapter(ve, fieldName);
        if (adapterResult != null) {
            MetadataAccessorResolver.AccessorResult accessors = accessorResolver.resolveAccessors(leafTypeElement, ve, fieldName, hasLombok);
            return accessors != null
                    ? buildAdapterFieldInfo(fieldName, typeName, adapterResult.keyEnc(), accessors, adapterResult.adapterFqn())
                    : null;
        }

        MetadataTypeDetector.FieldTypeResult typeResult = typeDetector.detectFieldType(ve, fieldName, typeName);
        if (typeResult == null) return null;

        MetadataFieldValidator.MetadataKeyAndEncoding keyEnc = validator.resolveMetadataKeyAndEncoding(ve, fieldName, typeName,
                typeResult.elementTypeName() != null, typeResult.mapType(),
                typeResult.nestedType() || typeResult.polymorphicType(),
                typeResult.elementTypeName());
        if (keyEnc == null) return null;

        MetadataAccessorResolver.AccessorResult accessors = accessorResolver.resolveAccessors(leafTypeElement, ve, fieldName, hasLombok);
        if (accessors == null) return null;

        return buildFieldInfo(fieldName, typeName, keyEnc, accessors, typeResult);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private Map<String, VariableElement> buildFieldElementMap(TypeElement typeElement) {
        Map<String, VariableElement> fieldElements = new LinkedHashMap<>();
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof VariableElement ve && enclosed.getKind() == ElementKind.FIELD) {
                fieldElements.put(ve.getSimpleName().toString(), ve);
            }
        }
        return fieldElements;
    }

    private boolean shouldSkipField(VariableElement ve, Set<String> seenFieldNames) {
        if (ve.getModifiers().contains(Modifier.STATIC)) return true;
        if (ve.getAnnotation(MetadataIgnore.class) != null) return true;
        String fieldName = ve.getSimpleName().toString();
        return !seenFieldNames.add(fieldName);
    }

    // ── MetadataFieldInfo builders ──────────────────────────────────────

    private MetadataFieldInfo buildFieldInfo(String fieldName, String typeName,
                                              MetadataFieldValidator.MetadataKeyAndEncoding keyEnc,
                                              MetadataAccessorResolver.AccessorResult accessors,
                                              MetadataTypeDetector.FieldTypeResult type) {
        return MetadataFieldInfo.builder()
                .javaFieldName(fieldName)
                .metadataKey(keyEnc.metadataKey())
                .javaTypeName(typeName)
                .enc(keyEnc.enc())
                .required(keyEnc.required())
                .defaultValue(keyEnc.defaultValue())
                .getterName(accessors.getterName())
                .setterName(accessors.setterName())
                .elementTypeName(type.elementTypeName())
                .enumType(type.enumType())
                .elementEnumType(type.elementEnumType())
                .nestedType(type.nestedType())
                .collectionType(type.collectionType())
                .optionalType(type.optionalType())
                .collectionKind(type.collectionKind())
                .elementNestedType(type.elementNestedType())
                .nestedConverterFqn(type.nestedType() ? type.nestedConverterFqn() : type.elementNestedConverterFqn())
                .mapType(type.mapType())
                .mapKeyTypeName(type.mapKeyTypeName())
                .mapValueTypeName(type.mapValueTypeName())
                .mapValueEnumType(type.mapValueEnumType())
                .mapValueNestedType(type.mapValueNestedType())
                .mapValueConverterFqn(type.mapValueConverterFqn())
                .mapValueCollectionType(type.mapValueCollectionType())
                .mapValueCollectionKind(type.mapValueCollectionKind())
                .mapValueElementTypeName(type.mapValueElementTypeName())
                .mapValueElementEnumType(type.mapValueElementEnumType())
                .mapValueElementNestedType(type.mapValueElementNestedType())
                .mapValueElementConverterFqn(type.mapValueElementConverterFqn())
                .mapValueMapType(type.mapValueMapType())
                .mapValueMapKeyTypeName(type.mapValueMapKeyTypeName())
                .mapValueMapValueTypeName(type.mapValueMapValueTypeName())
                .mapValueMapValueEnumType(type.mapValueMapValueEnumType())
                .mapValueMapValueNestedType(type.mapValueMapValueNestedType())
                .mapValueMapValueConverterFqn(type.mapValueMapValueConverterFqn())
                .elementCollectionType(type.elementCollectionType())
                .elementCollectionKind(type.elementCollectionKind())
                .elementElementTypeName(type.elementElementTypeName())
                .elementElementEnumType(type.elementElementEnumType())
                .elementElementNestedType(type.elementElementNestedType())
                .elementElementConverterFqn(type.elementElementConverterFqn())
                .elementMapType(type.elementMapType())
                .elementMapKeyTypeName(type.elementMapKeyTypeName())
                .elementMapValueTypeName(type.elementMapValueTypeName())
                .elementMapValueEnumType(type.elementMapValueEnumType())
                .elementMapValueNestedType(type.elementMapValueNestedType())
                .elementMapValueConverterFqn(type.elementMapValueConverterFqn())
                .polymorphicType(type.polymorphicType())
                .discriminatorKey(type.discriminatorKey())
                .subtypes(type.polymorphicSubtypes() != null ? type.polymorphicSubtypes() : List.of())
                .build();
    }

    private MetadataFieldInfo buildAdapterFieldInfo(String fieldName, String typeName,
                                                     MetadataFieldValidator.MetadataKeyAndEncoding keyEnc,
                                                     MetadataAccessorResolver.AccessorResult accessors,
                                                     String adapterFqn) {
        return MetadataFieldInfo.builder()
                .javaFieldName(fieldName)
                .metadataKey(keyEnc.metadataKey())
                .javaTypeName(typeName)
                .enc(keyEnc.enc())
                .required(keyEnc.required())
                .defaultValue(keyEnc.defaultValue())
                .getterName(accessors.getterName())
                .setterName(accessors.setterName())
                .adapterType(true)
                .adapterFqn(adapterFqn)
                .build();
    }
}
