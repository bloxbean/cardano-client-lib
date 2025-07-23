package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

/**
 * Command interface for schema operations using the Command pattern.
 * Each implementation handles a specific schema operation (create enum, interface, class, etc.)
 * 
 * This replaces the complex conditional logic in FieldSpecProcessor.createDatumClass()
 * with discrete, testable commands.
 */
public interface SchemaCommand {
    
    /**
     * Determines if this command can execute for the given schema
     * 
     * @param schema the Blueprint schema to check
     * @return true if this command can handle the schema
     */
    boolean canExecute(BlueprintSchema schema);
    
    /**
     * Executes the command for the given schema
     * 
     * @param context the schema processing context
     * @return the result of the command execution
     */
    CommandResult execute(SchemaProcessingContext context);
    
    /**
     * Returns the priority of this command for ordering when multiple 
     * commands can handle the same schema
     * 
     * @return priority value (lower values = higher priority)
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * Returns a human-readable name for this command (for debugging/logging)
     * 
     * @return command name
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * Returns a description of what this command does
     * 
     * @return command description
     */
    default String getDescription() {
        return "Processes Blueprint schema";
    }
}