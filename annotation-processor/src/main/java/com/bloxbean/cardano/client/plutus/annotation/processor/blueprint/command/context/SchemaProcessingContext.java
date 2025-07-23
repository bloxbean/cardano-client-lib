package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.JavaFileWriter;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Objects;

/**
 * Context object for schema command execution.
 * Contains all necessary information for schema-level operations.
 */
public final class SchemaProcessingContext {
    
    private final BlueprintSchema schema;
    private final String namespace;
    private final CodeGenerationConfig config;
    private final JavaFileWriter fileWriter;
    private final ProcessingEnvironment processingEnvironment;
    
    private SchemaProcessingContext(Builder builder) {
        this.schema = Objects.requireNonNull(builder.schema, "Schema cannot be null");
        this.namespace = builder.namespace;
        this.config = Objects.requireNonNull(builder.config, "Config cannot be null");
        this.fileWriter = Objects.requireNonNull(builder.fileWriter, "FileWriter cannot be null");
        this.processingEnvironment = Objects.requireNonNull(builder.processingEnvironment, "ProcessingEnvironment cannot be null");
    }
    
    // Static factory methods
    public static Builder builder() {
        return new Builder();
    }
    
    public static Builder fromSchema(BlueprintSchema schema) {
        return new Builder().schema(schema);
    }
    
    // Getters
    public BlueprintSchema getSchema() { return schema; }
    public String getNamespace() { return namespace; }
    public CodeGenerationConfig getConfig() { return config; }
    public JavaFileWriter getFileWriter() { return fileWriter; }
    public ProcessingEnvironment getProcessingEnvironment() { return processingEnvironment; }
    
    // Convenience methods
    public String getSchemaTitle() {
        return schema.getTitle();
    }
    
    public String getPackageName() {
        String basePkg = config.getPackageName();
        if (namespace != null && !namespace.isEmpty()) {
            return basePkg + "." + namespace + ".model";
        }
        return basePkg + ".model";
    }
    
    // Builder class
    public static class Builder {
        private BlueprintSchema schema;
        private String namespace;
        private CodeGenerationConfig config;
        private JavaFileWriter fileWriter;
        private ProcessingEnvironment processingEnvironment;
        
        public Builder schema(BlueprintSchema schema) {
            this.schema = schema;
            return this;
        }
        
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public Builder config(CodeGenerationConfig config) {
            this.config = config;
            return this;
        }
        
        public Builder fileWriter(JavaFileWriter fileWriter) {
            this.fileWriter = fileWriter;
            return this;
        }
        
        public Builder processingEnvironment(ProcessingEnvironment processingEnvironment) {
            this.processingEnvironment = processingEnvironment;
            return this;
        }
        
        public SchemaProcessingContext build() {
            return new SchemaProcessingContext(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaProcessingContext that = (SchemaProcessingContext) o;
        return Objects.equals(schema, that.schema) &&
               Objects.equals(namespace, that.namespace) &&
               Objects.equals(config, that.config) &&
               Objects.equals(fileWriter, that.fileWriter) &&
               Objects.equals(processingEnvironment, that.processingEnvironment);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(schema, namespace, config, fileWriter, processingEnvironment);
    }
    
    @Override
    public String toString() {
        return "SchemaProcessingContext{" +
               "schemaTitle='" + (schema != null ? schema.getTitle() : "null") + '\'' +
               ", namespace='" + namespace + '\'' +
               ", packageName='" + getPackageName() + '\'' +
               '}';
    }
}