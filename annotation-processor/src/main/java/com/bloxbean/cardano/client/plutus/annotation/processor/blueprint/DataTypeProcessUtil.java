package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint;

import com.bloxbean.cardano.client.plutus.annotation.Blueprint;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.shared.SharedTypeLookup;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.PackageResolver;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.BlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.FieldSpec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.lang.model.element.Modifier;

/**
 * Dispatches schema field generation to datatype-specific processors while sharing
 * common naming and package resolution utilities.
 */
public class DataTypeProcessUtil {

    private final FieldSpecProcessor fieldSpecProcessor;
    private final Blueprint annotation;
    private final NamingStrategy nameStrategy;
    private final Map<BlueprintDatatype, DataTypeProcessor> processors;
    private final DataTypeProcessor plutusDataTypeProcessor;
    private final SharedTypeLookup sharedTypeLookup;

    public DataTypeProcessUtil(FieldSpecProcessor fieldSpecProcessor,
                               Blueprint annotation,
                               NamingStrategy nameStrategy,
                               PackageResolver packageResolver,
                               SharedTypeLookup sharedTypeLookup) {
        this.fieldSpecProcessor = fieldSpecProcessor;
        this.annotation = annotation;
        this.nameStrategy = nameStrategy;
        this.sharedTypeLookup = sharedTypeLookup;

        SchemaTypeResolver typeResolver = new SchemaTypeResolver(fieldSpecProcessor);
        this.processors = new EnumMap<>(BlueprintDatatype.class);
        registerProcessors(typeResolver, packageResolver);
        this.plutusDataTypeProcessor = new PlutusDataTypeProcessor(nameStrategy, typeResolver);
    }

    private void registerProcessors(SchemaTypeResolver typeResolver, PackageResolver packageResolver) {
        processors.put(BlueprintDatatype.bytes, new BytesDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.integer, new IntegerDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.bool, new BoolDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.string, new StringDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.list, new ListDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.map, new MapDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.option, new OptionDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.pair, new PairDataTypeProcessor(nameStrategy, typeResolver));
        processors.put(BlueprintDatatype.constructor, new ConstructorDataTypeProcessor(nameStrategy, typeResolver, fieldSpecProcessor, packageResolver, annotation));
    }

    public List<FieldSpec> generateFieldSpecs(String namespace, String javaDoc, List<BlueprintSchema> schemas) {
        List<FieldSpec> specs = new ArrayList<>();
        if (schemas == null)
            return specs;

        for (int index = 0; index < schemas.size(); index++) {
            BlueprintSchema schema = schemas.get(index);
            specs.addAll(generateFieldSpecs(namespace, javaDoc, schema, "", determineAlternativeName(schema, index)));
        }

        return specs;
    }

    public List<FieldSpec> generateFieldSpecs(String namespace,
                                              String javaDoc,
                                              BlueprintSchema schema,
                                              String className,
                                              String alternativeName) {
        if (schema == null)
            return List.of();

        String resolvedAlternativeName = alternativeName;
        if (resolvedAlternativeName == null || resolvedAlternativeName.isBlank()) {
            resolvedAlternativeName = determineAlternativeName(schema, 0);
        }

        var sharedType = sharedTypeLookup.lookup(namespace, schema);
        if (sharedType.isPresent()) {
            FieldSpec.Builder builder = FieldSpec.builder(sharedType.get(), resolveFieldName(schema, resolvedAlternativeName))
                    .addModifiers(Modifier.PRIVATE);
            if (javaDoc != null && !javaDoc.isBlank())
                builder.addJavadoc(javaDoc);
            return List.of(builder.build());
        }

        DataTypeProcessingContext context = new DataTypeProcessingContext(namespace, javaDoc, schema, className, resolvedAlternativeName);
        if (schema.getDataType() == null) {
            return plutusDataTypeProcessor.process(context);
        }

        DataTypeProcessor processor = processors.get(schema.getDataType());
        if (processor == null) {
            return List.of();
        }
        return processor.process(context);
    }

    private String determineAlternativeName(BlueprintSchema schema, int index) {
        if (schema == null)
            return "field" + index;

        String title = schema.getTitle();
        if (title != null && !title.isBlank())
            return title;

        String ref = schema.getRef();
        if (ref != null && !ref.isBlank()) {
            String normalized = BlueprintUtil.normalizedReference(ref);
            if (!normalized.isBlank()) {
                int lastSlash = normalized.lastIndexOf('/');
                String candidate = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
                if (!candidate.isBlank())
                    return candidate + index;
            }
        }

        if (schema.getDataType() != null)
            return schema.getDataType().name().toLowerCase(Locale.ROOT) + index;

        return "field" + index;
    }

    private String resolveFieldName(BlueprintSchema schema, String alternativeName) {
        String title = schema != null ? schema.getTitle() : null;
        if (title == null || title.isBlank())
            title = alternativeName;
        if (title == null || title.isBlank())
            title = "field";

        return nameStrategy.firstLowerCase(nameStrategy.toCamelCase(title));
    }

}
