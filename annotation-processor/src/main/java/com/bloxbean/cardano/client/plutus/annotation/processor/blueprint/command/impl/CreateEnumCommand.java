package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl;

import com.bloxbean.cardano.client.plutus.annotation.Constr;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.analyzer.SchemaAnalyzer;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.CommandResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.SchemaCommand;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util.FieldNameResolver;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

/**
 * Command for creating enum classes from Blueprint schemas.
 * Handles schemas that represent enumeration types (multiple constructor options with no fields).
 * 
 * This replaces the createEnumIfPossible logic in the original FieldSpecProcessor.
 */
public class CreateEnumCommand implements SchemaCommand {
    
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.isEnumSchema(schema) && SchemaAnalyzer.hasValidTitle(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        try {
            BlueprintSchema schema = context.getSchema();
            String enumClassName = FieldNameResolver.resolveClassName(
                context.getProcessingEnvironment(),
                context.getConfig(),
                schema.getTitle(),
                schema.getTitle()
            );
            
            TypeSpec enumSpec = createEnumTypeSpec(schema, enumClassName);
            
            context.getFileWriter().writeJavaFile(context.getPackageName(), enumSpec);
            
            return CommandResult.success(
                "Created enum: " + enumClassName,
                enumSpec
            );
            
        } catch (Exception e) {
            return CommandResult.failure(
                "Failed to create enum: " + e.getMessage(),
                e
            );
        }
    }
    
    @Override
    public int getPriority() {
        return 20; // Higher priority than regular class creation
    }
    
    @Override
    public String getDescription() {
        return "Creates Java enum classes for Blueprint enum schemas";
    }
    
    private TypeSpec createEnumTypeSpec(BlueprintSchema schema, String enumClassName) {
        TypeSpec.Builder enumBuilder = TypeSpec.enumBuilder(enumClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Constr.class).build());
        
        // Add JavaDoc if enabled
        if (schema.getDescription() != null) {
            enumBuilder.addJavadoc(schema.getDescription());
        }
        
        // Add enum constants
        for (BlueprintSchema anyOfSchema : schema.getAnyOf()) {
            String constantName = sanitizeEnumConstantName(anyOfSchema.getTitle());
            enumBuilder.addEnumConstant(constantName);
        }
        
        return enumBuilder.build();
    }
    
    private String sanitizeEnumConstantName(String name) {
        if (name == null || name.isEmpty()) {
            return "UNKNOWN";
        }
        
        // Convert to valid Java enum constant name (UPPER_SNAKE_CASE)
        return name.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replaceAll("[^a-zA-Z0-9_]", "_")
                  .toUpperCase();
    }
}