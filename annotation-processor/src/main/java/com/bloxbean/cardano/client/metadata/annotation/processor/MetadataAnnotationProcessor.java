package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.annotation.MetadataType;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    private MetadataFieldExtractor fieldExtractor;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        generator = new MetadataConverterGenerator();
        fieldExtractor = new MetadataFieldExtractor(processingEnv, messager);
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
                if (!(element instanceof TypeElement typeElement)) continue;
                processType(typeElement);
            }
        }

        return true;
    }

    private void processType(TypeElement typeElement) {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).toString();
        String className = typeElement.getSimpleName().toString();

        boolean isRecord = fieldExtractor.isRecord(typeElement);
        List<MetadataFieldInfo> fields;
        List<MetadataConverterGenerator.RecordComponentInfo> allComponents = List.of();

        if (isRecord) {
            MetadataFieldExtractor.RecordExtractionResult result = fieldExtractor.extractRecordFields(typeElement);
            fields = result.fields();
            allComponents = result.allComponents();
        } else {
            boolean hasLombok = fieldExtractor.detectLombok(typeElement);
            fieldExtractor.validateNoArgConstructor(typeElement, hasLombok);
            fields = fieldExtractor.extractFields(typeElement, hasLombok);
        }

        long label = typeElement.getAnnotation(MetadataType.class).label();

        try {
            TypeSpec typeSpec = generator.generate(packageName, className, fields, label, isRecord, allComponents);
            String converterName = className + MetadataConverterGenerator.CONVERTER_SUFFIX;
            JavaFileUtil.createJavaFile(packageName, typeSpec, converterName, processingEnv);
        } catch (Exception e) {
            log.error("Failed to generate MetadataConverter for " + className, e);
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to generate MetadataConverter for " + className + ": " + e.getMessage(), typeElement);
        }
    }

}
