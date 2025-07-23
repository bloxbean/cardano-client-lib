package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes schema commands using the Command pattern.
 * Commands are executed in priority order until one can handle the schema.
 * 
 * This replaces the complex conditional logic in FieldSpecProcessor.createDatumClass().
 */
public class CommandExecutor {
    
    private final List<SchemaCommand> commands;
    private final boolean stopOnFirstMatch;
    
    /**
     * Creates a command executor with the given commands
     * 
     * @param commands list of schema commands
     */
    public CommandExecutor(List<SchemaCommand> commands) {
        this(commands, true);
    }
    
    /**
     * Creates a command executor with optional multi-command execution
     * 
     * @param commands list of schema commands
     * @param stopOnFirstMatch whether to stop after the first matching command
     */
    public CommandExecutor(List<SchemaCommand> commands, boolean stopOnFirstMatch) {
        this.commands = commands.stream()
                .sorted(Comparator.comparing(SchemaCommand::getPriority))
                .collect(Collectors.toList());
        this.stopOnFirstMatch = stopOnFirstMatch;
    }
    
    /**
     * Executes commands for the given schema
     * 
     * @param context the schema processing context
     * @return list of command results
     */
    public List<CommandResult> executeCommands(SchemaProcessingContext context) {
        BlueprintSchema schema = context.getSchema();
        List<CommandResult> results = new ArrayList<>();
        
        for (SchemaCommand command : commands) {
            if (command.canExecute(schema)) {
                try {
                    CommandResult result = command.execute(context);
                    results.add(result);
                    
                    // Stop on first successful execution if configured
                    if (stopOnFirstMatch && result.isSuccess()) {
                        break;
                    }
                    
                } catch (Exception e) {
                    CommandResult errorResult = CommandResult.failure(
                        "Command " + command.getName() + " failed: " + e.getMessage(),
                        e
                    );
                    results.add(errorResult);
                    
                    // Stop on error if configured
                    if (stopOnFirstMatch) {
                        break;
                    }
                }
            }
        }
        
        // If no commands could handle the schema, return a "no handler" result
        if (results.isEmpty()) {
            results.add(CommandResult.failure(
                "No command found to handle schema: " + schema.getTitle() + 
                " (dataType: " + schema.getDataType() + ")"
            ));
        }
        
        return results;
    }
    
    /**
     * Executes a single command for the given schema
     * 
     * @param context the schema processing context
     * @return the first command result, or failure if no command can handle the schema
     */
    public CommandResult executeSingleCommand(SchemaProcessingContext context) {
        List<CommandResult> results = executeCommands(context);
        return results.isEmpty() ? 
            CommandResult.failure("No commands available") : 
            results.get(0);
    }
    
    /**
     * Finds all commands that can handle the given schema
     * 
     * @param schema the schema to check
     * @return list of compatible commands
     */
    public List<SchemaCommand> findCompatibleCommands(BlueprintSchema schema) {
        return commands.stream()
                .filter(command -> command.canExecute(schema))
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if any command can handle the given schema
     * 
     * @param schema the schema to check
     * @return true if at least one command can handle the schema
     */
    public boolean canHandle(BlueprintSchema schema) {
        return commands.stream().anyMatch(command -> command.canExecute(schema));
    }
    
    /**
     * Returns all registered commands
     * 
     * @return list of commands
     */
    public List<SchemaCommand> getCommands() {
        return List.copyOf(commands);
    }
    
    /**
     * Returns execution statistics
     * 
     * @return executor info
     */
    public ExecutorInfo getInfo() {
        return new ExecutorInfo(
                commands.size(),
                stopOnFirstMatch,
                commands.stream()
                        .collect(Collectors.groupingBy(
                                cmd -> cmd.getClass().getSimpleName(),
                                Collectors.counting()
                        ))
        );
    }
    
    /**
     * Information about the command executor
     */
    public static class ExecutorInfo {
        private final int commandCount;
        private final boolean stopOnFirstMatch;
        private final java.util.Map<String, Long> commandTypeCounts;
        
        public ExecutorInfo(int commandCount, boolean stopOnFirstMatch, 
                           java.util.Map<String, Long> commandTypeCounts) {
            this.commandCount = commandCount;
            this.stopOnFirstMatch = stopOnFirstMatch;
            this.commandTypeCounts = java.util.Map.copyOf(commandTypeCounts);
        }
        
        public int getCommandCount() { return commandCount; }
        public boolean isStopOnFirstMatch() { return stopOnFirstMatch; }
        public java.util.Map<String, Long> getCommandTypeCounts() { return commandTypeCounts; }
        
        @Override
        public String toString() {
            return "ExecutorInfo{" +
                   "commands=" + commandCount +
                   ", stopOnFirstMatch=" + stopOnFirstMatch +
                   ", types=" + commandTypeCounts.keySet() +
                   '}';
        }
    }
}