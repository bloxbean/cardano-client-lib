package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookupFactory;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.*;
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

import static com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil.getNamespaceFromReferenceKey;

/**
 * Annotation processor that consumes {@link com.bloxbean.cardano.client.plutus.annotation.Blueprint}
 * types and emits validator/datum classes according to CIP-57 blueprint metadata.
 */
@AutoService(Processor.class)
@Slf4j
public class BlueprintAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private List<TypeElement> typeElements = new ArrayList<>();
    private ValidatorProcessor validatorProcessor;
    private FieldSpecProcessor fieldSpecProcessor;
    private final GeneratedTypesRegistry generatedTypesRegistry = new GeneratedTypesRegistry();
    private SharedTypeLookup sharedTypeLookup;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        sharedTypeLookup = SharedTypeLookupFactory.create(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotataions = new LinkedHashSet<>();
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
                generatedTypesRegistry.clear();
                validatorProcessor = new ValidatorProcessor(annotation, extendWith, processingEnv, generatedTypesRegistry, sharedTypeLookup);
                fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv, generatedTypesRegistry, sharedTypeLookup);
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
                e.printStackTrace();
                error(typeElement, "Error processing blueprint file %s", blueprintFile.getAbsolutePath(), e);
                return false;
            }


            Map<String, BlueprintSchema> definitions = plutusContractBlueprint.getDefinitions() != null? plutusContractBlueprint.getDefinitions()
                    : Collections.EMPTY_MAP;
            //Create Data classes
            for (Map.Entry<String, BlueprintSchema> definition: definitions.entrySet()) {
                String key = definition.getKey();

                // Skip generic type instantiations to prevent invalid class generation
                //
                // Aiken compiler generates blueprint definitions for generic type instantiations
                // in two different syntaxes depending on version:
                //   - Aiken v1.0.x (old):  "List$Int", "Option$ByteArray"
                //   - Aiken v1.1.x (new):  "List<Int>", "Option<types/order/Action>"
                //
                // These are NOT new type definitions - they're instantiations of built-in
                // generic containers (List, Option, Tuple, etc.) with specific type parameters.
                // Attempting to generate Java classes for these would cause:
                //   1. Invalid Java identifiers (class names can't contain < > $)
                //   2. NullPointerException in TypeSpec.classBuilder() when name parsing fails
                //   3. Naming conflicts (multiple "Option" classes for different type params)
                //   4. Code duplication (redundant with Java's Optional<T>, List<T>)
                //
                // Real-world examples from SundaeSwap blueprints:
                //   - Old syntax: "List$Tuple$Int_Option$types/order/SignedStrategyExecution_Int"
                //   - New syntax: "List<Tuple<<Int,Option<types/order/SignedStrategyExecution>,Int>>>"
                //
                // Skip condition checks for BOTH syntaxes to support all Aiken versions.
                if (key.contains("<") || key.contains(">") || key.contains("$")) {
                    continue;
                }

                String ns = getNamespaceFromReferenceKey(key);
                try {
                    fieldSpecProcessor.createDatumClass(ns, key, definition.getValue());
                } catch (BlueprintGenerationException e) {
                    error(typeElement, "Blueprint generation failed for definition '%s': %s", key, e.getMessage());
                    return false;
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

        if (!annotation.file().isEmpty()) {
            blueprintFile = new File(annotation.file());
        }

        if (!annotation.fileInResources().isEmpty()) {
            try {
                blueprintFile = getFileFromResources(annotation.fileInResources());
            } catch (BlueprintGenerationException e) {
                log.error("Blueprint file not found: {}", e.getMessage());
                return null;
            }
        }

        if (blueprintFile == null || !blueprintFile.exists()) {
            log.error("Blueprint file '{}' not found", annotation.file());
            if (blueprintFile != null) {
                JavaFileUtil.warn(processingEnv, null, "Trying to find blueprint file at " + blueprintFile.getAbsolutePath());
            }
            return null;
        }

        return blueprintFile;
    }

    public File getFileFromResources(String s) {
        // Try CLASS_PATH first
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", s);
            File f = new File(resource.toUri());
            if (f.exists()) {
                return f;
            }
        } catch (Exception e) {
            // Resource not in CLASS_PATH, will try CLASS_OUTPUT next
        }

        // Try CLASS_OUTPUT as fallback
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", s);
            File f = new File(resource.toUri());
            if (f.exists()) {
                return f;
            }
        } catch (Exception e) {
            // Resource not in CLASS_OUTPUT either
        }

        // Resource not found in either location
        throw new BlueprintGenerationException(
            String.format("Blueprint file '%s' not found in CLASS_PATH or CLASS_OUTPUT", s)
        );
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

}
