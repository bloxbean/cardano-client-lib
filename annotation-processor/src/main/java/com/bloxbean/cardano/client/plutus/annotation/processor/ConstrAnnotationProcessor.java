package com.bloxbean.cardano.client.plutus.annotation.processor;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.ClassDefinition;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor for Plutus Constr annotation. This annotation processor generates the Serilizer and Deserializer code
 * for the Constr annotated classes.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
@Slf4j
public class ConstrAnnotationProcessor extends AbstractProcessor {
    private Messager messager;
    private ClassDefinitionGenerator classDefinitionGenerator;
    private ConverterCodeGenerator serializerCodeGenerator;
    private List<TypeElement> typeElements = new ArrayList<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
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

//                       log.debug(classDefinition.toString());

                        TypeSpec typeSpec = serializerCodeGenerator.generate(classDefinition);
                        JavaFile javaFile = JavaFile.builder(classDefinition.getPackageName(), typeSpec)
                                .build();

                        JavaFileObject builderFile = processingEnv.getFiler()
                                .createSourceFile(classDefinition.getName());
                        Writer writer = builderFile.openWriter();
                        javaFile.writeTo(writer);
                        writer.close();

//                        if (log.isTraceEnabled())
//                            javaFile.writeTo(System.out);
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

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
