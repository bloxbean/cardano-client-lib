package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.*;
import com.bloxbean.cardano.client.util.Tuple;
import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.util.*;

@AutoService(Processor.class)
@Slf4j
public class BlueprintAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private List<TypeElement> typeElements = new ArrayList<>();
    private ValidatorProcessor validatorProcessor;
    private FieldSpecProcessor fieldSpecProcessor;

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
        log.debug("Processing Blueprint annotation");

        typeElements = getTypeElementsWithAnnotations(annotations, roundEnv);

        for(TypeElement typeElement : typeElements) {
            Blueprint annotation = typeElement.getAnnotation(Blueprint.class);
            ExtendWith[] extendWiths = typeElement.getAnnotationsByType(ExtendWith.class);
            ExtendWith extendWith = null;

            if (extendWiths != null && extendWiths.length > 1) {
                error(typeElement, "Multiple ExtendWith annotations are not supported. Only one ExtendWith annotation is allowed.");
                return false;
            } else if (extendWiths != null && extendWiths.length == 1) {
                extendWith = extendWiths[0];
            }

            if (annotation == null) {
                error(typeElement, "Blueprint annotation not found for class %s", typeElement.getSimpleName());
                return false;
            } else {
                validatorProcessor = new ValidatorProcessor(annotation, extendWith, processingEnv);
                fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv);
            }


            File blueprintFile = getFileFromAnnotation(annotation);
            if (blueprintFile == null || !blueprintFile.exists()) {
                error(typeElement, "Blueprint file %s not found", annotation.fileInResources());
                return false;
            }
            PlutusContractBlueprint plutusContractBlueprint;
            try {
                plutusContractBlueprint = PlutusBlueprintLoader.loadBlueprint(blueprintFile);
            } catch (Exception e) {
                error(typeElement, "Error processing blueprint file %s", blueprintFile.getAbsolutePath(), e);
                return false;
            }


            Map<String, BlueprintSchema> definitions = plutusContractBlueprint.getDefinitions() != null? plutusContractBlueprint.getDefinitions()
                    : Collections.EMPTY_MAP;
            //Create Data classes
            for(Map.Entry<String, BlueprintSchema> definition: definitions.entrySet()) {
                String key = definition.getKey();
                String[] titleTokens = key.split("\\/");
                String ns = "";

                if (titleTokens.length > 1) {
                    ns = titleTokens[0];
                }

                BlueprintSchema schema = definition.getValue();
                String dataClassName = schema.getTitle();
                if(dataClassName == null || dataClassName.isEmpty()) {
                    continue;
                }

                dataClassName = JavaFileUtil.firstUpperCase(dataClassName);

                String interfaceName = null;
                //For anyOf > 1, create an interface, if size == 1, create a class
                //TODO -- What about allOf ??
                if (schema.getAnyOf() != null && schema.getAnyOf().size() > 1) {
                    log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
                    //More than one constructor. So let's create an interface
                    fieldSpecProcessor.createDatumInterface(ns, dataClassName);
                    interfaceName = dataClassName;
                }

                Tuple<String, List<BlueprintSchema>> allFields = FieldSpecProcessor.collectAllFields(schema);
                for(BlueprintSchema innerSchema: allFields._2) {
                    dataClassName = schema.getTitle();
                    if(dataClassName == null || dataClassName.isEmpty()) {
                        continue;
                    }

                    dataClassName = JavaFileUtil.firstUpperCase(dataClassName);
                    fieldSpecProcessor.createDatumFieldSpec(ns, interfaceName,  innerSchema, "", dataClassName);
                }
            }


            for (Validator validator : plutusContractBlueprint.getValidators()) {
                validatorProcessor.processValidator(validator, plutusContractBlueprint.getPreamble().getPlutusVersion());
            }

        }
        return true;
    }

    private List<TypeElement> getTypeElementsWithAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<TypeElement> elementsList = new ArrayList<>();
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    elementsList.add(typeElement);

                }
            }
        }
        return elementsList;
    }

    private File getFileFromAnnotation(Blueprint annotation) {
        File blueprintFile = null;
        if(!annotation.file().isEmpty())
            blueprintFile = new File(annotation.file());
        if(!annotation.fileInResources().isEmpty())
            blueprintFile = getFileFromRessourcers(annotation.fileInResources());
        if(blueprintFile == null || !blueprintFile.exists()) {
            log.error("Blueprint file %s not found", annotation.file());
            return null;
        }
        return blueprintFile;
    }

    public File getFileFromRessourcers(String s) {
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", s);
            return new File(resource.toUri());
        } catch (Exception e) {
            return null;
        }
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }
}
