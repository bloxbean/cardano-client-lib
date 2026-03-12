package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataField;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.bloxbean.cardano.client.metadata.annotation.MetadataIgnore;
import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * Annotation processor for {@code @MetadataType} annotated classes.
 * Generates a {@code {ClassName}MetadataConverter} with {@code toMetadataMap}
 * and {@code fromMetadataMap} methods for Cardano metadata serialization.
 */
@AutoService(Processor.class)
@Slf4j
public class MetadataAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private MetadataConverterGenerator generator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        generator = new MetadataConverterGenerator();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(MetadataType.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (!(element instanceof TypeElement typeElement)) continue;

                String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).toString();
                String className = typeElement.getSimpleName().toString();

                boolean hasLombok = detectLombok(typeElement);
                validateNoArgConstructor(typeElement, hasLombok);
                List<MetadataFieldInfo> fields = extractFields(typeElement, hasLombok);

                // Read label from @MetadataType annotation
                MetadataType metadataTypeAnn = typeElement.getAnnotation(MetadataType.class);
                long label = metadataTypeAnn.label();

                try {
                    TypeSpec typeSpec = generator.generate(packageName, className, fields, label);
                    String converterName = className + MetadataConverterGenerator.CONVERTER_SUFFIX;
                    JavaFileUtil.createJavaFile(packageName, typeSpec, converterName, processingEnv);
                } catch (Exception e) {
                    log.error("Failed to generate MetadataConverter for " + className, e);
                    error(typeElement, "Failed to generate MetadataConverter for " + className + ": " + e.getMessage());
                }
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean detectLombok(TypeElement typeElement) {
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

    private void validateNoArgConstructor(TypeElement typeElement, boolean hasLombok) {
        for (Element e : typeElement.getEnclosedElements()) {
            if (!(e instanceof ExecutableElement executableElement)) continue;
            if (executableElement.getKind() == ElementKind.CONSTRUCTOR
                    && executableElement.getParameters().isEmpty()
                    && executableElement.getModifiers().contains(Modifier.PUBLIC)) {
                return; // explicit public no-arg constructor found
            }
        }
        if (!hasLombok) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No public no-arg constructor found on '" + typeElement.getSimpleName() + "'. " +
                    "The generated fromMetadataMap() calls new " + typeElement.getSimpleName() + "(). " +
                    "Add a public no-arg constructor or use @lombok.NoArgsConstructor.", typeElement);
        }
        // If hasLombok → suppress: @Data / @NoArgsConstructor will generate one at compile time.
    }

    /**
     * Extracts fields from the type and all its superclasses (Feature 4: Inheritance).
     * Fields are collected bottom-up; shadowed names (child overrides parent) are skipped.
     */
    private List<MetadataFieldInfo> extractFields(TypeElement typeElement, boolean hasLombok) {
        List<MetadataFieldInfo> fields = new ArrayList<>();
        Set<String> seenFieldNames = new LinkedHashSet<>();

        TypeElement current = typeElement;
        while (current != null) {
            List<MetadataFieldInfo> fieldsForType = extractFieldsForType(current, typeElement, hasLombok, seenFieldNames);
            fields.addAll(fieldsForType);

            // Walk up to superclass
            TypeMirror superclass = current.getSuperclass();
            if (superclass.getKind() == TypeKind.NONE) break;
            Element superEl = processingEnv.getTypeUtils().asElement(superclass);
            if (!(superEl instanceof TypeElement)) break;
            current = (TypeElement) superEl;
            // Stop at java.lang.Object
            if (current.getQualifiedName().toString().equals("java.lang.Object")) break;
        }

        return fields;
    }

    /**
     * Extracts fields for a single class in the hierarchy.
     * Uses {@code leafTypeElement} for getter/setter resolution so inherited accessors are found.
     */
    private List<MetadataFieldInfo> extractFieldsForType(TypeElement typeElement, TypeElement leafTypeElement,
                                                          boolean hasLombok, Set<String> seenFieldNames) {
        List<MetadataFieldInfo> fields = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (!(enclosed instanceof VariableElement ve)) continue;

            // Skip static fields
            if (ve.getModifiers().contains(Modifier.STATIC)) continue;

            // Skip @MetadataIgnore fields
            if (ve.getAnnotation(MetadataIgnore.class) != null) continue;

            String fieldName = ve.getSimpleName().toString();

            // Skip shadowed fields (child already declared same name)
            if (!seenFieldNames.add(fieldName)) continue;

            String typeName = ve.asType().toString();

            // Enum detection
            Element typeEl = processingEnv.getTypeUtils().asElement(ve.asType());
            boolean isEnum = typeEl != null && typeEl.getKind() == ElementKind.ENUM;

            // Nested @MetadataType detection (Feature 1)
            boolean isNestedType = false;
            String nestedConverterFqn = null;
            if (!isEnum && typeEl instanceof TypeElement fieldTypeEl) {

                if (fieldTypeEl.getAnnotation(MetadataType.class) != null) {
                    isNestedType = true;
                    String fieldPkg = processingEnv.getElementUtils().getPackageOf(fieldTypeEl).toString();
                    nestedConverterFqn = fieldPkg + "." + fieldTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
                }
            }

            // Map<String, V> detection (Feature 2)
            boolean isMapType = false;
            String mapKeyTypeName = null;
            String mapValueTypeName = null;
            boolean mapValueEnumType = false;
            boolean mapValueNestedType = false;
            String mapValueConverterFqn = null;

            if (typeName.startsWith("java.util.Map<") && typeName.endsWith(">")) {
                TypeMirror fieldTypeMirror = ve.asType();
                if (fieldTypeMirror instanceof DeclaredType declaredType) {
                    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
                    if (typeArgs.size() == 2) {
                        String keyType = typeArgs.get(0).toString();
                        String valueType = typeArgs.get(1).toString();
                        if ("java.lang.String".equals(keyType)) {
                            isMapType = true;
                            mapKeyTypeName = keyType;
                            mapValueTypeName = valueType;

                            // Check value type
                            Element valEl = processingEnv.getTypeUtils().asElement(typeArgs.get(1));
                            if (valEl != null && valEl.getKind() == ElementKind.ENUM) {
                                mapValueEnumType = true;
                            } else if (valEl instanceof TypeElement) {
                                TypeElement valTypeEl = (TypeElement) valEl;
                                if (valTypeEl.getAnnotation(MetadataType.class) != null) {
                                    mapValueNestedType = true;
                                    String valPkg = processingEnv.getElementUtils().getPackageOf(valTypeEl).toString();
                                    mapValueConverterFqn = valPkg + "." + valTypeEl.getSimpleName() + MetadataConverterGenerator.CONVERTER_SUFFIX;
                                }
                            }

                            if (!mapValueEnumType && !mapValueNestedType && !isSupportedScalarType(mapValueTypeName)) {
                                messager.printMessage(Diagnostic.Kind.WARNING,
                                        "Field '" + fieldName + "' has unsupported Map value type '" + mapValueTypeName + "' and will be skipped.", ve);
                                continue;
                            }
                        } else {
                            messager.printMessage(Diagnostic.Kind.ERROR,
                                    "Field '" + fieldName + "': Map key type must be java.lang.String, but found '" + keyType + "'.", ve);
                            continue;
                        }
                    }
                }
            }

            // Extract element type for collections/Optional
            String elementTypeName = null;
            if (!isMapType && (typeName.startsWith("java.util.List<") || typeName.startsWith("java.util.Set<")
                    || typeName.startsWith("java.util.SortedSet<")
                    || typeName.startsWith("java.util.Optional<"))
                    && typeName.endsWith(">")) {
                elementTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
            }

            // Detect enum element type for collection / Optional fields
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

            if (!isEnum && !isNestedType && !isMapType && !isSupportedType(typeName) && !elementEnumType && !elementNestedType) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "Field '" + fieldName + "' has unsupported type '" + typeName + "' and will be skipped.", ve);
                continue;
            }

            // Determine metadata key and output type
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
            if ((elementTypeName != null || isMapType || isNestedType) && enc != MetadataFieldType.DEFAULT) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "Field '" + fieldName + "': @MetadataField(enc=...) is not supported on this field type; using DEFAULT.", ve);
                enc = MetadataFieldType.DEFAULT;
            }

            if (!isNestedType && !isMapType && !isValidEnc(typeName, enc, ve)) {
                continue;
            }

            // Detect getter — use getAllMembers so inherited methods are found
            String getterName = null;
            ExecutableElement getter = findGetter(leafTypeElement, ve);
            if (getter != null) {
                getterName = getter.getSimpleName().toString();
            } else if (hasLombok) {
                getterName = "get" + capitalize(fieldName);
            } else if (isNotDirectlyAccessible(ve)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "No getter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
                continue;
            }

            // Detect setter — use getAllMembers so inherited methods are found
            String setterName = null;
            ExecutableElement setter = findSetter(leafTypeElement, ve);
            if (setter != null) {
                setterName = setter.getSimpleName().toString();
            } else if (hasLombok) {
                setterName = "set" + capitalize(fieldName);
            } else if (isNotDirectlyAccessible(ve)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "No setter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
                continue;
            }

            MetadataFieldInfo info = new MetadataFieldInfo();
            info.setJavaFieldName(fieldName);
            info.setMetadataKey(metadataKey);
            info.setJavaTypeName(typeName);
            info.setEnc(enc);
            info.setGetterName(getterName);
            info.setSetterName(setterName);
            info.setElementTypeName(elementTypeName);
            info.setEnumType(isEnum);
            info.setElementEnumType(elementEnumType);
            info.setNestedType(isNestedType);
            info.setElementNestedType(elementNestedType);
            info.setNestedConverterFqn(isNestedType ? nestedConverterFqn : elementNestedConverterFqn);
            info.setMapType(isMapType);
            info.setMapKeyTypeName(mapKeyTypeName);
            info.setMapValueTypeName(mapValueTypeName);
            info.setMapValueEnumType(mapValueEnumType);
            info.setMapValueNestedType(mapValueNestedType);
            info.setMapValueConverterFqn(mapValueConverterFqn);

            fields.add(info);
        }

        return fields;
    }

    private boolean isNotDirectlyAccessible(VariableElement ve) {
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
            // These types do not implement Comparable — TreeSet would throw ClassCastException at runtime

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

    /**
     * Finds a getter method. Uses {@code getAllMembers()} on the leaf type so that
     * inherited methods from superclasses are also found (Feature 4: Inheritance).
     */
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

    /**
     * Finds a setter method. Uses {@code getAllMembers()} on the leaf type so that
     * inherited methods from superclasses are also found (Feature 4: Inheritance).
     */
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

    private boolean isValidEnc(String typeName, MetadataFieldType enc, VariableElement ve) {
        return switch (enc) {
            case DEFAULT -> true;
            case STRING -> {
                if (typeName.equals("byte[]")) {
                    error(ve, "@MetadataField(enc=STRING) is ambiguous for byte[] — " +
                            "use STRING_HEX or STRING_BASE64 to specify the encoding.");
                    yield false;
                }
                yield true;
            }
            case STRING_HEX, STRING_BASE64 -> {
                if (!typeName.equals("byte[]")) {
                    error(ve, "@MetadataField(enc=" + enc + ") is only valid for byte[] fields, " +
                            "but field has type '" + typeName + "'.");
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

    private void error(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

}
