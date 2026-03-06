package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.Enc;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.NotSupportedException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.blueprint.type.Pair;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quartet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Quintet;
import com.bloxbean.cardano.client.plutus.blueprint.type.Triple;
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        // For nested classes inside interfaces, prefix converter/impl names with enclosing type
        // to avoid collisions (e.g., Credential.VerificationKey → CredentialVerificationKeyConverter)
        String prefix = className;
        Element enclosing = typeElement.getEnclosingElement();
        if (enclosing != null && enclosing.getKind().isInterface()) {
            String enclosingName = ((TypeElement) enclosing).getSimpleName().toString();
            prefix = enclosingName + className;
        }

        ClassDefinition classDefinition = new ClassDefinition();
        classDefinition.setPackageName(packageName);
        classDefinition.setDataClassName(className);
        classDefinition.setImplClassName(prefix + IMPL);
        classDefinition.setConverterClassName(prefix + CONVERTER);
        classDefinition.setObjType(typeElement.asType().toString());

        typeElement.getModifiers().stream().filter(modifier -> modifier.equals(Modifier.ABSTRACT))
                .findFirst().ifPresent(modifier -> classDefinition.setAbstract(true));

        //If typeElement is enum, get emum values
        if(typeElement.getKind() == ElementKind.ENUM) {
            processEnum(typeElement, classDefinition);
        }

        classDefinition.setConverterPackageName(getConverterPackageName(packageName));
        classDefinition.setImplPackageName(getImplPackageName(packageName));

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

    private record TypeMapping(Type type, JavaType javaType) {}

    private static final Map<TypeName, TypeMapping> SIMPLE_TYPES = Map.ofEntries(
            Map.entry(TypeName.get(Long.class),       new TypeMapping(Type.INTEGER, JavaType.LONG_OBJECT)),
            Map.entry(TypeName.LONG,                  new TypeMapping(Type.INTEGER, JavaType.LONG)),
            Map.entry(TypeName.get(BigInteger.class),  new TypeMapping(Type.INTEGER, JavaType.BIGINTEGER)),
            Map.entry(TypeName.get(Integer.class),     new TypeMapping(Type.INTEGER, JavaType.INTEGER)),
            Map.entry(TypeName.INT,                   new TypeMapping(Type.INTEGER, JavaType.INT)),
            Map.entry(TypeName.get(String.class),      new TypeMapping(Type.STRING, JavaType.STRING)),
            Map.entry(TypeName.get(byte[].class),      new TypeMapping(Type.BYTES, JavaType.BYTES)),
            Map.entry(TypeName.get(Boolean.class),     new TypeMapping(Type.BOOL, JavaType.BOOLEAN_OBJ)),
            Map.entry(TypeName.BOOLEAN,               new TypeMapping(Type.BOOL, JavaType.BOOLEAN)),
            Map.entry(TypeName.get(PlutusData.class),  new TypeMapping(Type.PLUTUSDATA, JavaType.PLUTUSDATA))
    );

    private static final Map<ClassName, TypeMapping> TUPLE_TYPES = Map.of(
            ClassName.get(Pair.class),    new TypeMapping(Type.PAIR, JavaType.PAIR),
            ClassName.get(Triple.class),  new TypeMapping(Type.TRIPLE, JavaType.TRIPLE),
            ClassName.get(Quartet.class), new TypeMapping(Type.QUARTET, JavaType.QUARTET),
            ClassName.get(Quintet.class), new TypeMapping(Type.QUINTET, JavaType.QUINTET)
    );

    private FieldType detectFieldType(TypeName typeName, TypeMirror typeMirror) throws NotSupportedException {
        FieldType fieldType = new FieldType();
        fieldType.setFqTypeName(typeName.toString());

        // Simple (non-generic) types
        TypeMapping simple = SIMPLE_TYPES.get(typeName);
        if (simple != null) {
            fieldType.setType(simple.type());
            fieldType.setJavaType(simple.javaType());
            return fieldType;
        }

        // Parameterized types
        if (typeName instanceof ParameterizedTypeName ptn) {
            ClassName rawType = ptn.rawType;

            if (rawType.equals(ClassName.get(List.class)) || isAssignableToList(typeMirror)) {
                fieldType.setType(Type.LIST);
                fieldType.setJavaType(JavaType.LIST);
                fieldType.setCollection(true);
                fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0), null));
                return fieldType;
            }

            if (rawType.equals(ClassName.get(Map.class)) || isAssignableToMap(typeMirror)) {
                fieldType.setType(Type.MAP);
                fieldType.setJavaType(JavaType.MAP);
                fieldType.setCollection(true);
                fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0), null));
                fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(1), null));
                return fieldType;
            }

            if (rawType.equals(ClassName.get(Optional.class))) {
                fieldType.setType(Type.OPTIONAL);
                fieldType.setJavaType(JavaType.OPTIONAL);
                fieldType.getGenericTypes().add(detectFieldType(ptn.typeArguments.get(0), null));
                return fieldType;
            }

            // Tuple types (Pair, Triple, Quartet, Quintet)
            TypeMapping tuple = TUPLE_TYPES.get(rawType);
            if (tuple != null) {
                fieldType.setType(tuple.type());
                fieldType.setJavaType(tuple.javaType());
                for (TypeName arg : ptn.typeArguments) {
                    fieldType.getGenericTypes().add(detectFieldType(arg, null));
                }
                return fieldType;
            }
        }

        // Constructor/custom type fallback
        if (isSupportedType(typeName, typeMirror)) {
            fieldType.setType(Type.CONSTRUCTOR);
            fieldType.setJavaType(new JavaType(typeName.toString(), true));
            fieldType.setRawDataType(isRawDataType(typeMirror));
            fieldType.setDataType(isDataType(typeMirror));
            return fieldType;
        }

        throw new NotSupportedException("Type not supported: " + typeName);
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

    public static ClassName getConverterClassFromField(FieldType fieldType) {
        ClassName fieldClass = ClassName.bestGuess(fieldType.getJavaType().getName());
        String converterPkg = getConverterPackageName(fieldClass.packageName());
        // Join all simple names for nested classes: ["Credential","VerificationKey"] → "CredentialVerificationKey"
        // For top-level classes: ["Address"] → "Address" (unchanged)
        String converterSimpleName = String.join("", fieldClass.simpleNames()) + CONVERTER;
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
