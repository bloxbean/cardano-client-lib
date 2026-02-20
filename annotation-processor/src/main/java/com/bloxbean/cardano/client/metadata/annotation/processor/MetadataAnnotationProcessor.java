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
import javax.lang.model.type.TypeKind;
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
                if (!(element instanceof TypeElement)) continue;
                TypeElement typeElement = (TypeElement) element;

                String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).toString();
                String className = typeElement.getSimpleName().toString();

                boolean hasLombok = detectLombok(typeElement);
                List<MetadataFieldInfo> fields = extractFields(typeElement, hasLombok);

                try {
                    TypeSpec typeSpec = generator.generate(packageName, className, fields);
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

    private List<MetadataFieldInfo> extractFields(TypeElement typeElement, boolean hasLombok) {
        List<MetadataFieldInfo> fields = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (!(enclosed instanceof VariableElement)) continue;
            VariableElement ve = (VariableElement) enclosed;

            // Skip static fields
            if (ve.getModifiers().contains(Modifier.STATIC)) continue;

            // Skip @MetadataIgnore fields
            if (ve.getAnnotation(MetadataIgnore.class) != null) continue;

            String fieldName = ve.getSimpleName().toString();
            String typeName = ve.asType().toString();

            // Enum detection — enums are not in isSupportedScalarType() because the class name varies
            Element typeEl = processingEnv.getTypeUtils().asElement(ve.asType());
            boolean isEnum = typeEl != null && typeEl.getKind() == ElementKind.ENUM;

            if (!isEnum && !isSupportedType(typeName)) {
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

            // Extract element type for List<T> / Set<T> / SortedSet<T> / Optional<T> fields; warn if enc= is used
            String elementTypeName = null;
            if ((typeName.startsWith("java.util.List<") || typeName.startsWith("java.util.Set<")
                    || typeName.startsWith("java.util.SortedSet<")
                    || typeName.startsWith("java.util.Optional<"))
                    && typeName.endsWith(">")) {
                elementTypeName = typeName.substring(typeName.indexOf('<') + 1, typeName.length() - 1);
                if (enc != MetadataFieldType.DEFAULT) {
                    messager.printMessage(Diagnostic.Kind.WARNING,
                            "Field '" + fieldName + "': @MetadataField(enc=...) is not supported on collection fields; using DEFAULT.", ve);
                    enc = MetadataFieldType.DEFAULT;
                }
            }

            if (!isValidEnc(typeName, enc, ve)) {
                continue;
            }

            // Detect getter
            String getterName = null;
            ExecutableElement getter = findGetter(typeElement, ve);
            if (getter != null) {
                getterName = getter.getSimpleName().toString();
            } else if (hasLombok) {
                getterName = "get" + capitalize(fieldName);
            } else if (!isDirectlyAccessible(ve)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                        "No getter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
                continue;
            }

            // Detect setter
            String setterName = null;
            ExecutableElement setter = findSetter(typeElement, ve);
            if (setter != null) {
                setterName = setter.getSimpleName().toString();
            } else if (hasLombok) {
                setterName = "set" + capitalize(fieldName);
            } else if (!isDirectlyAccessible(ve)) {
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

            fields.add(info);
        }

        return fields;
    }

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
        switch (typeName) {
            case "java.lang.String":
            case "java.math.BigInteger":
            case "java.math.BigDecimal":
            case "java.lang.Long":
            case "long":
            case "java.lang.Integer":
            case "int":
            case "java.lang.Short":
            case "short":
            case "java.lang.Byte":
            case "byte":
            case "java.lang.Boolean":
            case "boolean":
            case "java.lang.Double":
            case "double":
            case "java.lang.Float":
            case "float":
            case "java.lang.Character":
            case "char":
            case "byte[]":
            case "java.net.URI":
            case "java.net.URL":
            case "java.util.UUID":
            case "java.util.Currency":
            case "java.util.Locale":
            case "java.time.Instant":
            case "java.time.LocalDate":
            case "java.time.LocalDateTime":
                return true;
            default:
                return false;
        }
    }

    private ExecutableElement findGetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String getterMethodName = "get" + capitalize(fieldName);
        String isGetterMethodName = "is" + capitalize(fieldName);
        String fieldTypeName = variableElement.asType().toString();
        boolean isBooleanType = fieldTypeName.equals("boolean") || fieldTypeName.equals("java.lang.Boolean");

        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (!(enclosedElement instanceof ExecutableElement)) continue;
            ExecutableElement executableElement = (ExecutableElement) enclosedElement;
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

        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (!(enclosedElement instanceof ExecutableElement)) continue;
            ExecutableElement executableElement = (ExecutableElement) enclosedElement;
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
        switch (enc) {
            case DEFAULT:
                return true;
            case STRING:
                if (typeName.equals("byte[]")) {
                    error(ve, "@MetadataField(enc=STRING) is ambiguous for byte[] — " +
                            "use STRING_HEX or STRING_BASE64 to specify the encoding.");
                    return false;
                }
                return true;
            case STRING_HEX:
            case STRING_BASE64:
                if (!typeName.equals("byte[]")) {
                    error(ve, "@MetadataField(enc=" + enc + ") is only valid for byte[] fields, " +
                            "but field has type '" + typeName + "'.");
                    return false;
                }
                return true;
            default:
                return true;
        }
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void error(Element e, String msg) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

}
