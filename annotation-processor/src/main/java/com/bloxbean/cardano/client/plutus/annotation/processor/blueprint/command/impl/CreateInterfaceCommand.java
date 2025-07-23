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
 * Command for creating interface classes from Blueprint schemas.
 * Handles schemas that require interfaces (multiple anyOf implementations).
 * 
 * This replaces the interface creation logic in the original FieldSpecProcessor.
 */
public class CreateInterfaceCommand implements SchemaCommand {
    
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.requiresInterface(schema) && SchemaAnalyzer.hasValidTitle(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        try {
            BlueprintSchema schema = context.getSchema();
            String interfaceName = FieldNameResolver.resolveClassName(
                context.getProcessingEnvironment(),
                context.getConfig(),
                schema.getTitle(),
                schema.getTitle()
            );
            
            TypeSpec interfaceSpec = createInterfaceTypeSpec(schema, interfaceName);
            
            context.getFileWriter().writeJavaFile(context.getPackageName(), interfaceSpec);
            
            return CommandResult.success(
                "Created interface: " + interfaceName,
                interfaceSpec
            );
            
        } catch (Exception e) {
            return CommandResult.failure(
                "Failed to create interface: " + e.getMessage(),
                e
            );
        }
    }
    
    @Override
    public int getPriority() {
        return 15; // Higher priority than regular class creation, lower than enum
    }
    
    @Override
    public String getDescription() {
        return "Creates Java interfaces for Blueprint schemas with multiple implementations";
    }
    
    private TypeSpec createInterfaceTypeSpec(BlueprintSchema schema, String interfaceName) {
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Constr.class).build());
        
        // Add JavaDoc if enabled
        if (schema.getDescription() != null) {
            interfaceBuilder.addJavadoc(schema.getDescription());
            interfaceBuilder.addJavadoc("\n\nThis interface represents multiple possible implementations:");
            
            if (schema.getAnyOf() != null) {
                for (BlueprintSchema anyOf : schema.getAnyOf()) {
                    if (anyOf.getTitle() != null) {
                        interfaceBuilder.addJavadoc("\n- ").addJavadoc(anyOf.getTitle());
                    }
                }
            }
        }
        
        // Add comment about the purpose
        if (schema.getComment() != null) {
            interfaceBuilder.addJavadoc("\n\nNote: ").addJavadoc(schema.getComment());
        }
        
        return interfaceBuilder.build();
    }
}