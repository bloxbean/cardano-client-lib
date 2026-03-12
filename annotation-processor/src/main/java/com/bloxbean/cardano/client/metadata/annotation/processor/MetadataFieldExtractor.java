package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;

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
public class MetadataFieldExtractor {

    private final ProcessingEnvironment processingEnv;
    private final Messager messager;

    MetadataFieldExtractor(ProcessingEnvironment processingEnv, Messager messager) {
        this.processingEnv = processingEnv;
        this.messager = messager;
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Extracts fields from the type and all its superclasses (inheritance).
     * Fields are collected bottom-up; shadowed names (child overrides parent) are skipped.
     */
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
            if (current.getQualifiedName().toString().equals("java.lang.Object")) break;
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
                    typeResult.elementTypeName != null, typeResult.mapType, typeResult.nestedType);
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

        // Nested @MetadataType detection
        boolean isNestedType = false;
        String nestedConverterFqn = null;
        if (!isEnum && typeEl instanceof TypeElement fieldTypeEl) {
            if (fieldTypeEl.getAnnotation(MetadataType.class) != null) {
                isNestedType = true;
                String fieldPkg = processingEnv.getElementUtils().getPackageOf(fieldTypeEl).toString();
                nestedConverterFqn = fieldPkg + "." + fieldTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            }
        }

        // Map<String, V> detection
        boolean isMapType = false;
        String mapKeyTypeName = null;
        String mapValueTypeName = null;
        boolean mapValueEnumType = false;
        boolean mapValueNestedType = false;
        String mapValueConverterFqn = null;

        if (typeName.startsWith("java.util.Map<") && typeName.endsWith(">")) {
            MapDetectionResult mapResult = detectMapType(ve, fieldName);
            if (mapResult == null) return null; // error or unsupported, already reported
            isMapType = true;
            mapKeyTypeName = mapResult.keyTypeName;
            mapValueTypeName = mapResult.valueTypeName;
            mapValueEnumType = mapResult.valueEnumType;
            mapValueNestedType = mapResult.valueNestedType;
            mapValueConverterFqn = mapResult.valueConverterFqn;
        }

        // Collection/Optional element type
        String elementTypeName = null;
        if (!isMapType && (typeName.startsWith("java.util.List<") || typeName.startsWith("java.util.Set<")
                || typeName.startsWith("java.util.SortedSet<")
                || typeName.startsWith("java.util.Optional<"))
                && typeName.endsWith(">")) {
            elementTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
        }

        // Detect enum/nested element type for collection/Optional
        boolean elementEnumType = false;
        boolean elementNestedType = false;
        String elementNestedConverterFqn = null;
        if (elementTypeName != null && !isSupportedScalarType(elementTypeName)) {
            TypeElement elTypeEl = processingEnv.getElementUtils().getTypeElement(elementTypeName);
            if (elTypeEl != null && elTypeEl.getKind() == ElementKind.ENUM) {
                elementEnumType = true;
            } else if (elTypeEl != null && elTypeEl.getAnnotation(MetadataType.class) != null) {
                elementNestedType = true;
                String elPkg = processingEnv.getElementUtils().getPackageOf(elTypeEl).toString();
                elementNestedConverterFqn = elPkg + "." + elTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            }
        }

        // Validate supported type
        if (!isEnum && !isNestedType && !isMapType && !isSupportedType(typeName) && !elementEnumType && !elementNestedType) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported type '" + typeName + "' and will be skipped.", ve);
            return null;
        }

        return new FieldTypeResult(isEnum, isNestedType, nestedConverterFqn,
                isMapType, mapKeyTypeName, mapValueTypeName, mapValueEnumType, mapValueNestedType, mapValueConverterFqn,
                elementTypeName, elementEnumType, elementNestedType, elementNestedConverterFqn);
    }

    private MapDetectionResult detectMapType(VariableElement ve, String fieldName) {
        TypeMirror fieldTypeMirror = ve.asType();
        if (!(fieldTypeMirror instanceof DeclaredType declaredType)) return null;

        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs.size() != 2) return null;

        String keyType = typeArgs.get(0).toString();
        String valueType = typeArgs.get(1).toString();

        if (!"java.lang.String".equals(keyType)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Field '" + fieldName + "': Map key type must be java.lang.String, but found '" + keyType + "'.", ve);
            return null;
        }

        boolean valueEnumType = false;
        boolean valueNestedType = false;
        String valueConverterFqn = null;

        Element valEl = processingEnv.getTypeUtils().asElement(typeArgs.get(1));
        if (valEl != null && valEl.getKind() == ElementKind.ENUM) {
            valueEnumType = true;
        } else if (valEl instanceof TypeElement valTypeEl) {
            if (valTypeEl.getAnnotation(MetadataType.class) != null) {
                valueNestedType = true;
                String valPkg = processingEnv.getElementUtils().getPackageOf(valTypeEl).toString();
                valueConverterFqn = valPkg + "." + valTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
            }
        }

        if (!valueEnumType && !valueNestedType && !isSupportedScalarType(valueType)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "' has unsupported Map value type '" + valueType + "' and will be skipped.", ve);
            return null;
        }

        return new MapDetectionResult(keyType, valueType, valueEnumType, valueNestedType, valueConverterFqn);
    }

    // ── Phase 3: Metadata key and encoding ─────────────────────────────

    private MetadataKeyAndEncoding resolveMetadataKeyAndEncoding(VariableElement ve, String fieldName, String typeName,
                                                                  boolean hasElementType, boolean isMapType, boolean isNestedType) {
        String metadataKey = fieldName;
        MetadataFieldType enc = MetadataFieldType.DEFAULT;
        MetadataField mf = ve.getAnnotation(MetadataField.class);
        if (mf != null) {
            if (!mf.key().isEmpty()) {
                metadataKey = mf.key();
            }
            enc = mf.enc();
        }

        // Warn and reset enc= on collection/Optional/Map/nested fields
        if ((hasElementType || isMapType || isNestedType) && enc != MetadataFieldType.DEFAULT) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "Field '" + fieldName + "': @MetadataField(enc=...) is not supported on this field type; using DEFAULT.", ve);
            enc = MetadataFieldType.DEFAULT;
        }

        if (!isNestedType && !isMapType && !isValidEnc(typeName, enc, ve)) {
            return null;
        }

        return new MetadataKeyAndEncoding(metadataKey, enc);
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
                .getterName(accessors.getterName)
                .setterName(accessors.setterName)
                .elementTypeName(type.elementTypeName)
                .enumType(type.enumType)
                .elementEnumType(type.elementEnumType)
                .nestedType(type.nestedType)
                .elementNestedType(type.elementNestedType)
                .nestedConverterFqn(type.nestedType ? type.nestedConverterFqn : type.elementNestedConverterFqn)
                .mapType(type.mapType)
                .mapKeyTypeName(type.mapKeyTypeName)
                .mapValueTypeName(type.mapValueTypeName)
                .mapValueEnumType(type.mapValueEnumType)
                .mapValueNestedType(type.mapValueNestedType)
                .mapValueConverterFqn(type.mapValueConverterFqn)
                .build();
    }

    // ── Accessor helpers ───────────────────────────────────────────────

    private ExecutableElement findGetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String getterMethodName = "get" + capitalize(fieldName);
        String isGetterMethodName = "is" + capitalize(fieldName);
        String fieldTypeName = variableElement.asType().toString();
        boolean isBooleanType = fieldTypeName.equals("boolean") || fieldTypeName.equals("java.lang.Boolean");

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

    private boolean isDirectlyAccessible(VariableElement ve) {
        return ve.getModifiers().contains(Modifier.PUBLIC) || ve.getModifiers().isEmpty();
    }

    private boolean isSupportedType(String typeName) {
        if (isSupportedScalarType(typeName)) return true;
        if ((typeName.startsWith("java.util.List<") || typeName.startsWith("java.util.Set<"))
                && typeName.endsWith(">")) {
            String elementType = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
            return isSupportedScalarType(elementType);
        }
        if (typeName.startsWith("java.util.SortedSet<") && typeName.endsWith(">")) {
            String elementType = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
            return isSupportedScalarType(elementType)
                    && !"byte[]".equals(elementType)
                    && !"java.net.URL".equals(elementType)
                    && !"java.util.Currency".equals(elementType)
                    && !"java.util.Locale".equals(elementType);
        }
        if (typeName.startsWith("java.util.Optional<") && typeName.endsWith(">")) {
            String elementType = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
            return isSupportedScalarType(elementType);
        }
        return false;
    }

    private boolean isSupportedScalarType(String typeName) {
        return switch (typeName) {
            case "java.lang.String", "java.math.BigInteger", "java.math.BigDecimal", "java.lang.Long", "long",
                 "java.lang.Integer", "int", "java.lang.Short", "short", "java.lang.Byte", "byte", "java.lang.Boolean",
                 "boolean", "java.lang.Double", "double", "java.lang.Float", "float", "java.lang.Character", "char",
                 "byte[]", "java.net.URI", "java.net.URL", "java.util.UUID", "java.util.Currency", "java.util.Locale",
                 "java.time.Instant", "java.time.LocalDate", "java.time.LocalDateTime", "java.util.Date" -> true;
            default -> false;
        };
    }

    private boolean isValidEnc(String typeName, MetadataFieldType enc, VariableElement ve) {
        return switch (enc) {
            case DEFAULT -> true;
            case STRING -> {
                if (typeName.equals("byte[]")) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "@MetadataField(enc=STRING) is ambiguous for byte[] — " +
                            "use STRING_HEX or STRING_BASE64 to specify the encoding.", ve);
                    yield false;
                }
                yield true;
            }
            case STRING_HEX, STRING_BASE64 -> {
                if (!typeName.equals("byte[]")) {
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
            boolean mapType,
            String mapKeyTypeName,
            String mapValueTypeName,
            boolean mapValueEnumType,
            boolean mapValueNestedType,
            String mapValueConverterFqn,
            String elementTypeName,
            boolean elementEnumType,
            boolean elementNestedType,
            String elementNestedConverterFqn
    ) {}

    private record MapDetectionResult(
            String keyTypeName,
            String valueTypeName,
            boolean valueEnumType,
            boolean valueNestedType,
            String valueConverterFqn
    ) {}

    private record MetadataKeyAndEncoding(String metadataKey, MetadataFieldType enc) {}

    private record AccessorResult(String getterName, String setterName) {}
}
