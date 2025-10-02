package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.datatype;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Shared context passed to datatype processors containing namespace, schema and
 * naming information for a single field.
 */
public class DataTypeProcessingContext {
    private final String namespace;
    private final String javaDoc;
    private final BlueprintSchema schema;
    private final String className;
    private final String alternativeName;

    public DataTypeProcessingContext(String namespace,
                                     String javaDoc,
                                     BlueprintSchema schema,
                                     String className,
                                     String alternativeName) {
        this.namespace = namespace;
        this.javaDoc = javaDoc;
        this.schema = schema;
        this.className = className;
        this.alternativeName = alternativeName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getJavaDoc() {
        return javaDoc;
    }

    public BlueprintSchema getSchema() {
        return schema;
    }

    public String getClassName() {
        return className;
    }

    public String getAlternativeName() {
        return alternativeName;
    }
}
