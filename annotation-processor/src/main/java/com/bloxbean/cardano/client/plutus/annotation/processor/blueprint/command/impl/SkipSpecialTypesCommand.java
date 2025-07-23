package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.analyzer.SchemaAnalyzer;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.CommandResult;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.SchemaCommand;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Command for skipping special built-in types (Option, Pair).
 * These types are handled by specialized processors and don't need class generation.
 * 
 * This replaces the Option/Pair checking logic in the original FieldSpecProcessor.
 */
public class SkipSpecialTypesCommand implements SchemaCommand {
    
    @Override
    public boolean canExecute(BlueprintSchema schema) {
        return SchemaAnalyzer.isOptionSchema(schema) || SchemaAnalyzer.isPairSchema(schema);
    }
    
    @Override
    public CommandResult execute(SchemaProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        
        String reason;
        if (SchemaAnalyzer.isOptionSchema(schema)) {
            reason = "Schema is an Option type - handled by specialized processor";
        } else if (SchemaAnalyzer.isPairSchema(schema)) {
            reason = "Schema is a Pair type - handled by specialized processor";
        } else {
            reason = "Schema is a special built-in type";
        }
        
        return CommandResult.skip(reason);
    }
    
    @Override
    public int getPriority() {
        return 8; // High priority to skip early
    }
    
    @Override
    public String getDescription() {
        return "Skips Blueprint schemas for special built-in types (Option, Pair)";
    }
}