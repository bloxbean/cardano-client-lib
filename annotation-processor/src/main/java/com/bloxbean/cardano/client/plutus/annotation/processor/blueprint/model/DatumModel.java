package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.model;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier.SchemaClassificationResult;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.Objects;

/**
 * Internal representation of a resolved blueprint datum definition including
 * namespace, original schema and classification metadata.
 */
public class DatumModel {

    private final String namespace;
    private final String name;
    private final BlueprintSchema schema;
    private final SchemaClassificationResult classificationResult;

    private DatumModel(Builder builder) {
        this.namespace = builder.namespace;
        this.name = builder.name;
        this.schema = builder.schema;
        this.classificationResult = builder.classificationResult;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public BlueprintSchema getSchema() {
        return schema;
    }

    public SchemaClassificationResult getClassificationResult() {
        return classificationResult;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String namespace;
        private String name;
        private BlueprintSchema schema;
        private SchemaClassificationResult classificationResult;

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(BlueprintSchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder classification(SchemaClassificationResult classificationResult) {
            this.classificationResult = classificationResult;
            return this;
        }

        public DatumModel build() {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(schema, "schema cannot be null");
            Objects.requireNonNull(classificationResult, "classificationResult cannot be null");
            return new DatumModel(this);
        }
    }
}
