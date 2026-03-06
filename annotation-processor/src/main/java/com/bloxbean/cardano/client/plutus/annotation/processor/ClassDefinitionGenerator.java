package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.Enc;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.NotSupportedException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.FieldTypeDetector;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.CONVERTER;
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.IMPL;

/**
 * Generates ClassDefinition from the given TypeElement
 */
@Slf4j
public class ClassDefinitionGenerator {
    private ProcessingEnvironment processingEnvironment;
    private Elements elements;
    private List<TypeElement> typeElements = null;

    public ClassDefinitionGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        this.elements = processingEnvironment.getElementUtils();
    }

    public void setTypeElements(List<TypeElement> typeElements) {
        this.typeElements = typeElements;
    }

    public ClassDefinition getClassDefinition(TypeElement typeElement) {
        String packageName = processingEnvironment.getElementUtils().getPackageOf(typeElement).toString();
        String className = typeElement.getSimpleName().toString();

        // Use factory methods based on nesting context
        ClassDefinition classDefinition;
        Element enclosing = typeElement.getEnclosingElement();
        if (enclosing != null && enclosing.getKind().isInterface()) {
            // Nested variant inside interface
            String enclosingName = ((TypeElement) enclosing).getSimpleName().toString();
            classDefinition = ClassDefinition.forNestedVariant(packageName, enclosingName, className, 0);
        } else if (typeElement.getKind().isInterface()) {
            // Interface type itself
            classDefinition = ClassDefinition.forInterface(packageName, className);
        } else {
            // Regular top-level type
            classDefinition = ClassDefinition.forTopLevel(packageName, className);
        }
        classDefinition.setObjType(typeElement.asType().toString());

        typeElement.getModifiers().stream().filter(modifier -> modifier.equals(Modifier.ABSTRACT))
                .findFirst().ifPresent(modifier -> classDefinition.setAbstract(true));

        //If typeElement is enum, get emum values
        if(typeElement.getKind() == ElementKind.ENUM) {
            processEnum(typeElement, classDefinition);
        }

        Class lombokDataClazz;
        Class lombokGetterClazz;
        Class lombokSetterClazz;

        try {
            lombokDataClazz = Class.forName("lombok.Data");
            lombokGetterClazz = Class.forName("lombok.Getter");
            lombokSetterClazz = Class.forName("lombok.Setter");

            boolean lombokData = typeElement.getAnnotation(lombokDataClazz) != null;
            boolean lombokGetter = typeElement.getAnnotation(lombokGetterClazz) != null;
            boolean lombokSetter = typeElement.getAnnotation(lombokSetterClazz) != null;

            if (lombokData || (lombokGetter && lombokSetter)) {
                classDefinition.setHasLombokAnnotation(true);
            }
        } catch (Exception e) {
        }

        Constr plutusConstr = typeElement.getAnnotation(Constr.class);
        int alternative = plutusConstr.alternative();
        classDefinition.setAlternative(alternative);

        int index = 0;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof VariableElement variableElement &&
                    enclosedElement.getAnnotation(PlutusIgnore.class) == null) {
                Field field = new Field();
                field.setIndex(index++);
                String fieldName = variableElement.getSimpleName().toString();
                field.setName(fieldName);
                ExecutableElement getter = findGetter(typeElement, variableElement);
                ExecutableElement setter = findSetter(typeElement, variableElement);
                boolean isFieldVisible = variableElement.getModifiers().contains(Modifier.PUBLIC)
                        || variableElement.getModifiers().size() == 0; //default

                if ((getter == null || setter == null) && !isFieldVisible && !classDefinition.isHasLombokAnnotation()) {
                    error(variableElement, "Getter / Setter method not found for field: " + fieldName);
                    continue;
                }

                TypeName typeName = TypeName.get(variableElement.asType());
                FieldType fieldType = null;
                try {
                    fieldType = detectFieldType(typeName, variableElement.asType());
                } catch (NotSupportedException e) {
                    error(variableElement, e.getMessage());
                }
                field.setFieldType(fieldType);

                if (getter != null && setter != null) {
                    field.setHashGetter(true);
                    field.setGetterName(getter.getSimpleName().toString());
                } else if (classDefinition.isHasLombokAnnotation()) {
                    field.setHashGetter(true);
                    if (Type.BOOL.equals(field.getFieldType().getType())) {
                        if (JavaType.BOOLEAN.equals(field.getFieldType().getJavaType())) {
                            field.setGetterName("is" + capitalize(fieldName));
                        } else {
                            field.setGetterName("get" + capitalize(fieldName));
                        }
                    } else {
                        field.setGetterName("get" + capitalize(fieldName));
                    }
                }

                Enc encodingField = variableElement.getAnnotation(Enc.class);
                if (encodingField != null && encodingField.value() != null) {
                    field.getFieldType().setEncoding(encodingField.value());
                }

                classDefinition.getFields().add(field);
            }
        }

        return classDefinition;
    }

    private int getAlternative(String fieldName) {
        Optional<TypeElement> first = typeElements.stream().filter(typeElement -> typeElement.getSimpleName().toString().toLowerCase().equals(fieldName.toLowerCase())).findFirst();
        if(first.isPresent()) {
            TypeElement typeElement = first.get();
            return typeElement.getAnnotation(Constr.class).alternative();
        } else {
            return 0;
        }
    }

    private FieldType detectFieldType(TypeName typeName, TypeMirror typeMirror) throws NotSupportedException {
        FieldType fieldType = FieldTypeDetector.fromTypeName(typeName);
        if (fieldType != null) {
            // FieldTypeDetector doesn't have TypeMirror/typeElements context,
            // so fix up CONSTRUCTOR-typed generic arguments (e.g., List<Script>
            // where Script is an interface with nested converters)
            fixUpConstructorGenericTypes(fieldType);
            return fieldType;
        }

        // TypeMirror-based List/Map subtype check (e.g., ArrayList<T> implements List<T>)
        if (typeName instanceof ParameterizedTypeName && isAssignableToList(typeMirror)) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            fieldType = new FieldType();
            fieldType.setFqTypeName(typeName.toString());
            fieldType.setType(Type.LIST);
            fieldType.setJavaType(JavaType.LIST);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(parameterizedTypeName.typeArguments.get(0), null));
            return fieldType;
        }
        if (typeName instanceof ParameterizedTypeName && isAssignableToMap(typeMirror)) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            fieldType = new FieldType();
            fieldType.setFqTypeName(typeName.toString());
            fieldType.setType(Type.MAP);
            fieldType.setJavaType(JavaType.MAP);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(parameterizedTypeName.typeArguments.get(0), null));
            fieldType.getGenericTypes().add(detectFieldType(parameterizedTypeName.typeArguments.get(1), null));
            return fieldType;
        }

        // CONSTRUCTOR fallback — needs TypeMirror for shared type checks
        if (isSupportedType(typeName, typeMirror)) {
            fieldType = new FieldType();
            fieldType.setFqTypeName(typeName.toString());
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(isRawDataType(typeMirror));
            fieldType.setDataType(isDataType(typeMirror));

            // Resolve converter FQN, checking if the type is an interface
            if (typeName instanceof ClassName) {
                boolean isIface = false;
                if (typeElements != null) {
                    for (TypeElement te : typeElements) {
                        boolean match = (typeMirror != null && te.asType().equals(typeMirror))
                                || te.getQualifiedName().toString().equals(typeName.toString());
                        if (match && te.getKind().isInterface()) {
                            isIface = true;
                            break;
                        }
                    }
                }
                fieldType.setConverterClassFqn(
                        resolveConverterFqn((ClassName) typeName, isIface));
            }
            return fieldType;
        }

        throw new NotSupportedException("Type not supported: " + typeName);
    }

    /**
     * Post-processes generic type arguments to set converterClassFqn for
     * CONSTRUCTOR types. FieldTypeDetector can't do this because it lacks
     * access to typeElements.
     */
    private void fixUpConstructorGenericTypes(FieldType fieldType) {
        for (FieldType genericType : fieldType.getGenericTypes()) {
            if (genericType.getType() == Type.CONSTRUCTOR && typeElements != null) {
                String typeFqn = genericType.getJavaType().getName();
                boolean isIface = false;
                for (TypeElement te : typeElements) {
                    if (te.getQualifiedName().toString().equals(typeFqn) && te.getKind().isInterface()) {
                        isIface = true;
                        break;
                    }
                }
                try {
                    ClassName cn = ClassName.bestGuess(typeFqn);
                    genericType.setConverterClassFqn(resolveConverterFqn(cn, isIface));
                } catch (IllegalArgumentException ignored) {
                    // bestGuess can fail for parameterized types — skip
                }
            }
            fixUpConstructorGenericTypes(genericType);
        }
    }

    private boolean isSupportedType(TypeName typeName, TypeMirror typeMirror) {
        for (TypeElement typeElement : typeElements) {
            if (typeMirror != null && typeElement.asType().equals(typeMirror)) {
                return true;
            } else if (typeElement.getQualifiedName().toString().equals(typeName.toString())) {
                return true;
            }
        }
        return true;
    }

    private ExecutableElement findGetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String getterMethodName = "get" + capitalize(fieldName.toString());

        String altGetterMethodName = null;
        if (variableElement.asType().getKind().equals(TypeKind.BOOLEAN)
                || variableElement.asType().toString().equals("java.lang.Boolean")) {
            altGetterMethodName = "is" + capitalize(fieldName.toString());
        }

        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement executableElement) {
                if ((executableElement.getSimpleName().toString().equals(getterMethodName) ||
                        executableElement.getSimpleName().toString().equals(altGetterMethodName)) &&
                        executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                        executableElement.getParameters().isEmpty() &&
                        executableElement.getReturnType().toString().equals(variableElement.asType().toString())) {
                    return executableElement;
                }
            }
        }

        return null;
    }

    private ExecutableElement findSetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String setterName = "set" + capitalize(fieldName.toString());
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement executableElement) {
                if (executableElement.getSimpleName().toString().equals(setterName) &&
                        executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                        executableElement.getParameters().size() == 1 &&
                        executableElement.getParameters().get(0).asType().toString().equals(variableElement.asType().toString()) &&
                        executableElement.getReturnType().getKind().equals(TypeKind.VOID)) {
                    return executableElement;
                }
            }
        }

        return null;
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void error(Element e, String msg, Object... args) {
        processingEnvironment.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    private static final String DATA_INTERFACE_FQN = "com.bloxbean.cardano.client.plutus.blueprint.model.Data";
    private static final String RAW_DATA_INTERFACE_FQN = "com.bloxbean.cardano.client.plutus.blueprint.model.RawData";

    /**
     * Checks if a type implements {@code Data<T>} — a constr-based shared type
     * that is NOT a generated {@code @Constr} model class.
     */
    private boolean isDataType(TypeMirror typeMirror) {
        if (typeMirror == null) return false;
        Types typeUtils = processingEnvironment.getTypeUtils();
        Element element = typeUtils.asElement(typeMirror);
        if (!(element instanceof TypeElement typeElement)) return false;
        // @Constr-annotated classes are generated model classes, not shared types
        if (typeElement.getAnnotation(Constr.class) != null) return false;
        TypeElement dataInterface = elements.getTypeElement(DATA_INTERFACE_FQN);
        if (dataInterface == null) return false;
        return typeUtils.isAssignable(typeUtils.erasure(typeMirror), typeUtils.erasure(dataInterface.asType()));
    }

    /**
     * Checks if a type implements {@code RawData} — a bytes-wrapper shared type
     * whose on-chain encoding is raw {@code PlutusData} (not {@code ConstrPlutusData}).
     */
    private boolean isRawDataType(TypeMirror typeMirror) {
        if (typeMirror == null) return false;
        TypeElement rawDataInterface = elements.getTypeElement(RAW_DATA_INTERFACE_FQN);
        if (rawDataInterface == null) return false;
        Types typeUtils = processingEnvironment.getTypeUtils();
        return typeUtils.isAssignable(typeMirror, rawDataInterface.asType());
    }

    private boolean isAssignableToMap(TypeMirror typeMirror) {
        if (typeMirror == null)
            return false;
        Types typeUtils = processingEnvironment.getTypeUtils();

        // Check if the type is assignable to java.util.Map
        if (typeUtils.isAssignable(typeMirror, typeUtils.getDeclaredType(elements.getTypeElement("java.util.Map"))))
            return true;
        else
            return false;
    }

    private boolean isAssignableToList(TypeMirror typeMirror) {
        if (typeMirror == null)
            return false;
        Types typeUtils = processingEnvironment.getTypeUtils();

        // Check if the type is assignable to java.util.List
        if (typeUtils.isAssignable(typeMirror, typeUtils.getDeclaredType(elements.getTypeElement("java.util.List"))))
            return true;
        else
            return false;
    }

    /**
     * Resolves the fully-qualified converter class name for a given type.
     *
     * @param typeClass   the ClassName of the type
     * @param isInterface true if the type is an interface with nested converters
     * @return the FQN of the converter (e.g., "com.example.Credential.CredentialConverter")
     */
    public static String resolveConverterFqn(ClassName typeClass, boolean isInterface) {
        if (typeClass.simpleNames().size() > 1) {
            // Nested variant type (e.g., Credential.VerificationKey):
            // converter is a sibling nested class in the parent interface
            String parentName = typeClass.simpleNames().get(0);
            String variantName = typeClass.simpleNames().get(typeClass.simpleNames().size() - 1);
            return typeClass.packageName() + "." + parentName + "." + variantName + CONVERTER;
        }
        if (isInterface) {
            // Interface type with nested dispatch converter
            String name = typeClass.simpleName();
            return typeClass.packageName() + "." + name + "." + name + CONVERTER;
        }
        // Top-level type: converter in converter sub-package
        return getConverterPackageName(typeClass.packageName()) + "." + typeClass.simpleName() + CONVERTER;
    }

    public static ClassName getConverterClassFromField(FieldType fieldType) {
        // Direct FQN lookup — set at construction time
        if (fieldType.getConverterClassFqn() != null) {
            return ClassName.bestGuess(fieldType.getConverterClassFqn());
        }

        // Defensive fallback: derive from type name
        ClassName fieldClass = ClassName.bestGuess(fieldType.getJavaType().getName());

        if (fieldClass.simpleNames().size() > 1) {
            // Nested variant type (e.g., Credential.VerificationKey):
            // converter is a sibling nested class in the parent interface
            String pkg = fieldClass.packageName();
            List<String> simpleNames = fieldClass.simpleNames();
            String parentName = simpleNames.get(0);
            String variantName = simpleNames.get(simpleNames.size() - 1);
            return ClassName.get(pkg, parentName, variantName + CONVERTER);
        }

        // Top-level type: converter in converter sub-package
        String converterPkg = getConverterPackageName(fieldClass.packageName());
        String converterSimpleName = fieldClass.simpleName() + CONVERTER;
        return ClassName.get(converterPkg, converterSimpleName);
    }

    private static String getConverterPackageName(String modelPackage) {
        return modelPackage + ".converter";
    }

    private static String getImplPackageName(String modelPackage) {
        return modelPackage + ".impl";
    }

    private void processEnum(TypeElement typeElement, ClassDefinition classDefinition) {
        // Log that we found an enum
       log.debug("Found enum: " + typeElement.getQualifiedName());

        classDefinition.setEnum(true);
        List<String> enumValues = new ArrayList<>();
        // Find all enum constants
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.ENUM_CONSTANT) {
                VariableElement variableElement = (VariableElement) enclosedElement;
                log.debug("Enum constant: " + variableElement.getSimpleName());
                enumValues.add(variableElement.getSimpleName().toString());
            }
        }

        classDefinition.setEnumValues(enumValues);
    }
}
