package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.Enc;
import com.bloxbean.cardano.client.plutus.annotation.PlutusIgnore;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.NotSupportedException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        String serializationClassName = className + "PlutusDataConverter";

        ClassDefinition classDefinition = new ClassDefinition();
        classDefinition.setPackageName(packageName);
        classDefinition.setName(serializationClassName);
        classDefinition.setObjType(typeElement.asType().toString());

        Constr plutusConstr = typeElement.getAnnotation(Constr.class);
        int alternative = plutusConstr.alternative();
        classDefinition.setAlternative(alternative);

        int index = 0;
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof VariableElement &&
                    enclosedElement.getAnnotation(PlutusIgnore.class) == null) {
                Field field = new Field();
                field.setIndex(index++);

                VariableElement variableElement = (VariableElement) enclosedElement;
                String fieldName = variableElement.getSimpleName().toString();
                field.setName(fieldName);

                ExecutableElement getter = findGetter(typeElement, variableElement);
                ExecutableElement setter = findSetter(typeElement, variableElement);
                boolean isFieldVisible = variableElement.getModifiers().contains(Modifier.PUBLIC)
                        || variableElement.getModifiers().size() == 0; //default

                if ((getter == null || setter == null) && !isFieldVisible) {
                    error(variableElement, "Getter / Setter method not found for field: " + fieldName);
                    continue;
                }

                if (getter != null && setter != null)
                    field.setHashGetter(true);

                TypeName typeName = TypeName.get(variableElement.asType());
                FieldType fieldType = null;
                try {
                    fieldType = detectFieldType(typeName, variableElement.asType());
                } catch (NotSupportedException e) {
                    error(variableElement, e.getMessage());
                }
                field.setFieldType(fieldType);

                Enc encodingField = variableElement.getAnnotation(Enc.class);
                if (encodingField != null && encodingField.value() != null) {
                    field.getFieldType().setEncoding(encodingField.value());
                }

                classDefinition.getFields().add(field);
            }
        }

        return classDefinition;
    }

    private FieldType detectFieldType(TypeName typeName, TypeMirror typeMirror) throws NotSupportedException {
        FieldType fieldType = new FieldType();
        if (typeName.equals(TypeName.get(Long.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.LONG_OBJECT);
        } else if (typeName.equals(TypeName.LONG)) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.LONG);
        } else if (typeName.equals(TypeName.get(BigInteger.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.BIGINTEGER);
        } else if (typeName.equals(TypeName.get(Integer.class))) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.INTEGER);
        } else if (typeName.equals(TypeName.INT)) {
            fieldType.setType(Type.INTEGER);
            fieldType.setJavaType(JavaType.INT);
        } else if (typeName.equals(TypeName.get(String.class))) {
            fieldType.setType(Type.STRING);
            fieldType.setJavaType(JavaType.STRING);
        } else if (typeName.equals(byte[].class)) {
            fieldType.setType(Type.BYTES);
            fieldType.setJavaType(JavaType.BYTES);
        } else if (typeName instanceof ParameterizedTypeName &&
                (((ParameterizedTypeName) typeName).rawType.equals(ClassName.get(List.class))
                        || isAssignableToList(typeMirror))) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            TypeName itemType = parameterizedTypeName.typeArguments.get(0);

            fieldType.setType(Type.LIST);
            fieldType.setJavaType(JavaType.LIST);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(itemType, null));
        } else if (typeName instanceof ParameterizedTypeName
                && (((ParameterizedTypeName) typeName).rawType.equals(ClassName.get(Map.class)) ||
                isAssignableToMap(typeMirror))) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            TypeName keyItemType = parameterizedTypeName.typeArguments.get(0);
            TypeName valueItemType = parameterizedTypeName.typeArguments.get(1);

            fieldType.setType(Type.MAP);
            fieldType.setJavaType(JavaType.MAP);
            fieldType.setCollection(true);
            fieldType.getGenericTypes().add(detectFieldType(keyItemType, null));
            fieldType.getGenericTypes().add(detectFieldType(valueItemType, null));
        } else if (typeName instanceof ParameterizedTypeName
                && ((ParameterizedTypeName) typeName).rawType.equals(ClassName.get(Optional.class))) {
            ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;
            TypeName itemType = parameterizedTypeName.typeArguments.get(0);

            fieldType.setType(Type.OPTIONAL);
            fieldType.setJavaType(JavaType.OPTIONAL);
            fieldType.getGenericTypes().add(detectFieldType(itemType, null));
        } else {
            if (isSupportedType(typeName, typeMirror)) {
                fieldType.setType(Type.CONSTRUCTOR);
                fieldType.setJavaType(new JavaType(typeName.toString(), true));
            } else {
                throw new NotSupportedException("Type not supported: " + typeName);
            }
        }

        return fieldType;
    }

    private boolean isSupportedType(TypeName typeName, TypeMirror typeMirror) {
        for (TypeElement typeElement: typeElements) {
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
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) enclosedElement;
                if (executableElement.getSimpleName().toString().equals(getterMethodName) &&
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
            if (enclosedElement instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) enclosedElement;
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
}
