package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.analyzer.SchemaAnalyzer;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.CommandResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.SchemaCommand;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Command for skipping primitive type aliases.
 * Handles schemas that are just aliases for primitive types with no additional structure.
 * 
 * This replaces the primitive type checking logic in the original FieldSpecProcessor.
 */
public class SkipPrimitiveAliasCommand implements SchemaCommand {
    
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.isPrimitiveTypeAlias(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        
        String reason = String.format(
            "Schema '%s' is a primitive type alias (%s) with no additional structure",
            schema.getTitle(),
            schema.getDataType()
        );
        
        return CommandResult.skip(reason);
    }
    
    @Override
    public int getPriority() {
        return 5; // Very high priority to skip early
    }
    
    @Override
    public String getDescription() {
        return "Skips Blueprint schemas that are simple primitive type aliases";
    }
}