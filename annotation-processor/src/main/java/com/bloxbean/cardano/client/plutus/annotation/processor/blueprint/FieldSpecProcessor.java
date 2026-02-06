package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassification;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassificationResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModel;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model.DatumModelFactory;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.GeneratedTypesRegistry;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.DefaultNamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.SourceWriter;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
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
     * Creates a Datum class for a given schema
     * If the schema has anyOf &gt; 1, it creates an interface and a class for each of the anyOf
     * This is the main method called to create Datum classes from BlueprintAnnotationProcessor
     * @param ns     namespace or package suffix
     * @param schema Definition schema to create Datum class
     */
    public void createDatumClass(String ns, BlueprintSchema schema) {
        if (schema == null || schema.getTitle() == null || schema.getTitle().isEmpty()) {
            return;
        }

        if (sharedTypeLookup.lookup(ns, schema).isPresent()) {
            return;
        }

        DatumModel datumModel;
        try {
            datumModel = datumModelFactory.create(ns, schema);
        } catch (IllegalArgumentException ex) {
            return;
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

        String finalNS = BlueprintUtil.getNSFromReference(schema.getRef());
        String pkg = getPackageName(finalNS);

        //For anyOf > 1, create an interface, if size == 1, create a class
        //TODO -- What about allOf ??
        if (schema.getAnyOf() != null && schema.getAnyOf().size() > 1) {
            log.debug("Create interface as size > 1 : " + schema.getTitle() + ", size: " + schema.getAnyOf().size());
            //More than one constructor. So it's an interface

            return ClassName.get(pkg, dataClassName);
        }

        if (schema.getAnyOf() != null && schema.getAnyOf().size() == 1) {
            var anyOfSchema = schema.getAnyOf().get(0);
            dataClassName = anyOfSchema.getTitle();
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

        String className = nameStrategy.toClassName(dataClassName); // TODO, WHY IS THIS TODO?

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
        TypeSpec redeemerJavaFile = createDatumTypeSpec(ns, interfaceName, schema);

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

            String refTitle = schema.getRefSchema().getTitle();
            className = refTitle != null ? refTitle : className;
            className = nameStrategy.toClassName(className);
        }

        String finalNS = BlueprintUtil.getNSFromReference(schema.getRef());;
        String pkg = getPackageName(finalNS);

        ClassName classNameType = ClassName.get(pkg, className);

        String fieldName = title; // + (schema.getDataType() == BlueprintDatatype.constructor ? String.valueOf(schema.getIndex()) : "") ;
        fieldName = nameStrategy.firstLowerCase(nameStrategy.toCamelCase(fieldName));

        FieldSpec fieldSpec = FieldSpec.builder(classNameType, fieldName)
                .addModifiers(Modifier.PRIVATE)
                .build();

        return new Tuple<>(fieldSpec, classNameType);
    }

    private TypeSpec createDatumTypeSpec(String ns, String interfaceName, BlueprintSchema schema) {
        Tuple<String, List<BlueprintSchema>> allInnerSchemas = FieldSpecProcessor.collectAllFields(schema);

        List<FieldSpec> fields = null;
        if (schema.getDataType() != null) {
            fields = createFieldSpecForDataTypes(ns, allInnerSchemas._1, allInnerSchemas._2);
        } else {
            fields = new ArrayList<>();
        }

        AnnotationSpec constrAnnotationBuilder = AnnotationSpec.builder(Constr.class).addMember("alternative", "$L", schema.getIndex()).build();

        String title = schema.getTitle();
        String className = nameStrategy.toClassName(title); //TODO

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
