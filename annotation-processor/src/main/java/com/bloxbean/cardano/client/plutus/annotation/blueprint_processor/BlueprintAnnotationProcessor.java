package com.bloxbean.cardano.client.plutus.annotation.blueprint_processor;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.*;
import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.File;
import java.util.*;

@AutoService(Processor.class)
@Slf4j
public class BlueprintAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private List<TypeElement> typeElements = new ArrayList<>();
    private Blueprint annotation;
    private ValidatorProcessor validatorProcessor;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<String>();
        annotataions.add(Blueprint.class.getCanonicalName());
        return annotataions;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("Processing Blueprint annotation");
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    typeElements.add(typeElement);

                }
            }
        }

        for(TypeElement typeElement : typeElements) {
            annotation = typeElement.getAnnotation(Blueprint.class);
            if (annotation == null) {
                log.error("Blueprint annotation not found for class {}", typeElement.getSimpleName());
                return false;
            } else {
                validatorProcessor = new ValidatorProcessor(annotation, processingEnv);
            }

            File blueprintFile = getFile();

            if (blueprintFile == null || !blueprintFile.exists()) {
                log.error("Blueprint file {} not found", annotation.fileInRessources());
                return false;
            }
            PlutusContractBlueprint plutusContractBlueprint = PlutusBlueprintLoader.loadBlueprint(blueprintFile);

            for (Validator validator : plutusContractBlueprint.getValidators()) {
                validatorProcessor.processValidator(validator);
            }
        }
        return true;
    }

    private File getFile() {
        File blueprintFile = null;
        if(!annotation.file().isEmpty())
            blueprintFile = new File(annotation.file());
        if(!annotation.fileInRessources().isEmpty())
            blueprintFile = JavaFileUtil.getFileFromRessourcers(annotation.fileInRessources());
        if(blueprintFile == null || !blueprintFile.exists()) {
            log.error("Blueprint file {} not found", annotation.file());
            return null;
        }
        return blueprintFile;
    }


    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
