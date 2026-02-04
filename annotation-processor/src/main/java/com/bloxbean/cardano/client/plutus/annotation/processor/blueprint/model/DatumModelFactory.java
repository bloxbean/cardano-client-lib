package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassificationResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassifier;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support.NameStrategy;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.Objects;

/**
 * Factory responsible for translating {@link BlueprintSchema} instances into
 * {@link DatumModel}s with classification metadata applied.
 */
public class DatumModelFactory {

    private final SchemaClassifier schemaClassifier;
    private final NameStrategy nameStrategy;

    public DatumModelFactory(NameStrategy nameStrategy) {
        this.nameStrategy = Objects.requireNonNull(nameStrategy, "nameStrategy cannot be null");
        this.schemaClassifier = new SchemaClassifier(nameStrategy);
    }

    public DatumModel create(String namespace, BlueprintSchema schema) {
        Objects.requireNonNull(schema, "schema cannot be null");

        String title = schema.getTitle();
        if (title == null || title.isEmpty())
            throw new IllegalArgumentException("Schema title cannot be null or empty");

        String className = nameStrategy.toClassName(title);
        SchemaClassificationResult classificationResult = schemaClassifier.classify(schema);

        return DatumModel.builder()
                .namespace(namespace)
                .name(className)
                .schema(schema)
                .classification(classificationResult)
                .build();
    }
}
