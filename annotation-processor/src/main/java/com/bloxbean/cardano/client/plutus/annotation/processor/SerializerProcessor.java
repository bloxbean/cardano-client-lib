package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.PlutusField;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes(
        "com.bloxbean.cardano.client.plutus.annotation.Constr")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
@Slf4j
public class SerializerProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    try {
                        generateJSONSerializationClass(typeElement);
                    } catch (IOException e) {
                        log.error("Failed to generate JSON serialization class: " + e.getMessage());
                    }
                }
            }
        }
        return true;
    }

    private void generateJSONSerializationClass(TypeElement typeElement) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).toString();
        String className = typeElement.getSimpleName().toString();
        String serializationClassName = className + "Serializer";

        Constr plutusConstr = typeElement.getAnnotation(Constr.class);
        int alternative = plutusConstr.alternative();

        // Create the JSON serialization class
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(serializationClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(BasePlutusDataSerializer.class);

        // Create the serialize method
        MethodSpec.Builder serializeMethodBuilder = MethodSpec.methodBuilder("serialize")
                .addModifiers(Modifier.PUBLIC)
                .returns(ConstrPlutusData.class)
                .addParameter(TypeName.get(typeElement.asType()), "obj");

        CodeBlock serializeBody = CodeBlock.builder()
                .addStatement("$T constr = initConstr($L)", ConstrPlutusData.class, alternative)
                .build();
        serializeMethodBuilder.addCode(serializeBody);
        // Add the fields annotated with @PlutusField to the class wrapper
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof VariableElement &&
                    enclosedElement.getAnnotation(PlutusField.class) != null) {

                VariableElement variableElement = (VariableElement) enclosedElement;
                String fieldName = variableElement.getSimpleName().toString();

                ExecutableElement getter = findGetter(typeElement, variableElement);

                boolean isFieldVisible = variableElement.getModifiers().contains(Modifier.PUBLIC)
                        || variableElement.getModifiers().size() == 0; //default

                if (getter == null && !isFieldVisible) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Getter method not found for field: " + fieldName, variableElement);
                    continue;
                }
                String getterMethodName = "get" + capitalize(fieldName.toString());
                String fieldOrGetterName = getter != null ? getterMethodName + "()" : fieldName;

                TypeName fieldType = TypeName.get(variableElement.asType());
                if (fieldType.equals(TypeName.get(Long.class)) || fieldType.equals(TypeName.LONG)
                        || fieldType.equals(TypeName.get(BigInteger.class)) || fieldType.equals(TypeName.get(Integer.class))
                        || fieldType.equals(TypeName.INT) || fieldType.equals(TypeName.get(String.class))
                        || fieldType.equals(byte[].class)) {
                    // Code for Long type
                    CodeBlock fieldSerializeBody = CodeBlock.builder()
                            .addStatement("constr.getData().add(toPlutusData(obj.$L))", fieldOrGetterName)
                            .build();
                    serializeMethodBuilder.addCode(fieldSerializeBody);
                } else if (fieldType.equals(TypeName.get(List.class))) {
                    System.out.println("Collection type not supported yet : " + fieldName);
                } else if (fieldType instanceof ParameterizedTypeName && ((ParameterizedTypeName) fieldType).rawType.equals(ClassName.get(List.class))) {
                    ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) fieldType;
                    TypeName itemType = parameterizedTypeName.typeArguments.get(0);

                    if (itemType.equals(TypeName.get(Long.class)) || itemType.equals(TypeName.LONG)
                            || itemType.equals(TypeName.get(BigInteger.class)) || itemType.equals(TypeName.get(Integer.class))
                            || itemType.equals(TypeName.INT) || itemType.equals(TypeName.get(String.class))
                            || itemType.equals(byte[].class)) {
                        CodeBlock fieldSerializeBody = CodeBlock.builder()
                                .addStatement("$T $LListPlutusData = $T.builder().build();", ListPlutusData.class, fieldName, ListPlutusData.class)
                                .beginControlFlow("for($T item: obj.$L)", itemType, fieldOrGetterName)
                                .addStatement("$LListPlutusData.add(toPlutusData(item))", fieldOrGetterName)
                                .endControlFlow()
                                .addStatement("constr.getData().add($LListPlutusData)", fieldName)
                                .build();
                        serializeMethodBuilder.addCode(fieldSerializeBody);
                    } else {
                        CodeBlock fieldSerializeBody = CodeBlock.builder()
                                .addStatement("$T $LListPlutusData = $T.builder().build();", ListPlutusData.class, fieldName, ListPlutusData.class)
                                .beginControlFlow("for($T item: obj.$L)", itemType, fieldOrGetterName)
                                .addStatement("$LListPlutusData.add(new $LSerializer().serialize(obj.$L))", fieldName, itemType.toString(), fieldOrGetterName)
                                .endControlFlow()
                                .addStatement("constr.getData().add($LListPlutusData)", fieldName)
                                .build();
                        serializeMethodBuilder.addCode(fieldSerializeBody);
                    }

                } else {
                    CodeBlock fieldSerializeBody = CodeBlock.builder()
                            .addStatement("constr.getData().add(new $LSerializer().serialize(obj.$L)))", fieldType.toString(), fieldOrGetterName)
                            .build();
                    serializeMethodBuilder.addCode(fieldSerializeBody);
                }
            }
        }

        serializeMethodBuilder.addStatement("return $L", "constr");

        classBuilder.addMethod(serializeMethodBuilder.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .build();
        JavaFileObject builderFile = processingEnv.getFiler()
                .createSourceFile(serializationClassName);
        javaFile.writeTo(builderFile.openWriter());
        javaFile.writeTo(System.out);
    }

    private void error(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
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
                        executableElement.getReturnType().equals(variableElement.asType())) {
                    return executableElement;
                }
            }
        }

        return null;
    }
}
