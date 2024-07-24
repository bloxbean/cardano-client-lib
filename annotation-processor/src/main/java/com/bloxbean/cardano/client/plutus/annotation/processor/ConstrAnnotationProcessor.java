package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor for Plutus Constr annotation. This annotation processor generates the Serilizer and Deserializer code
 * for the Constr annotated classes.
 */

@AutoService(Processor.class)
@Slf4j
public class ConstrAnnotationProcessor extends AbstractProcessor {
    private Messager messager;
    private ClassDefinitionGenerator classDefinitionGenerator;
    private ConverterCodeGenerator serializerCodeGenerator;
    private DataImplGenerator dataImplGenerator;
    private List<TypeElement> typeElements = new ArrayList<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        this.classDefinitionGenerator = new ClassDefinitionGenerator(processingEnv);
        this.serializerCodeGenerator = new ConverterCodeGenerator(processingEnv);
        this.dataImplGenerator = new DataImplGenerator(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<String>();
        annotataions.add(Constr.class.getCanonicalName());
        return annotataions;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
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
                    ClassDefinition classDefinition = classDefinitionGenerator.getClassDefinition(typeElement);

                    //Generate converter class
                    try {
                        TypeSpec typeSpec = serializerCodeGenerator.generate(classDefinition);
                        JavaFileUtil.createJavaFile(classDefinition.getPackageName(), typeSpec, classDefinition.getName(), processingEnv);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("Failed to generate serialization class: " + e.getMessage(), e);
                        error(typeElement, "Failed to generate serialization class for " + typeElement.getQualifiedName());
                    }

                    //Generate Data Impl class
                    try {
                        TypeSpec typeSpec = dataImplGenerator.generate(classDefinition);
                        JavaFileUtil.createJavaFile(classDefinition.getPackageName(), typeSpec, classDefinition.getDataClassName() + "Impl", processingEnv);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("Failed to generate DataImpl class: " + e.getMessage(), e);
                        error(typeElement, "Failed to generate DataImpl class for " + typeElement.getQualifiedName());
                    }

                }
            }
        }
        return true;
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
