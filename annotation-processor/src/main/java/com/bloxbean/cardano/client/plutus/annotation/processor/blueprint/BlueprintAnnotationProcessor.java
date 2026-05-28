package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.ExtendWith;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookupFactory;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintLoader;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusContractBlueprint;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
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

import static com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil.*;

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
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(Blueprint.class.getCanonicalName());

        return annotations;
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(SharedTypeLookupFactory.OPTION_ENABLE_REGISTRY);
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
                    : Collections.emptyMap();

            LookupContext lookupContext = sharedTypeLookup.resolveHints(typeElement);

            fieldSpecProcessor = new FieldSpecProcessor(annotation, processingEnv, generatedTypesRegistry, sharedTypeLookup, lookupContext);
            validatorProcessor = new ValidatorProcessor(annotation, extendWith, processingEnv, generatedTypesRegistry, sharedTypeLookup, lookupContext);

            //Create Data classes
            for (Map.Entry<String, BlueprintSchema> definition: definitions.entrySet()) {
                String key = definition.getKey();

                // Skip built-in containers (List, Option, etc.); extract base type for domain-specific generics.
                String processKey = resolveDefinitionKeyForClassGeneration(key);
                if (processKey == null) continue;

                String ns = getNamespaceFromReferenceKey(processKey);

                try {
                    fieldSpecProcessor.createDatumClass(ns, processKey, definition.getValue());
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

    /**
     * Resolves a blueprint definition key to determine if and how a Java class should be generated.
     *
     * <p>CIP-57 blueprints contain definitions for both built-in container types
     * (which map to Java's standard library) and domain-specific types (which need generated classes).
     * Generic type instantiations use angle-bracket syntax (e.g. {@code Interval<Int>},
     * {@code List<types/order/Action>}). Built-in containers (List, Option, Tuple, Pair, Map,
     * Dict, Data, Redeemer, Quartet, Quintet) return {@code null}; domain-specific types
     * return the base type name (e.g. {@code "Interval<Int>"} → {@code "Interval"}).</p>
     *
     * @param definitionKey the blueprint definition key
     * @return the resolved key for class generation, or {@code null} if this definition should be skipped
     */
    String resolveDefinitionKeyForClassGeneration(String definitionKey) {
        String processKey = definitionKey;

        int angleIndex = definitionKey.indexOf('<');
        if (angleIndex > 0) {
            String baseTypeName = definitionKey.substring(0, angleIndex);

            String simpleName = baseTypeName.contains("/")
                ? baseTypeName.substring(baseTypeName.lastIndexOf('/') + 1)
                : baseTypeName;

            if (isBuiltInGenericContainer(simpleName)) {
                return null;
            }

            processKey = baseTypeName;
        }

        return processKey;
    }

    private List<TypeElement> getTypeElementsWithAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<TypeElement> elementsList = new ArrayList<>();
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement typeElement) {
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
        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "", s);
            File f = new File(resource.toUri());
            if (f.exists()) {
                return f;
            }
        } catch (Exception e) {
            // not in CLASS_PATH, try CLASS_OUTPUT
        }

        try {
            FileObject resource = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", s);
            File f = new File(resource.toUri());
            if (f.exists()) {
                return f;
            }
        } catch (Exception e) {
            // not in CLASS_OUTPUT either
        }

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
