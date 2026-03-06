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

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.CONVERTER;

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

        ClassDefinition classDefinition = createClassDefinition(typeElement, packageName, className);
        classDefinition.setObjType(typeElement.asType().toString());

        typeElement.getModifiers().stream().filter(modifier -> modifier.equals(Modifier.ABSTRACT))
                .findFirst().ifPresent(modifier -> classDefinition.setAbstract(true));

        if (typeElement.getKind() == ElementKind.ENUM) {
            processEnum(typeElement, classDefinition);
        }

        detectLombokAnnotations(typeElement, classDefinition);

        Constr plutusConstr = typeElement.getAnnotation(Constr.class);
        classDefinition.setAlternative(plutusConstr.alternative());

        processFields(typeElement, classDefinition);

        return classDefinition;
    }

    private ClassDefinition createClassDefinition(TypeElement typeElement, String packageName, String className) {
        Element enclosing = typeElement.getEnclosingElement();
        if (enclosing != null && enclosing.getKind().isInterface()) {
            String enclosingName = enclosing.getSimpleName().toString();

            return ClassDefinition.forNestedVariant(packageName, enclosingName, className, 0);
        }

        if (typeElement.getKind().isInterface()) {
            return ClassDefinition.forInterface(packageName, className);
        }

        return ClassDefinition.forTopLevel(packageName, className);
    }

    private void detectLombokAnnotations(TypeElement typeElement, ClassDefinition classDefinition) {
        try {
            Class lombokDataClazz = Class.forName("lombok.Data");
            Class lombokGetterClazz = Class.forName("lombok.Getter");
            Class lombokSetterClazz = Class.forName("lombok.Setter");

            boolean lombokData = typeElement.getAnnotation(lombokDataClazz) != null;
            boolean lombokGetter = typeElement.getAnnotation(lombokGetterClazz) != null;
            boolean lombokSetter = typeElement.getAnnotation(lombokSetterClazz) != null;

            if (lombokData || (lombokGetter && lombokSetter)) {
                classDefinition.setHasLombokAnnotation(true);
            }
        } catch (Exception e) {
        }
    }

    private void processFields(TypeElement typeElement, ClassDefinition classDefinition) {
        int index = 0;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof VariableElement variableElement &&
                    enclosedElement.getAnnotation(PlutusIgnore.class) == null) {
                Field field = processField(typeElement, classDefinition, variableElement, index++);
                if (field != null) {
                    classDefinition.getFields().add(field);
                }
            }
        }
    }

    private Field processField(TypeElement typeElement, ClassDefinition classDefinition,
                               VariableElement variableElement, int index) {
        String fieldName = variableElement.getSimpleName().toString();
        ExecutableElement getter = findGetter(typeElement, variableElement);
        ExecutableElement setter = findSetter(typeElement, variableElement);
        boolean isFieldVisible = variableElement.getModifiers().contains(Modifier.PUBLIC)
                || variableElement.getModifiers().isEmpty();

        if ((getter == null || setter == null) && !isFieldVisible && !classDefinition.isHasLombokAnnotation()) {
            error(variableElement, "Getter / Setter method not found for field: " + fieldName);
            return null;
        }

        Field field = new Field();
        field.setIndex(index);
        field.setName(fieldName);

        TypeName typeName = TypeName.get(variableElement.asType());
        try {
            field.setFieldType(detectFieldType(typeName, variableElement.asType()));
        } catch (NotSupportedException e) {
            error(variableElement, e.getMessage());
        }

        resolveGetterName(field, getter, setter, classDefinition, fieldName);

        Enc encodingField = variableElement.getAnnotation(Enc.class);
        if (encodingField != null && encodingField.value() != null) {
            field.getFieldType().setEncoding(encodingField.value());
        }

        return field;
    }

    private void resolveGetterName(Field field, ExecutableElement getter, ExecutableElement setter,
                                   ClassDefinition classDefinition, String fieldName) {
        if (getter != null && setter != null) {
            field.setHashGetter(true);
            field.setGetterName(getter.getSimpleName().toString());
        } else if (classDefinition.isHasLombokAnnotation()) {
            field.setHashGetter(true);
            if (Type.BOOL.equals(field.getFieldType().getType())
                    && JavaType.BOOLEAN.equals(field.getFieldType().getJavaType())) {
                field.setGetterName("is" + capitalize(fieldName));
            } else {
                field.setGetterName("get" + capitalize(fieldName));
            }
        }
    }

    private FieldType detectFieldType(TypeName typeName, TypeMirror typeMirror) throws NotSupportedException {
        FieldType fieldType = FieldTypeDetector.fromTypeName(typeName);
        if (fieldType != null) {
            // FieldTypeDetector doesn't have TypeMirror/typeElements context,
            // so fix up CONSTRUCTOR-typed generic arguments (e.g., List<Script>
            // where Script is an interface with nested converters)
            FieldTypeDetector.resolveConverterFqns(fieldType, this::isInterfaceType);
            return fieldType;
        }

        // TypeMirror-based List/Map subtype check (e.g., ArrayList<T> implements List<T>)
        if (typeName instanceof ParameterizedTypeName ptn && isAssignableToList(typeMirror)) {
            fieldType = new FieldType();
            fieldType.setFqTypeName(typeName.toString());
            fieldType.setType(Type.LIST);
            fieldType.setJavaType(JavaType.LIST);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0), null));
            return fieldType;
        }
        if (typeName instanceof ParameterizedTypeName ptn && isAssignableToMap(typeMirror)) {
            fieldType = new FieldType();
            fieldType.setFqTypeName(typeName.toString());
            fieldType.setType(Type.MAP);
            fieldType.setJavaType(JavaType.MAP);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0), null));
            fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(1), null));
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
            if (typeName instanceof ClassName className) {
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
                        resolveConverterFqn(className, isIface));
            }
            return fieldType;
        }

        throw new NotSupportedException("Type not supported: " + typeName);
    }

    /**
     * Checks if a type identified by (packageName, simpleName) is an interface
     * in the current set of {@code @Constr}-annotated type elements.
     */
    private boolean isInterfaceType(String packageName, String simpleName) {
        if (typeElements == null) return false;
        String fqn = packageName + "." + simpleName;
        for (TypeElement te : typeElements) {
            if (te.getQualifiedName().toString().equals(fqn) && te.getKind().isInterface()) {
                return true;
            }
        }
        return false;
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

    private void processEnum(TypeElement typeElement, ClassDefinition classDefinition) {
        // Log that we found an enum
       log.debug("Found enum: " + typeElement.getQualifiedName());

        classDefinition.setEnum(true);
        List<String> enumValues = new ArrayList<>();
        // Find all enum constants
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof VariableElement variableElement
                    && enclosedElement.getKind() == ElementKind.ENUM_CONSTANT) {
                log.debug("Enum constant: " + variableElement.getSimpleName());
                enumValues.add(variableElement.getSimpleName().toString());
            }
        }

        classDefinition.setEnumValues(enumValues);
    }

}
