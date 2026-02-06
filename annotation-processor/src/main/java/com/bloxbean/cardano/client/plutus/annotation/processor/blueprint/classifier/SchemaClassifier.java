package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the schema classification logic previously embedded in
 * {@link com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.FieldSpecProcessor}.
 * Given a blueprint schema it determines the appropriate generation strategy.
 */
public class SchemaClassifier {

    private final NamingStrategy nameStrategy;

    public SchemaClassifier(NamingStrategy nameStrategy) {
        this.nameStrategy = Objects.requireNonNull(nameStrategy, "nameStrategy cannot be null");
    }

    public SchemaClassificationResult classify(BlueprintSchema schema) {
        if (schema == null)
            return SchemaClassificationResult.unknown();

        if (isAlias(schema))
            return SchemaClassificationResult.alias();

        if (isOptionType(schema))
            return SchemaClassificationResult.option();

        if (isPairAlias(schema))
            return SchemaClassificationResult.pairAlias();

        if (isEnum(schema))
            return SchemaClassificationResult.enumType(extractEnumValues(schema));

        if (isInterface(schema)) {
            String interfaceName = nameStrategy.toClassName(schema.getTitle());
            return SchemaClassificationResult.interfaceType(interfaceName);
        }

        return SchemaClassificationResult.classType();
    }

    private boolean isAlias(BlueprintSchema schema) {
        if (schema.getDataType() != null && schema.getDataType().isPrimitiveType()
                && (schema.getItems() == null || schema.getItems().isEmpty())
                && (schema.getFields() == null || schema.getFields().isEmpty())
                && (schema.getAnyOf() == null || schema.getAnyOf().isEmpty())
                && (schema.getAllOf() == null || schema.getAllOf().isEmpty())
                && (schema.getOneOf() == null || schema.getOneOf().isEmpty())
                && (schema.getNotOf() == null || schema.getNotOf().isEmpty())) {
            return true;
        }
        return false;
    }

    private boolean isOptionType(BlueprintSchema schema) {
        String title = schema.getTitle();
        if (title == null)
            return false;

        boolean optionTitle = "Option".equals(title) || "Optional".equals(title);
        if (!optionTitle)
            return false;

        if (schema.getAnyOf() == null || schema.getAnyOf().size() != 2)
            return false;

        BlueprintSchema someSchema = schema.getAnyOf().get(0);
        BlueprintSchema noneSchema = schema.getAnyOf().get(1);

        if (someSchema.getTitle() == null || noneSchema.getTitle() == null)
            return false;

        if (!"Some".equals(someSchema.getTitle()) || !"None".equals(noneSchema.getTitle()))
            return false;

        if (someSchema.getFields() == null || someSchema.getFields().size() != 1)
            return false;

        if (noneSchema.getFields() != null && noneSchema.getFields().size() != 0)
            return false;

        return true;
    }

    private boolean isPairAlias(BlueprintSchema schema) {
        String title = schema.getTitle();
        if (title == null)
            return false;
        return "Pair".equals(title) && schema.getDataType() == BlueprintDatatype.pair;
    }

    private boolean isEnum(BlueprintSchema schema) {
        if (schema.getAnyOf() == null || !(schema.getAnyOf().size() > 1))
            return false;

        if (schema.getFields() != null && schema.getFields().size() != 0)
            return false;

        for (BlueprintSchema anyOfSchema : schema.getAnyOf()) {
            if (BlueprintDatatype.constructor != anyOfSchema.getDataType())
                return false;

            if (anyOfSchema.getTitle() == null || anyOfSchema.getTitle().isEmpty())
                return false;

            if (anyOfSchema.getFields() != null && anyOfSchema.getFields().size() > 0)
                return false;
        }

        return true;
    }

    private List<String> extractEnumValues(BlueprintSchema schema) {
        List<String> enumValues = new ArrayList<>();
        if (schema.getAnyOf() == null)
            return enumValues;

        for (BlueprintSchema anyOfSchema : schema.getAnyOf()) {
            enumValues.add(anyOfSchema.getTitle());
        }
        return enumValues;
    }

    private boolean isInterface(BlueprintSchema schema) {
        return schema.getAnyOf() != null && schema.getAnyOf().size() > 1;
    }
}
