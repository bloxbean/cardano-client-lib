package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.math.BigInteger;
import java.util.*;

@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
@Slf4j
public class ConstrAnnotationProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;
    private ClassDefinitionGenerator classDefinitionGenerator;
    private ConverterCodeGenerator serializerCodeGenerator;
    private List<TypeElement> typeElements = new ArrayList<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        this.classDefinitionGenerator = new ClassDefinitionGenerator(processingEnv);
        this.serializerCodeGenerator = new ConverterCodeGenerator(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<String>();
        annotataions.add(Constr.class.getCanonicalName());
        return annotataions;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    typeElements.add(typeElement);
                }
            }
        }

        this.classDefinitionGenerator.setTypeElements(typeElements);

        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    try {
                        ClassDefinition classDefinition = classDefinitionGenerator.getClassDefinition(typeElement);

                        log.debug(classDefinition.toString());

                        TypeSpec typeSpec = serializerCodeGenerator.generate(classDefinition);
                        JavaFile javaFile = JavaFile.builder(classDefinition.getPackageName(), typeSpec)
                                .build();

                        JavaFileObject builderFile = processingEnv.getFiler()
                                .createSourceFile(classDefinition.getName());
                        Writer writer = builderFile.openWriter();
                        javaFile.writeTo(writer);
                        writer.close();

                        if (log.isDebugEnabled())
                            javaFile.writeTo(System.out);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("Failed to generate serialization class: " + e.getMessage(), e);
                        error(typeElement, "Failed to generate serialization class for " + typeElement.getQualifiedName());
                    }
                }
            }
        }
        return true;
    }

    private CodeBlock createBodyForMap(String fieldName, String fieldOrGetterName, TypeName keyType, TypeName valueType) {
        CodeBlock fieldSerializeBody = CodeBlock.builder()
                .addStatement("$T $LMapPlutusData = $T.builder().build();", MapPlutusData.class, fieldName, MapPlutusData.class)
                .beginControlFlow("for(var entry: obj.$L.entrySet())", fieldOrGetterName)
                .addStatement("$LMapPlutusData.put($L, $L)", fieldName,
                        toPlutusDataCodeBlock(keyType, "entry.getKey()"),
                        toPlutusDataCodeBlock(valueType, "entry.getValue()")
                )
                .endControlFlow()
                .addStatement("constr.getData().add($LMapPlutusData)", fieldName)
                .build();

        return fieldSerializeBody;
    }

    private String toPlutusDataCodeBlock(TypeName itemType, String fieldOrGetterName) {
        if (itemType.equals(TypeName.get(Long.class)) || itemType.equals(TypeName.LONG)
                || itemType.equals(TypeName.get(BigInteger.class)) || itemType.equals(TypeName.get(Integer.class))
                || itemType.equals(TypeName.INT) || itemType.equals(TypeName.get(String.class))
                || itemType.equals(byte[].class)) {
            return "toPlutusData(" + fieldOrGetterName + ")";
        } else {
            return String.format("new %sPlutusDataConverter().serialize(%s)", itemType.toString(), fieldOrGetterName);
        }
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
                        executableElement.getReturnType().toString().equals(variableElement.asType().toString())) {
                    return executableElement;
                }
            }
        }

        return null;
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    private boolean isAssignableToMap(TypeMirror typeMirror) {
        Types typeUtils = processingEnv.getTypeUtils();

        // Check if the type is assignable to java.util.Map
        if (typeUtils.isAssignable(typeMirror, typeUtils.getDeclaredType(elementUtils.getTypeElement("java.util.Map"))))
            return true;
        else
            return false;
    }

    private boolean isAssignableToList(TypeMirror typeMirror) {
        Types typeUtils = processingEnv.getTypeUtils();

        // Check if the type is assignable to java.util.List
        if (typeUtils.isAssignable(typeMirror, typeUtils.getDeclaredType(elementUtils.getTypeElement("java.util.List"))))
            return true;
        else
            return false;
    }
}
