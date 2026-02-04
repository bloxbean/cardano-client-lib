package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.classifier;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of schema classification containing the resolved category and
 * any metadata (enum values, interface name) required downstream during generation.
 */
public class SchemaClassificationResult {
    private final SchemaClassification classification;
    private final List<String> enumValues;
    private final String interfaceName;

    private SchemaClassificationResult(Builder builder) {
        this.classification = builder.classification;
        this.enumValues = builder.enumValues != null ? List.copyOf(builder.enumValues) : List.of();
        this.interfaceName = builder.interfaceName;
    }

    public SchemaClassification getClassification() {
        return classification;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public boolean isSkippable() {
        return classification == SchemaClassification.ALIAS
                || classification == SchemaClassification.OPTION
                || classification == SchemaClassification.PAIR_ALIAS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SchemaClassificationResult alias() {
        return builder().classification(SchemaClassification.ALIAS).build();
    }

    public static SchemaClassificationResult option() {
        return builder().classification(SchemaClassification.OPTION).build();
    }

    public static SchemaClassificationResult pairAlias() {
        return builder().classification(SchemaClassification.PAIR_ALIAS).build();
    }

    public static SchemaClassificationResult enumType(List<String> enumValues) {
        Objects.requireNonNull(enumValues, "enumValues cannot be null");
        return builder()
                .classification(SchemaClassification.ENUM)
                .enumValues(enumValues)
                .build();
    }

    public static SchemaClassificationResult interfaceType(String interfaceName) {
        return builder()
                .classification(SchemaClassification.INTERFACE)
                .interfaceName(interfaceName)
                .build();
    }

    public static SchemaClassificationResult classType() {
        return builder().classification(SchemaClassification.CLASS).build();
    }

    public static SchemaClassificationResult unknown() {
        return builder().classification(SchemaClassification.UNKNOWN).build();
    }

    public static class Builder {
        private SchemaClassification classification = SchemaClassification.UNKNOWN;
        private List<String> enumValues = Collections.emptyList();
        private String interfaceName;

        public Builder classification(SchemaClassification classification) {
            this.classification = classification;
            return this;
        }

        public Builder enumValues(List<String> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public Builder interfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        public SchemaClassificationResult build() {
            return new SchemaClassificationResult(this);
        }
    }
}
