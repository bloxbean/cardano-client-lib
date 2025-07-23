package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Objects;

/**
 * Immutable context object that contains all information needed for processing Blueprint schemas.
 * Eliminates the need to pass multiple parameters between methods and provides a clean API.
 * 
 * This replaces the parameter passing in the original code where methods had 5+ parameters.
 */
public final class ProcessingContext {
    
    private final String namespace;
    private final String javaDoc;
    private final BlueprintSchema schema;
    private final String alternativeName;
    private final CodeGenerationConfig config;
    private final ProcessingEnvironment processingEnvironment;
    private final String fieldName;
    private final String className;
    
    private ProcessingContext(Builder builder) {
        this.namespace = builder.namespace;
        this.javaDoc = builder.javaDoc;
        this.schema = Objects.requireNonNull(builder.schema, "Schema cannot be null");
        this.alternativeName = builder.alternativeName;
        this.config = Objects.requireNonNull(builder.config, "Config cannot be null");
        this.processingEnvironment = Objects.requireNonNull(builder.processingEnvironment, "ProcessingEnvironment cannot be null");
        this.fieldName = builder.fieldName;
        this.className = builder.className;
    }
    
    // Static factory methods
    public static Builder builder() {
        return new Builder();
    }
    
    public static Builder fromSchema(BlueprintSchema schema) {
        return new Builder().schema(schema);
    }
    
    // Getters
    public String getNamespace() { return namespace; }
    public String getJavaDoc() { return javaDoc; }
    public BlueprintSchema getSchema() { return schema; }
    public String getAlternativeName() { return alternativeName; }
    public CodeGenerationConfig getConfig() { return config; }
    public ProcessingEnvironment getProcessingEnvironment() { return processingEnvironment; }
    public String getFieldName() { return fieldName; }
    public String getClassName() { return className; }
    
    // Convenience methods
    public String getSchemaTitle() {
        return schema.getTitle();
    }
    
    public String getEffectiveFieldName() {
        if (fieldName != null) return fieldName;
        if (schema.getTitle() != null) return schema.getTitle();
        return alternativeName;
    }
    
    public String getEffectiveClassName() {
        if (className != null) return className;
        if (schema.getTitle() != null) return schema.getTitle();
        return alternativeName;
    }
    
    public String getPackageName() {
        String basePkg = config.getPackageName();
        if (namespace != null && !namespace.isEmpty()) {
            return basePkg + "." + namespace + ".model";
        }
        return basePkg + ".model";
    }
    
    // Builder for creating new contexts with modified values
    public Builder toBuilder() {
        return new Builder()
            .namespace(namespace)
            .javaDoc(javaDoc)
            .schema(schema)
            .alternativeName(alternativeName)
            .config(config)
            .processingEnvironment(processingEnvironment)
            .fieldName(fieldName)
            .className(className);
    }
    
    // Builder class
    public static class Builder {
        private String namespace;
        private String javaDoc = "";
        private BlueprintSchema schema;
        private String alternativeName;
        private CodeGenerationConfig config;
        private ProcessingEnvironment processingEnvironment;
        private String fieldName;
        private String className;
        
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public Builder javaDoc(String javaDoc) {
            this.javaDoc = javaDoc;
            return this;
        }
        
        public Builder schema(BlueprintSchema schema) {
            this.schema = schema;
            return this;
        }
        
        public Builder alternativeName(String alternativeName) {
            this.alternativeName = alternativeName;
            return this;
        }
        
        public Builder config(CodeGenerationConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder processingEnvironment(ProcessingEnvironment processingEnvironment) {
            this.processingEnvironment = processingEnvironment;
            return this;
        }
        
        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }
        
        public Builder className(String className) {
            this.className = className;
            return this;
        }
        
        public ProcessingContext build() {
            return new ProcessingContext(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessingContext that = (ProcessingContext) o;
        return Objects.equals(namespace, that.namespace) &&
               Objects.equals(javaDoc, that.javaDoc) &&
               Objects.equals(schema, that.schema) &&
               Objects.equals(alternativeName, that.alternativeName) &&
               Objects.equals(config, that.config) &&
               Objects.equals(processingEnvironment, that.processingEnvironment) &&
               Objects.equals(fieldName, that.fieldName) &&
               Objects.equals(className, that.className);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, javaDoc, schema, alternativeName, config, 
                          processingEnvironment, fieldName, className);
    }
    
    @Override
    public String toString() {
        return "ProcessingContext{" +
               "namespace='" + namespace + '\'' +
               ", javaDoc='" + javaDoc + '\'' +
               ", schemaTitle='" + (schema != null ? schema.getTitle() : "null") + '\'' +
               ", alternativeName='" + alternativeName + '\'' +
               ", fieldName='" + fieldName + '\'' +
               ", className='" + className + '\'' +
               '}';
    }
}