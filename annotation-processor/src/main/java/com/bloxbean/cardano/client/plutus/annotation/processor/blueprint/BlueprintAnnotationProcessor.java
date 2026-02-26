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

            // Detect and resolve type aliases (e.g., PaymentCredential → Credential)
            // before generating classes, so alias definitions are removed and all
            // $ref pointers redirect to the canonical type.
            Map<String, String> typeAliases = detectTypeAliases(definitions);
            if (!typeAliases.isEmpty()) {
                resolveTypeAliases(definitions, plutusContractBlueprint.getValidators(), typeAliases);
            }

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
     * Detects type aliases among blueprint definitions.
     *
     * <p>Two definitions are considered aliases when they share the same namespace and have
     * structurally identical {@code anyOf} variants (same titles, constructor indices, and
     * field {@code $ref} values). For example, in SundaeSwap V3, {@code PaymentCredential}
     * is an alias for {@code Credential} — both have identical VerificationKey/Script variants.</p>
     *
     * @param definitions the blueprint definitions map
     * @return a map from alias definition key to canonical definition key
     */
    Map<String, String> detectTypeAliases(Map<String, BlueprintSchema> definitions) {
        Map<String, String> aliases = new LinkedHashMap<>();
        // signature → first (canonical) definition key
        Map<String, String> signatureToCanonical = new LinkedHashMap<>();

        for (String key : new TreeSet<>(definitions.keySet())) {
            BlueprintSchema schema = definitions.get(key);
            if (schema.getAnyOf() == null || schema.getAnyOf().size() <= 1) {
                continue;
            }

            String ns = getNamespaceFromReferenceKey(key);
            String signature = computeAnyOfSignature(ns, schema.getAnyOf());

            String existing = signatureToCanonical.get(signature);
            if (existing != null) {
                aliases.put(key, existing);
                log.debug("Type alias detected: {} -> {}", key, existing);
            } else {
                signatureToCanonical.put(signature, key);
            }
        }
        return aliases;
    }

    /**
     * Computes a structural signature for a list of anyOf variants, used to detect
     * type aliases. The signature includes namespace, variant titles, constructor
     * indices, and field $ref values.
     */
    private String computeAnyOfSignature(String namespace, List<BlueprintSchema> anyOf) {
        List<String> variantSignatures = new ArrayList<>();
        for (BlueprintSchema variant : anyOf) {
            StringBuilder sb = new StringBuilder();
            sb.append(variant.getTitle() != null ? variant.getTitle() : "");
            sb.append("@").append(variant.getIndex());
            if (variant.getFields() != null) {
                for (BlueprintSchema field : variant.getFields()) {
                    sb.append(":").append(field.getRef() != null ? field.getRef() : "");
                }
            }
            variantSignatures.add(sb.toString());
        }
        Collections.sort(variantSignatures);

        return namespace + "|" + String.join(",", variantSignatures);
    }

    /**
     * Resolves type aliases by removing alias definitions and rewriting all {@code $ref}
     * pointers that reference aliases to point to the canonical definitions instead.
     *
     * @param definitions the mutable definitions map
     * @param validators  the list of validators whose schemas also need rewriting
     * @param aliases     map from alias definition key to canonical definition key
     */
    void resolveTypeAliases(Map<String, BlueprintSchema> definitions,
                            List<Validator> validators,
                            Map<String, String> aliases) {
        // Build $ref rewrite map: "#/definitions/cardano~1address~1PaymentCredential" → "#/definitions/cardano~1address~1Credential"
        Map<String, String> refRewrites = new HashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String aliasRef = "#/definitions/" + entry.getKey().replace("/", "~1");
            String canonicalRef = "#/definitions/" + entry.getValue().replace("/", "~1");
            refRewrites.put(aliasRef, canonicalRef);
        }

        // Remove alias definitions
        for (String aliasKey : aliases.keySet()) {
            definitions.remove(aliasKey);
        }

        // Rewrite $ref in all remaining definitions
        for (BlueprintSchema schema : definitions.values()) {
            rewriteRefs(schema, refRewrites);
        }

        // Rewrite $ref in all validator schemas (datum, redeemer, parameters)
        if (validators != null) {
            for (Validator validator : validators) {
                if (validator.getDatum() != null && validator.getDatum().getSchema() != null) {
                    rewriteRefs(validator.getDatum().getSchema(), refRewrites);
                }
                if (validator.getRedeemer() != null && validator.getRedeemer().getSchema() != null) {
                    rewriteRefs(validator.getRedeemer().getSchema(), refRewrites);
                }
                if (validator.getParameters() != null) {
                    for (BlueprintDatum param : validator.getParameters()) {
                        if (param.getSchema() != null) {
                            rewriteRefs(param.getSchema(), refRewrites);
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively rewrites {@code $ref} strings in a schema tree. Uses an identity set
     * to handle object-graph cycles created by {@link BlueprintSchema#copyFrom} sharing
     * sub-schema objects between definitions.
     */
    private void rewriteRefs(BlueprintSchema schema, Map<String, String> refRewrites) {
        rewriteRefs(schema, refRewrites, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void rewriteRefs(BlueprintSchema schema, Map<String, String> refRewrites,
                              Set<BlueprintSchema> visited) {
        if (schema == null || !visited.add(schema)) return;

        // Rewrite this schema's $ref if it matches an alias
        if (schema.getRef() != null && refRewrites.containsKey(schema.getRef())) {
            schema.setRef(refRewrites.get(schema.getRef()));
        }

        // Recurse into composite schema properties
        rewriteRefsList(schema.getAnyOf(), refRewrites, visited);
        rewriteRefsList(schema.getOneOf(), refRewrites, visited);
        rewriteRefsList(schema.getAllOf(), refRewrites, visited);
        rewriteRefsList(schema.getNotOf(), refRewrites, visited);
        rewriteRefsList(schema.getFields(), refRewrites, visited);
        rewriteRefsList(schema.getItems(), refRewrites, visited);

        rewriteRefs(schema.getKeys(), refRewrites, visited);
        rewriteRefs(schema.getValues(), refRewrites, visited);
        rewriteRefs(schema.getLeft(), refRewrites, visited);
        rewriteRefs(schema.getRight(), refRewrites, visited);
    }

    private void rewriteRefsList(List<BlueprintSchema> schemas,
                                 Map<String, String> refRewrites,
                                 Set<BlueprintSchema> visited) {
        if (schemas == null) return;

        for (BlueprintSchema s : schemas) {
            rewriteRefs(s, refRewrites, visited);
        }
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
