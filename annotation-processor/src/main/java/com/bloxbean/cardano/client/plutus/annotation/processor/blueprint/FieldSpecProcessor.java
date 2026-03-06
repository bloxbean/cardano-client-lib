package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassification;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassificationResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassifier;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModel;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModelFactory;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.ClassDefinitionGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.ConverterCodeGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.FieldTypeDetector;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.DefaultNamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.SourceWriter;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.blueprint.registry.LookupContext;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.Tuple;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.*;

import static com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil.isAbstractPlutusDataType;
import static com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil.isBuiltInGenericContainer;
import static com.bloxbean.cardano.client.plutus.annotation.processor.util.CodeGenUtil.createMethodSpecsForGetterSetters;

/**
 * Builds datum model types/interfaces and corresponding field specifications from
 * blueprint schema fragments.
 */
@Slf4j
public class FieldSpecProcessor {

    private final ProcessingEnvironment processingEnv;
    private final Blueprint annotation;
    private final DataTypeProcessUtil dataTypeProcessUtil;
    private final NamingStrategy nameStrategy;
    private final PackageResolver packageResolver;
    private final DatumModelFactory datumModelFactory;
    private final SourceWriter sourceWriter;
    private final GeneratedTypesRegistry generatedTypesRegistry;
    private final SharedTypeLookup sharedTypeLookup;
    private final SharedTypeConverterGenerator sharedTypeConverterGenerator;
    private final LookupContext lookupContext;

    private Map<String, BlueprintSchema> definitions;

    public FieldSpecProcessor(Blueprint annotation,
                              ProcessingEnvironment processingEnv,
                              GeneratedTypesRegistry generatedTypesRegistry,
                              SharedTypeLookup sharedTypeLookup) {
        this(annotation, processingEnv, generatedTypesRegistry, sharedTypeLookup, LookupContext.EMPTY);
    }

    public FieldSpecProcessor(Blueprint annotation,
                              ProcessingEnvironment processingEnv,
                              GeneratedTypesRegistry generatedTypesRegistry,
                              SharedTypeLookup sharedTypeLookup,
                              LookupContext lookupContext) {
        this.annotation = annotation;
        this.processingEnv = processingEnv;
        this.nameStrategy = new DefaultNamingStrategy();
        this.packageResolver = new PackageResolver();
        this.datumModelFactory = new DatumModelFactory(nameStrategy);
        this.sourceWriter = new SourceWriter(processingEnv);
        this.generatedTypesRegistry = generatedTypesRegistry;
        this.sharedTypeLookup = sharedTypeLookup;
        this.sharedTypeConverterGenerator = new SharedTypeConverterGenerator();
        this.lookupContext = lookupContext;
        this.dataTypeProcessUtil = new DataTypeProcessUtil(this, annotation, nameStrategy, packageResolver, sharedTypeLookup, lookupContext);
    }

    /**
     * Sets the blueprint definitions map and pre-scans interface types.
     */
    public void setDefinitions(Map<String, BlueprintSchema> definitions) {
        this.definitions = definitions;
        preScanInterfaces(definitions);
    }

    /**
     * Pre-scans all blueprint definitions to identify and register interface types in the
     * {@link GeneratedTypesRegistry}. Called eagerly from {@link #setDefinitions}.
     *
     * @param definitions all blueprint definitions (key → schema)
     */
    private void preScanInterfaces(Map<String, BlueprintSchema> definitions) {
        SchemaClassifier classifier = new SchemaClassifier(nameStrategy);
        for (Map.Entry<String, BlueprintSchema> entry : definitions.entrySet()) {
            BlueprintSchema schema = entry.getValue();
            if (schema == null) continue;

            // Resolve title the same way createDatumClass does
            String title = resolveTitleFromDefinitionKey(entry.getKey(), schema);
            if (title == null || title.isEmpty()) continue;

            schema.setTitle(title);

            // Check shared type — skip if shared
            Optional<ClassName> sharedType = sharedTypeLookup.lookup(null, schema);
            if (sharedType.isPresent()) continue;

            SchemaClassificationResult result = classifier.classify(schema);
            if (result.getClassification() == SchemaClassification.INTERFACE) {
                String ns = com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil
                        .getNamespaceFromReferenceKey(entry.getKey());
                String pkg = getPackageName(ns);
                String className = nameStrategy.toClassName(title);
                generatedTypesRegistry.markInterface(pkg, className);
                log.debug("Pre-scanned interface type: {}.{}", pkg, className);
            }
        }
    }

    /**
     * Resolves the class name for a blueprint definition, preferring the definition key
     * and falling back to the schema's title field only if the key yields nothing.
     *
     * <p><b>CIP-57 Compliance:</b> Per CIP-57 spec, definition keys are the technical identifiers,
     * while the "title" field is OPTIONAL and used for "user interface decoration". This method
     * prefers the definition key for consistency and predictability.</p>
     *
     * <p><b>Rationale for preferring definition keys:</b></p>
     * <ul>
     *   <li>CIP-57 specifies keys as technical identifiers (titles are for UI decoration)</li>
     *   <li>More predictable: class name always derived from key structure</li>
     *   <li>Consistent: all 45+ test blueprints have title matching key's last segment</li>
     *   <li>Abstract types (e.g., "Data") are skipped anyway (no class generated)</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>Key "types/order/Action", title "Action" → returns "Action" (from key)</li>
     *   <li>Key "types/custom/Data", no title → returns "Data" (from key)</li>
     *   <li>Key "Int", title "Int" → returns "Int" (from key)</li>
     *   <li>Key null, title "Action" → returns "Action" (fallback to title)</li>
     * </ul>
     *
     * @param definitionKey the blueprint definition key (e.g., "types/custom/Data")
     * @param schema the blueprint schema that may or may not have a title
     * @return the resolved class name (from key or title), or null if neither available
     */
    protected String resolveTitleFromDefinitionKey(String definitionKey, BlueprintSchema schema) {
        if (schema == null) {
            return null;
        }

        // PREFER definition key (CIP-57 technical identifier)
        String keyClassName = BlueprintUtil.getClassNameFromReferenceKey(definitionKey);
        if (keyClassName != null && !keyClassName.isEmpty()) {
            return keyClassName;
        }

        // FALLBACK to title only if key yields nothing
        return schema.getTitle();
    }

    /**
     * Extracts base type from a definition key by stripping generic parameters.
     * Handles both $ syntax (older Aiken) and &lt;&gt; syntax (newer Aiken).
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>"Interval$Int" → "Interval"</li>
     *   <li>"Option&lt;T&gt;" → "Option"</li>
     *   <li>"List&lt;Option&lt;Int&gt;&gt;" → "List"</li>
     *   <li>"cardano/transaction/ValidityRange" → "cardano/transaction/ValidityRange" (unchanged)</li>
     * </ul>
     *
     * @param key the definition key that may contain generic parameters
     * @return the base type without generic parameters
     */
    String extractBaseType(final String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }

        // Strip generic parameters: "Interval$Int" → "Interval"
        int dollarIndex = key.indexOf('$');
        if (dollarIndex > 0) {
            return key.substring(0, dollarIndex);
        }

        // Strip angle bracket generics: "Option<T>" → "Option"
        int angleIndex = key.indexOf('<');
        if (angleIndex > 0) {
            return key.substring(0, angleIndex);
        }

        return key;
    }

    /**
     * Resolves class name from a $ref value, preferring the definition key
     * and falling back to the referenced schema's title.
     *
     * <p><b>CIP-57 Compliance:</b> This method implements the CIP-57 principle that definition keys are
     * the technical identifiers (source of truth), while titles are optional UI decoration.
     * For generic instantiations like "Interval$Int" or "Option&lt;T&gt;", it extracts the base type.</p>
     *
     * <p><b>Rationale:</b> When a field references another definition via $ref, we must use the
     * definition key from that $ref to determine the class name, NOT the variant's title.
     * This ensures consistency with the recent refactoring (commit cf953ed1) that established
     * "definition keys as source of truth per CIP-57 specification".</p>
     *
     * <p><b>Generic Type Handling:</b> For generic instantiations, this method extracts the base type
     * and checks if it's a built-in container or domain-specific type:</p>
     * <ul>
     *   <li><b>Built-in containers</b> (List, Option, Tuple, etc.): Returns null to signal PlutusData fallback</li>
     *   <li><b>Domain-specific types</b> (Interval, IntervalBound, etc.): Returns base type for typed class</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *   <li>$ref "#/definitions/cardano~1transaction~1ValidityRange" → extracts "ValidityRange"</li>
     *   <li>$ref "#/definitions/aiken~1interval~1Interval$Int" → extracts "Interval" (domain-specific, typed class)</li>
     *   <li>$ref "#/definitions/Option&lt;Credential&gt;" → returns null (built-in container, PlutusData)</li>
     *   <li>$ref "#/definitions/List&lt;Int&gt;" → returns null (built-in container, PlutusData)</li>
     *   <li>$ref null, schema title "Action" → returns "Action" (fallback)</li>
     * </ul>
     *
     * @param ref the $ref value (e.g., "#/definitions/aiken~1interval~1Interval$Int")
     * @param refSchema the resolved schema from the reference (may have title)
     * @return the resolved class name (base type for domain-specific generics), or null to signal PlutusData fallback
     */
    protected String resolveClassNameFromRef(String ref, BlueprintSchema refSchema) {
        if (ref == null || ref.isEmpty()) {
            return refSchema != null ? refSchema.getTitle() : null;
        }

        // Extract definition key from $ref (normalizes JSON pointer syntax)
        String defKey = BlueprintUtil.normalizedReference(ref);

        // For generic instantiations, extract base type
        String baseType = extractBaseType(defKey);

        // Get the simple name (last segment after /) for built-in container check
        String simpleName = baseType.contains("/")
            ? baseType.substring(baseType.lastIndexOf('/') + 1)
            : baseType;

        // Return null for built-in containers (List, Option, etc.) to signal PlutusData fallback
        if (isBuiltInGenericContainer(simpleName)) {
            return null;
        }

        // Prefer class name from definition key (base type for domain-specific generics)
        String keyClassName = BlueprintUtil.getClassNameFromReferenceKey(baseType);
        if (keyClassName != null && !keyClassName.isEmpty()) {
            return keyClassName;
        }

        // Fallback: title from referenced schema
        return refSchema != null ? refSchema.getTitle() : null;
    }

    /**
     * Creates a Datum class for a given schema
     * If the schema has anyOf &gt; 1, it creates an interface and a class for each of the anyOf
     * This is the main method called to create Datum classes from BlueprintAnnotationProcessor
     * @param ns            namespace or package suffix
     * @param definitionKey the definition key from the blueprint (used as fallback if title is missing)
     * @param schema        Definition schema to create Datum class
     * @throws BlueprintGenerationException if title cannot be resolved or datum model creation fails
     */
    public void createDatumClass(String ns, String definitionKey, BlueprintSchema schema) {
        if (schema == null) {
            return;
        }

        // Resolve title (use schema's title or extract from definition key)
        String title = resolveTitleFromDefinitionKey(definitionKey, schema);
        if (title == null || title.isEmpty()) {
            throw BlueprintGenerationException.forDefinition(definitionKey,
                    "Unable to resolve title from schema or definition key. " +
                    "Per CIP-57, either the schema must have a 'title' field or the definition key must be parseable.");
        }

        // Set the resolved title on the schema for downstream processing
        schema.setTitle(title);

        Optional<ClassName> sharedType = sharedTypeLookup.lookup(ns, schema, lookupContext);
        if (sharedType.isPresent()) {
            generateSharedTypeConverter(sharedType.get(), schema);
            return;
        }

        DatumModel datumModel;
        try {
            datumModel = datumModelFactory.create(ns, schema);
        } catch (BlueprintGenerationException ex) {
            // Re-throw with definition key context if not already present
            if (ex.hasDefinitionContext()) {
                throw ex;
            }
            throw BlueprintGenerationException.forDefinition(definitionKey, ex.getMessage());
        }

        createDatumClass(datumModel);
    }

    public void createDatumClass(DatumModel datumModel) {
        if (datumModel == null) {
            return;
        }

        SchemaClassificationResult classification = datumModel.getClassificationResult();
        if (classification.isSkippable()) {
            return;
        }

        if (classification.getClassification() == SchemaClassification.ENUM) {
            createEnum(datumModel, classification);
            return;
        }

        var schema = datumModel.getSchema();
        Tuple<String, List<BlueprintSchema>> allFields = FieldSpecProcessor.collectAllFields(schema);

        if (classification.getClassification() == SchemaClassification.INTERFACE) {
            // Build interface with nested variant classes and converters
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            String interfaceName = datumModel.getName();
            TypeSpec.Builder interfaceBuilder = buildInterfaceTypeSpecBuilder(interfaceName);

            String pkg = getPackageName(datumModel.getNamespace());
            String className = nameStrategy.toClassName(interfaceName);

            // Track this as an interface type for nested converter resolution
            generatedTypesRegistry.markInterface(pkg, className);

            ConverterCodeGenerator converterGen = new ConverterCodeGenerator(processingEnv);
            List<ClassDefinition> variantClassDefs = new ArrayList<>();

            for (BlueprintSchema innerSchema : allFields._2) {
                if (interfaceName == null || interfaceName.isEmpty()) continue;
                TypeSpec variantTypeSpec = buildVariantTypeSpec(datumModel.getNamespace(), interfaceName, innerSchema);
                if (variantTypeSpec != null) {
                    interfaceBuilder.addType(variantTypeSpec);

                    // Build ClassDefinition for the variant and generate its converter
                    String variantName = nameStrategy.toClassName(innerSchema.getTitle());
                    int alternative = innerSchema.getIndex();
                    List<FieldSpec> variantFields = extractNonStaticFields(variantTypeSpec);

                    ClassDefinition variantClassDef = ClassDefinition.forNestedVariant(
                            pkg, className, variantName, alternative);
                    variantClassDef.setFields(mapFieldSpecs(variantFields));
                    variantClassDefs.add(variantClassDef);

                    try {
                        TypeSpec converterTypeSpec = converterGen.generate(variantClassDef);
                        // Add STATIC modifier — required for classes nested inside an interface
                        interfaceBuilder.addType(converterTypeSpec.toBuilder()
                                .addModifiers(Modifier.STATIC).build());
                    } catch (Exception e) {
                        log.error("Failed to generate nested converter for " + className + "." + variantName, e);
                    }
                }
            }

            // Generate dispatch converter for the interface
            if (!variantClassDefs.isEmpty()) {
                ClassDefinition interfaceClassDef = ClassDefinition.forInterface(pkg, className);
                try {
                    TypeSpec dispatchConverterSpec = converterGen.generateInterfaceConverter(
                            interfaceClassDef, variantClassDefs);
                    // Add STATIC modifier — required for classes nested inside an interface
                    interfaceBuilder.addType(dispatchConverterSpec.toBuilder()
                            .addModifiers(Modifier.STATIC).build());
                } catch (Exception e) {
                    log.error("Failed to generate dispatch converter for " + className, e);
                }
            }

            if (generatedTypesRegistry.markGenerated(pkg, className)) {
                sourceWriter.write(pkg, interfaceBuilder.build(), className);
            }
        } else {
            // Non-interface: keep existing flat-class behavior (single anyOf or no anyOf)
            for (BlueprintSchema innerSchema : allFields._2) {
                String outerClassName = datumModel.getName();
                if (outerClassName == null || outerClassName.isEmpty()) continue;
                createDatumFieldSpec(datumModel.getNamespace(), null, innerSchema, outerClassName);
            }
        }
    }

    private void createEnum(DatumModel datumModel, SchemaClassificationResult classification) {
        String pkg = getPackageName(datumModel.getNamespace());
        String enumClassName = datumModel.getName();

        var enumConstrBuilder = TypeSpec.enumBuilder(enumClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Constr.class).build());

        for (String value : classification.getEnumValues()) {
            enumConstrBuilder.addEnumConstant(value);
        }

        if (generatedTypesRegistry.markGenerated(pkg, enumClassName)) {
            sourceWriter.write(pkg, enumConstrBuilder.build(), enumClassName);
        }
    }

    /**
     * Get the inner class name for a given schema. This is currently used to find the generic types for list and map
     *
     * @param ns     namespace or package suffix
     * @param schema schema to get the inner class name
     * @return ClassName of the inner class
     */
    public ClassName getInnerDatumClass(String ns, BlueprintSchema schema) {
        Optional<ClassName> sharedType = sharedTypeLookup.lookup(ns, schema, lookupContext);
        if (sharedType.isPresent()) {
            return sharedType.get();
        }

        // Check if this is an abstract PlutusData type (e.g., "Data", "Redeemer")
        if (isAbstractPlutusDataType(schema)) {
            return ClassName.get(PlutusData.class);
        }

        String dataClassName = schema.getTitle();

        if (dataClassName != null) {
            dataClassName = nameStrategy.toClassName(dataClassName);
        }

        // Use namespace from $ref when processing a field reference; else use the passed-in namespace
        String finalNS = (schema.getRef() != null)
            ? BlueprintUtil.getNamespaceFromReference(schema.getRef())
            : ns;
        String pkg = getPackageName(finalNS);

        //For anyOf > 1, create an interface, if size == 1, create a class
        //TODO -- What about allOf ??
        if (schema.getAnyOf() != null && schema.getAnyOf().size() > 1) {
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            //More than one constructor. So it's an interface

            return ClassName.get(pkg, dataClassName);
        }

        if (schema.getAnyOf() != null && schema.getAnyOf().size() == 1) {
            // Use $ref key (CIP-57 source of truth) when present, otherwise use title
            if (schema.getRef() != null) {
                dataClassName = resolveClassNameFromRef(schema.getRef(), schema);
            }

            // Fallback: use parent schema's title (already resolved from definition key in createDatumClass)
            // NOT the variant's title, which may differ from the definition name
            if (dataClassName == null || dataClassName.isEmpty()) {
                dataClassName = schema.getTitle();
            }

            if (dataClassName != null) {
                dataClassName = nameStrategy.toClassName(dataClassName);

                return ClassName.get(pkg, dataClassName);
            }
        }

        //Otherwise
        return ClassName.get(pkg, dataClassName);
    }

    /**
     * Collects all fields from a schema including the ones from allOf, anyOf, oneOf, noneOf and returns them as a list
     *
     * @param schema schema to collect fields from
     * @return list of all fields
     */
    public static Tuple<String, List<BlueprintSchema>> collectAllFields(BlueprintSchema schema) {
        List<BlueprintSchema> toFields = new ArrayList<>();

        String javaDoc = "";
        if (schema.getAllOf() != null) {
            toFields.addAll(schema.getAllOf());
            javaDoc = "AllOf";
        } else if (schema.getAnyOf() != null) {
            toFields.addAll(schema.getAnyOf());
            javaDoc = "AnyOf";
        } else if (schema.getOneOf() != null) {
            toFields.addAll(schema.getOneOf());
            javaDoc = "OneOf";
        } else {
            toFields.add(schema);
        }

        return new Tuple<>(javaDoc, toFields);
    }

    /**
     * Builds a TypeSpec.Builder for a Datum interface. Does not write the file —
     * the caller adds nested variant classes and then writes.
     *
     * @param dataClassName name of the interface
     * @return TypeSpec.Builder for the interface
     */
    TypeSpec.Builder buildInterfaceTypeSpecBuilder(String dataClassName) {
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).build();

        String className = nameStrategy.toClassName(dataClassName);

        return TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(constrAnnotationBuilder);
    }

    /**
     * Builds a TypeSpec for a variant class that will be nested inside an interface.
     * The variant is a static abstract class implementing Data&lt;InterfaceName.VariantName&gt;
     * and the parent interface.
     *
     * @param ns            namespace or package suffix
     * @param interfaceName the parent interface name
     * @param schema        the variant schema
     * @return TypeSpec for the variant, or null if schema has no dataType
     */
    TypeSpec buildVariantTypeSpec(String ns, String interfaceName, BlueprintSchema schema) {
        if (schema.getDataType() == null) {
            return null; // No dataType means no class generation needed
        }

        // Process the variant's actual fields directly (not via collectAllFields which would
        // wrap the variant schema itself and create self-referential enum fields for empty variants)
        List<FieldSpec> fields = new ArrayList<>();
        if (schema.getFields() != null) {
            for (BlueprintSchema field : schema.getFields()) {
                if (field.getDataType() != null) {
                    fields.addAll(createFieldSpecForDataTypes(ns, "AnyOf", field, "", field.getTitle()));
                } else {
                    Tuple<FieldSpec, ClassName> tuple = createDatumFieldSpec(ns, null, field, field.getTitle());
                    fields.add(tuple._1);
                }
            }
        }

        AnnotationSpec constrAnnotation = AnnotationSpec.builder(Constr.class)
                .addMember("alternative", "$L", schema.getIndex()).build();

        String className = nameStrategy.toClassName(schema.getTitle());
        String pkg = getPackageName(ns);
        String interfaceClassName = nameStrategy.toClassName(interfaceName);

        // Use nested ClassName: InterfaceName.VariantName for correct Data<T> parameterization
        ClassName datumClass = ClassName.get(pkg, interfaceClassName, className);
        ClassName dataInterface = ClassName.get(Data.class);
        ParameterizedTypeName parameterizedInterface = ParameterizedTypeName.get(dataInterface, datumClass);

        // Parent interface type
        ClassName interfaceTypeName = ClassName.get(pkg, interfaceClassName);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addFields(fields)
                .addSuperinterface(parameterizedInterface)
                .addSuperinterface(interfaceTypeName)
                .addMethods(createMethodSpecsForGetterSetters(fields, false))
                .addAnnotation(constrAnnotation);

        log.debug("---------- Inside buildVariantTypeSpec ---------");
        log.debug("Package: " + pkg);
        log.debug("Interface: " + interfaceClassName);
        log.debug("Variant: " + className);

        return classBuilder.build();
    }

    /**
     * Creates a Datum field for a given schema. It also creates a Datum class for the schema if it's not already created
     *
     * @param ns            namespace or package suffix
     * @param interfaceName Optional interface name if the schema has anyOf &gt; 1
     * @param schema        schema to create Datum field
     * @param title         title of the schema
     * @return Tuple of FieldSpec and ClassName of the field
     */
    public Tuple<FieldSpec, ClassName> createDatumFieldSpec(String ns, String interfaceName, BlueprintSchema schema, String title) {
        Optional<ClassName> sharedType = sharedTypeLookup.lookup(ns, schema, lookupContext);

        if (sharedType.isPresent()) {
            generateSharedTypeConverter(sharedType.get(), schema);

            String fieldName = nameStrategy.firstLowerCase(nameStrategy.toCamelCase(title));

            FieldSpec fieldSpec = FieldSpec.builder(sharedType.get(), fieldName)
                    .addModifiers(Modifier.PRIVATE)
                    .build();

            return new Tuple<>(fieldSpec, sharedType.get());
        }

        String classNameString = nameStrategy.toClassName(title);
        // Pass the outer class name (definition title) for single anyOf cases
        TypeSpec redeemerJavaFile = createDatumTypeSpec(ns, interfaceName, schema, title);

        String className = redeemerJavaFile.name;

        log.debug("---------- Inside createDatumFieldSpec ---------");
        log.debug("ClasNameString : " + classNameString);
        log.debug("RedeemerJavaFile : " + redeemerJavaFile.name);

        if (schema.getRefSchema() != null) {
            // Check if the referenced schema is an abstract PlutusData type
            if (isAbstractPlutusDataType(schema.getRefSchema())) {
                ClassName plutusDataType = ClassName.get(PlutusData.class);

                String fieldName = title;
                fieldName = nameStrategy.firstLowerCase(nameStrategy.toCamelCase(fieldName));

                FieldSpec fieldSpec = FieldSpec.builder(plutusDataType, fieldName)
                        .addModifiers(Modifier.PRIVATE)
                        .build();

                return new Tuple<>(fieldSpec, plutusDataType);
            }

            // Resolve class name from $ref: null means built-in container → fall back to PlutusData
            String refClassName = resolveClassNameFromRef(schema.getRef(), schema.getRefSchema());

            if (refClassName == null) {
                ClassName plutusDataType = ClassName.get(PlutusData.class);

                String fieldName = title;
                fieldName = nameStrategy.firstLowerCase(nameStrategy.toCamelCase(fieldName));

                FieldSpec fieldSpec = FieldSpec.builder(plutusDataType, fieldName)
                        .addModifiers(Modifier.PRIVATE)
                        .build();

                return new Tuple<>(fieldSpec, plutusDataType);
            }

            className = refClassName;
            className = nameStrategy.toClassName(className);
        }

        String finalNS = BlueprintUtil.getNamespaceFromReference(schema.getRef());
        String pkg = getPackageName(finalNS);

        ClassName classNameType = ClassName.get(pkg, className);

        String fieldName = title; // + (schema.getDataType() == BlueprintDatatype.constructor ? String.valueOf(schema.getIndex()) : "") ;
        fieldName = nameStrategy.firstLowerCase(nameStrategy.toCamelCase(fieldName));

        FieldSpec fieldSpec = FieldSpec.builder(classNameType, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .build();

        return new Tuple<>(fieldSpec, classNameType);
    }

    private TypeSpec createDatumTypeSpec(String ns, String interfaceName, BlueprintSchema schema, String outerClassName) {
        Tuple<String, List<BlueprintSchema>> allInnerSchemas = FieldSpecProcessor.collectAllFields(schema);

        List<FieldSpec> fields = null;
        if (schema.getDataType() != null) {
            fields = createFieldSpecForDataTypes(ns, allInnerSchemas._1, allInnerSchemas._2);
        } else {
            fields = new ArrayList<>();
        }

        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        // For single anyOf (interfaceName == null), use outerClassName (definition title)
        // For multiple anyOf (interface), use schema title (variant title)
        String title = (interfaceName == null && outerClassName != null) ? outerClassName : schema.getTitle();
        String className = nameStrategy.toClassName(title);

        String pkg = getPackageName(ns);

        ClassName datumClass = ClassName.get(pkg, className);
        ClassName DataClazz = ClassName.get(Data.class);
        ParameterizedTypeName parameterizedInterface = ParameterizedTypeName.get(DataClazz, datumClass);

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addFields(fields)
                .addSuperinterface(parameterizedInterface)
                .addMethods(createMethodSpecsForGetterSetters(fields, false))
                .addAnnotation(constrAnnotationBuilder);

        if (interfaceName != null && !interfaceName.isEmpty()) {
            ClassName interfaceTypeName = ClassName.get(pkg, interfaceName);
            classBuilder.addSuperinterface(interfaceTypeName);
        }

        TypeSpec build = classBuilder.build();

        log.debug("---------- Inside createDatumTypeSpec ---------");
        log.debug("Package: " + pkg);
        log.debug("Class: " + className);
        log.debug("Data type: " + schema.getDataType());

        log.debug("\n");

        if (schema.getDataType() != null) {
            if (generatedTypesRegistry.markGenerated(pkg, className)) {
                sourceWriter.write(pkg, build, className);
            }
        } else {
            log.debug("Datatype is null. Looks like we don't need to create a class for this schema");
        }

        return build;
    }

    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, List<BlueprintSchema> schemas) {
        return dataTypeProcessUtil.generateFieldSpecs(ns, javaDoc, schemas);
    }

    public List<FieldSpec> createFieldSpecForDataTypes(String ns, String javaDoc, BlueprintSchema schema, String className, String alternativeName) {
        return dataTypeProcessUtil.generateFieldSpecs(ns, javaDoc, schema, className, alternativeName);
    }

    /**
     * Generates a thin converter wrapper for a shared/registered type so that generated
     * code can reference {@code XConverter} even though the model class was not generated.
     */
    void generateSharedTypeConverter(ClassName sharedType, BlueprintSchema schema) {
        SharedTypeConverterGenerator.SharedTypeKind kind = SharedTypeConverterGenerator.kindOf(schema);

        String converterPkg = sharedType.packageName() + ".converter";
        String converterName = sharedType.simpleName() + "Converter";

        if (!generatedTypesRegistry.markGenerated(converterPkg, converterName)) {
            // Already generated — skip
            return;
        }

        TypeSpec converterSpec = sharedTypeConverterGenerator.generate(sharedType, kind);
        sourceWriter.write(converterPkg, converterSpec, converterName);

        log.debug("Generated shared type converter: {}.{}", converterPkg, converterName);
    }

    /**
     * Extracts non-static field specs from a built TypeSpec (the variant's fields).
     */
    private static List<FieldSpec> extractNonStaticFields(TypeSpec typeSpec) {
        List<FieldSpec> fields = new ArrayList<>();
        for (FieldSpec fs : typeSpec.fieldSpecs) {
            if (!fs.modifiers.contains(Modifier.STATIC)) {
                fields.add(fs);
            }
        }
        return fields;
    }

    /**
     * Converts a list of JavaPoet FieldSpecs to model Fields, detecting types
     * via {@link FieldTypeDetector} and resolving converter FQNs for CONSTRUCTOR types.
     */
    private List<Field> mapFieldSpecs(List<FieldSpec> fieldSpecs) {
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < fieldSpecs.size(); i++) {
            fields.add(mapFieldSpec(fieldSpecs.get(i), i));
        }
        return fields;
    }

    private Field mapFieldSpec(FieldSpec fs, int index) {
        FieldType ft = FieldTypeDetector.fromTypeName(fs.type);
        if (ft == null) {
            ft = new FieldType();
            ft.setFqTypeName(fs.type.toString());
            ft.setType(Type.CONSTRUCTOR);
            ft.setJavaType(new JavaType(fs.type.toString(), true));
            if (fs.type instanceof ClassName cn) {
                boolean isIface = generatedTypesRegistry.isInterface(cn.packageName(), cn.simpleName());
                ft.setConverterClassFqn(ClassDefinitionGenerator.resolveConverterFqn(cn, isIface));
            }
        } else {
            FieldTypeDetector.resolveConverterFqns(ft, generatedTypesRegistry::isInterface);
        }

        String getter;
        if (Type.BOOL.equals(ft.getType()) && JavaType.BOOLEAN.equals(ft.getJavaType())) {
            getter = "is" + capitalize(fs.name);
        } else {
            getter = "get" + capitalize(fs.name);
        }

        return Field.builder()
                .name(fs.name)
                .index(index)
                .fieldType(ft)
                .hashGetter(true)
                .getterName(getter)
                .build();
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String getPackageName(String ns) {
        return packageResolver.getModelPackage(annotation, ns);
    }

}
