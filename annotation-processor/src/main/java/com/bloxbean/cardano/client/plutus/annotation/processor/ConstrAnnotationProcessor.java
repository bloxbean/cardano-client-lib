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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

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
        List<TypeElement> interfaceElements = new ArrayList<>();

        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    typeElements.add(typeElement);

                    if (typeElement.getKind().isInterface()) {
                        interfaceElements.add(typeElement);
                    }
                }
            }
        }

        this.classDefinitionGenerator.setTypeElements(typeElements);

        //Interface map
        Map<TypeElement, List<ClassDefinition>> interfaceToConstructorsMap = new HashMap<>();

        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;

                    if (typeElement.getKind().isInterface()) {
                        continue;
                    }

                    ClassDefinition classDefinition = classDefinitionGenerator.getClassDefinition(typeElement);

                    //check if the class implements any known Constr interface
                    var interfaces = typeElement.getInterfaces();
                    for (TypeMirror typeMirror : interfaces) {
                        TypeElement interfaceElement = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
                        if(interfaceElements.contains(interfaceElement)) {
                            List<ClassDefinition> constructors = interfaceToConstructorsMap.get(interfaceElement);
                            if(constructors == null) {
                                constructors = new ArrayList<>();
                                interfaceToConstructorsMap.put(interfaceElement, constructors);
                            }
                            constructors.add(classDefinition);
                        }
                    }

                    //Handle enum first
                    //Only need to create Converter for now
                    if (classDefinition.isEnum()) {
                        serializerCodeGenerator.generateEnumConverter(classDefinition)
                                        .ifPresent(typeSpec -> {
                                            JavaFileUtil.createJavaFile(classDefinition.getConverterPackageName(), typeSpec,
                                                    classDefinition.getConverterClassName(), processingEnv);
                                        });
                    } else {
                        //Generate converter class
                        try {
                            TypeSpec typeSpec = serializerCodeGenerator.generate(classDefinition);
                            JavaFileUtil.createJavaFile(classDefinition.getConverterPackageName(), typeSpec, classDefinition.getConverterClassName(), processingEnv);
                        } catch (Exception e) {
                            e.printStackTrace();
                            log.error("Failed to generate serialization class: " + e.getMessage(), e);
                            error(typeElement, "Failed to generate serialization class for " + typeElement.getQualifiedName());
                        }
                    }

                    //Generate Data Impl class
                    try {
                        TypeSpec typeSpec = dataImplGenerator.generate(classDefinition);
                        JavaFileUtil.createJavaFile(classDefinition.getImplPackageName(), typeSpec, classDefinition.getImplClassName(), processingEnv);
                    } catch (Exception e) {
                        e.printStackTrace();
                        log.error("Failed to generate DataImpl class: " + e.getMessage(), e);
                        error(typeElement, "Failed to generate DataImpl class for " + typeElement.getQualifiedName());
                    }

                }
            }
        }

        //Implement Converter for interfaces
        for (Map.Entry<TypeElement, List<ClassDefinition>> entry : interfaceToConstructorsMap.entrySet()) {
            TypeElement interfaceElement = entry.getKey();
            List<ClassDefinition> constructors = entry.getValue();

            ClassDefinition classDefinition = classDefinitionGenerator.getClassDefinition(interfaceElement);

            //Generate converter class
            try {
                TypeSpec typeSpec = serializerCodeGenerator.generateInterfaceConverter(classDefinition, constructors);
                JavaFileUtil.createJavaFile(classDefinition.getConverterPackageName(), typeSpec, classDefinition.getConverterClassName(), processingEnv);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Failed to generate converter class: " + e.getMessage(), e);
                error(interfaceElement, "Failed to generate converter class for " + interfaceElement.getQualifiedName());
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
