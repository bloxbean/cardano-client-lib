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
import static com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil.isBuiltInGenericContainer;
import static java.util.Collections.EMPTY_MAP;

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
                    : EMPTY_MAP;

            //Create Data classes
            for (Map.Entry<String, BlueprintSchema> definition: definitions.entrySet()) {
                String key = definition.getKey();

                // Skip built-in containers (List, Option, etc.); extract base type for domain-specific generics.
                // Handles both Aiken v1.0.x ($) and v1.1.x (<>) syntax.
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
     * <p><b>Purpose:</b> CIP-57 blueprints contain definitions for both built-in container types
     * (which map to Java's standard library) and domain-specific types (which need generated classes).
     * This method distinguishes between them and extracts the appropriate class name for generation.</p>
     *
     * <p><b>Aiken Compiler Generic Type Syntax:</b></p>
     * <ul>
     *   <li><b>Aiken v1.0.x (dollar syntax):</b> {@code "Interval$Int"}, {@code "List$ByteArray"}, {@code "Option$Credential"}</li>
     *   <li><b>Aiken v1.1.x (angle bracket syntax):</b> {@code "Interval<Int>"}, {@code "List<types/order/Action>"}, {@code "Option<StakeCredential>"}</li>
     * </ul>
     *
     * <p><b>Processing Strategy:</b></p>
     * <table border="1">
     *   <tr><th>Input Definition Key</th><th>Type Category</th><th>Return Value</th><th>Reason</th></tr>
     *   <tr><td>List$Int</td><td>Built-in container</td><td>null (skip)</td><td>Maps to java.util.List</td></tr>
     *   <tr><td>Option&lt;Credential&gt;</td><td>Built-in container</td><td>null (skip)</td><td>Handled by OptionDataTypeProcessor → Optional</td></tr>
     *   <tr><td>Tuple$Int_String</td><td>Built-in container</td><td>null (skip)</td><td>Maps to Tuple or PlutusData</td></tr>
     *   <tr><td>Data</td><td>Built-in abstract type</td><td>null (skip)</td><td>Maps to PlutusData (opaque)</td></tr>
     *   <tr><td>Interval$Int</td><td>Domain-specific</td><td>"Interval"</td><td>Generate typed Interval class</td></tr>
     *   <tr><td>aiken/interval/IntervalBound&lt;Int&gt;</td><td>Domain-specific</td><td>"aiken/interval/IntervalBound"</td><td>Generate typed IntervalBound class</td></tr>
     *   <tr><td>cardano/transaction/ValidityRange</td><td>Non-generic</td><td>"cardano/transaction/ValidityRange"</td><td>Generate ValidityRange class</td></tr>
     * </table>
     *
     * <p><b>Built-in Containers (skipped):</b> List, Option, Optional, Tuple, Pair, Map, Dict, Data, Redeemer</p>
     *
     * <p><b>Domain-Specific Types (generated):</b> Interval, IntervalBound, IntervalBoundType, ValidityRange, custom user types</p>
     *
     * <p><b>Examples:</b></p>
     * <pre>
     * // Built-in containers → null (skip generation)
     * resolveDefinitionKeyForClassGeneration("List$Int")              → null
     * resolveDefinitionKeyForClassGeneration("Option&lt;Credential&gt;")    → null
     * resolveDefinitionKeyForClassGeneration("Tuple$Int_String")      → null
     * resolveDefinitionKeyForClassGeneration("Data")                  → null
     *
     * // Domain-specific generics → base type (generate typed class)
     * resolveDefinitionKeyForClassGeneration("Interval$Int")          → "Interval"
     * resolveDefinitionKeyForClassGeneration("IntervalBound&lt;Int&gt;")    → "IntervalBound"
     * resolveDefinitionKeyForClassGeneration("aiken/interval/Interval$Int") → "aiken/interval/Interval"
     *
     * // Non-generic types → as-is (generate class)
     * resolveDefinitionKeyForClassGeneration("ValidityRange")         → "ValidityRange"
     * resolveDefinitionKeyForClassGeneration("types/order/Action")    → "types/order/Action"
     * </pre>
     *
     * <p><b>Why This Matters:</b></p>
     * <ul>
     *   <li><b>Type Safety:</b> Domain-specific types generate typed classes (e.g., {@code Interval} instead of {@code PlutusData})</li>
     *   <li><b>No Redundancy:</b> Built-in containers don't generate conflicting classes</li>
     *   <li><b>Aiken Version Support:</b> Handles both v1.0.x ({@code $}) and v1.1.x ({@code <>}) syntax uniformly</li>
     *   <li><b>Real-world Impact:</b> SundaeSwap V2 "Interval$Int" generates typed {@code Interval} class for type-safe field access</li>
     * </ul>
     *
     * @param definitionKey the blueprint definition key (e.g., "Interval$Int", "Option&lt;Credential&gt;", "types/order/Action")
     * <p>Package-private for testing.</p>
     *
     * @return the resolved key for class generation, or {@code null} if this definition should be skipped
     * @see #isBuiltInGenericContainer(String) for built-in container detection logic
     */
    String resolveDefinitionKeyForClassGeneration(String definitionKey) {
        String processKey = definitionKey;

        // Check if this is a generic type instantiation (contains $ or < or >)
        boolean isGenericInstantiation = definitionKey.contains("<")
            || definitionKey.contains(">")
            || definitionKey.contains("$");

        if (isGenericInstantiation) {
            // Extract base type name before $ or < (e.g., "Interval$Int" → "Interval")
            String baseTypeName = definitionKey;

            int dollarIndex = baseTypeName.indexOf('$');
            if (dollarIndex > 0) {
                baseTypeName = baseTypeName.substring(0, dollarIndex);
            }

            int angleIndex = baseTypeName.indexOf('<');
            if (angleIndex > 0) {
                baseTypeName = baseTypeName.substring(0, angleIndex);
            }

            // Extract simple name (last segment after /) for container detection
            String simpleName = baseTypeName.contains("/")
                ? baseTypeName.substring(baseTypeName.lastIndexOf('/') + 1)
                : baseTypeName;

            // Skip built-in containers; they map to Java types or are handled by specialized processors
            if (isBuiltInGenericContainer(simpleName)) {
                return null;
            }

            // Domain-specific types: use base type (e.g., "Interval$Int" → "Interval" class)
            processKey = baseTypeName;
        }

        return processKey;
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
