package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassification;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassificationResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModel;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModelFactory;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
import com.bloxbean.cardano.client.plutus.annotation.processor.exception.BlueprintGenerationException;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.DefaultNamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.SourceWriter;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.Data;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.Tuple;
import com.squareup.javapoet.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public FieldSpecProcessor(Blueprint annotation,
                              ProcessingEnvironment processingEnv,
                              GeneratedTypesRegistry generatedTypesRegistry,
                              SharedTypeLookup sharedTypeLookup) {
        this.annotation = annotation;
        this.processingEnv = processingEnv;
        this.nameStrategy = new DefaultNamingStrategy();
        this.packageResolver = new PackageResolver();
        this.datumModelFactory = new DatumModelFactory(nameStrategy);
        this.sourceWriter = new SourceWriter(processingEnv);
        this.generatedTypesRegistry = generatedTypesRegistry;
        this.sharedTypeLookup = sharedTypeLookup;
        this.dataTypeProcessUtil = new DataTypeProcessUtil(this, annotation, nameStrategy, packageResolver, sharedTypeLookup);
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
    private String extractBaseType(final String key) {
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

        if (sharedTypeLookup.lookup(ns, schema).isPresent()) {
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
        String interfaceName = null;
        if (classification.getClassification() == SchemaClassification.INTERFACE) {
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            createDatumInterface(datumModel.getNamespace(), datumModel.getName(), schema);
            interfaceName = datumModel.getName();
        }

        Tuple<String, List<BlueprintSchema>> allFields = FieldSpecProcessor.collectAllFields(schema);
        for (BlueprintSchema innerSchema : allFields._2) {
            String outerClassName = datumModel.getName();
            if (outerClassName == null || outerClassName.isEmpty()) {
                continue;
            }

            createDatumFieldSpec(datumModel.getNamespace(), interfaceName, innerSchema, outerClassName);
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
        Optional<ClassName> sharedType = sharedTypeLookup.lookup(ns, schema);
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
     * Creates a Datum interface for a given schema. This is used when a schema has anyOf &gt; 1
     *
     * @param ns            namespace or package suffix
     * @param dataClassName name of the interface
     * @param schema        the blueprint schema to create an interface for
     * @return ClassName of the interface
     */
    public ClassName createDatumInterface(String ns, String dataClassName, BlueprintSchema schema) {
        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).build();

        String className = nameStrategy.toClassName(dataClassName);

        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(constrAnnotationBuilder);

        TypeSpec build = interfaceBuilder.build();
        String pkg = getPackageName(ns);

        if (generatedTypesRegistry.markGenerated(pkg, className)) {
            sourceWriter.write(pkg, build, className);
        }

        return ClassName.get(pkg, className);
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
        Optional<ClassName> sharedType = sharedTypeLookup.lookup(ns, schema);

        if (sharedType.isPresent()) {
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

        String finalNS = BlueprintUtil.getNamespaceFromReference(schema.getRef());;
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

    private String getPackageName(String ns) {
        return packageResolver.getModelPackage(annotation, ns);
    }

}
