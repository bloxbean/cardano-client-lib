package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.context.SchemaProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl.CreateEnumCommand;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl.SkipPrimitiveAliasCommand;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.command.impl.SkipSpecialTypesCommand;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for CommandExecutor using Command pattern
 */
class CommandExecutorTest {
    
    private CommandExecutor executor;
    private SchemaProcessingContext context;
    private BlueprintSchema schema;
    
    @BeforeEach
    void setUp() {
        schema = new BlueprintSchema();
        schema.setTitle("TestSchema");
        schema.setDataType(BlueprintDatatype.constructor);
        
        context = mock(SchemaProcessingContext.class);
        when(context.getSchema()).thenReturn(schema);
    }
    
    @Test
    void testExecutorWithEmptyCommands() {
        executor = new CommandExecutor(Collections.emptyList());
        
        List<CommandResult> results = executor.executeCommands(context);
        
        assertNotNull(results);
        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).getMessage().contains("No command found"));
    }
    
    @Test
    void testExecutorWithSingleCommand() {
        SchemaCommand mockCommand = mock(SchemaCommand.class);
        when(mockCommand.canExecute(schema)).thenReturn(true);
        when(mockCommand.execute(context)).thenReturn(CommandResult.success("Test executed"));
        when(mockCommand.getName()).thenReturn("TestCommand");
        
        executor = new CommandExecutor(Arrays.asList(mockCommand));
        
        List<CommandResult> results = executor.executeCommands(context);
        
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("Test executed", results.get(0).getMessage());
        
        verify(mockCommand).canExecute(schema);
        verify(mockCommand).execute(context);
    }
    
    @Test
    void testExecutorWithMultipleCommands() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        when(command1.canExecute(schema)).thenReturn(false);
        when(command1.getPriority()).thenReturn(10);
        
        SchemaCommand command2 = mock(SchemaCommand.class);
        when(command2.canExecute(schema)).thenReturn(true);
        when(command2.execute(context)).thenReturn(CommandResult.success("Command 2 executed"));
        when(command2.getName()).thenReturn("Command2");
        when(command2.getPriority()).thenReturn(20);
        
        executor = new CommandExecutor(Arrays.asList(command2, command1), true);
        
        List<CommandResult> results = executor.executeCommands(context);
        
        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("Command 2 executed", results.get(0).getMessage());
        
        verify(command1).canExecute(schema);
        verify(command2).canExecute(schema);
        verify(command2).execute(context);
        verify(command1, never()).execute(any());
    }
    
    @Test
    void testExecutorWithCommandException() {
        SchemaCommand mockCommand = mock(SchemaCommand.class);
        when(mockCommand.canExecute(schema)).thenReturn(true);
        when(mockCommand.execute(context)).thenThrow(new RuntimeException("Test exception"));
        when(mockCommand.getName()).thenReturn("FailingCommand");
        
        executor = new CommandExecutor(Arrays.asList(mockCommand));
        
        List<CommandResult> results = executor.executeCommands(context);
        
        assertNotNull(results);
        assertEquals(1, results.size());
        assertFalse(results.get(0).isSuccess());
        assertTrue(results.get(0).getMessage().contains("FailingCommand failed"));
        assertTrue(results.get(0).getMessage().contains("Test exception"));
    }
    
    @Test
    void testExecutorWithStopOnFirstMatchDisabled() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        when(command1.canExecute(schema)).thenReturn(true);
        when(command1.execute(context)).thenReturn(CommandResult.success("Command 1 executed"));
        when(command1.getName()).thenReturn("Command1");
        when(command1.getPriority()).thenReturn(10);
        
        SchemaCommand command2 = mock(SchemaCommand.class);
        when(command2.canExecute(schema)).thenReturn(true);
        when(command2.execute(context)).thenReturn(CommandResult.success("Command 2 executed"));
        when(command2.getName()).thenReturn("Command2");
        when(command2.getPriority()).thenReturn(20);
        
        executor = new CommandExecutor(Arrays.asList(command1, command2), false);
        
        List<CommandResult> results = executor.executeCommands(context);
        
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
        
        verify(command1).execute(context);
        verify(command2).execute(context);
    }
    
    @Test
    void testExecuteSingleCommand() {
        SchemaCommand mockCommand = mock(SchemaCommand.class);
        when(mockCommand.canExecute(schema)).thenReturn(true);
        when(mockCommand.execute(context)).thenReturn(CommandResult.success("Single command executed"));
        when(mockCommand.getName()).thenReturn("SingleCommand");
        
        executor = new CommandExecutor(Arrays.asList(mockCommand));
        
        CommandResult result = executor.executeSingleCommand(context);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("Single command executed", result.getMessage());
    }
    
    @Test
    void testExecuteSingleCommandWithNoCommands() {
        executor = new CommandExecutor(Collections.emptyList());
        
        CommandResult result = executor.executeSingleCommand(context);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No command found"));
    }
    
    @Test
    void testFindCompatibleCommands() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        when(command1.canExecute(schema)).thenReturn(true);
        
        SchemaCommand command2 = mock(SchemaCommand.class);
        when(command2.canExecute(schema)).thenReturn(false);
        
        SchemaCommand command3 = mock(SchemaCommand.class);
        when(command3.canExecute(schema)).thenReturn(true);
        
        executor = new CommandExecutor(Arrays.asList(command1, command2, command3));
        
        List<SchemaCommand> compatibleCommands = executor.findCompatibleCommands(schema);
        
        assertNotNull(compatibleCommands);
        assertEquals(2, compatibleCommands.size());
        assertTrue(compatibleCommands.contains(command1));
        assertTrue(compatibleCommands.contains(command3));
        assertFalse(compatibleCommands.contains(command2));
    }
    
    @Test
    void testCanHandle() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        when(command1.canExecute(schema)).thenReturn(false);
        
        SchemaCommand command2 = mock(SchemaCommand.class);
        when(command2.canExecute(schema)).thenReturn(true);
        
        executor = new CommandExecutor(Arrays.asList(command1, command2));
        
        assertTrue(executor.canHandle(schema));
        
        when(command2.canExecute(schema)).thenReturn(false);
        assertFalse(executor.canHandle(schema));
    }
    
    @Test
    void testGetCommands() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        SchemaCommand command2 = mock(SchemaCommand.class);
        
        executor = new CommandExecutor(Arrays.asList(command1, command2));
        
        List<SchemaCommand> commands = executor.getCommands();
        
        assertNotNull(commands);
        assertEquals(2, commands.size());
        assertTrue(commands.contains(command1));
        assertTrue(commands.contains(command2));
        
        // Test immutability
        assertThrows(UnsupportedOperationException.class, () -> {
            commands.add(mock(SchemaCommand.class));
        });
    }
    
    @Test
    void testGetInfo() {
        SchemaCommand command1 = mock(SchemaCommand.class);
        SchemaCommand command2 = mock(SchemaCommand.class);
        
        executor = new CommandExecutor(Arrays.asList(command1, command2), true);
        
        CommandExecutor.ExecutorInfo info = executor.getInfo();
        
        assertNotNull(info);
        assertEquals(2, info.getCommandCount());
        assertTrue(info.isStopOnFirstMatch());
        assertNotNull(info.getCommandTypeCounts());
    }
    
    @Test
    void testCommandPriorityOrdering() {
        SchemaCommand lowPriorityCommand = mock(SchemaCommand.class);
        when(lowPriorityCommand.getPriority()).thenReturn(100);
        when(lowPriorityCommand.canExecute(schema)).thenReturn(true);
        when(lowPriorityCommand.execute(context)).thenReturn(CommandResult.success("Low priority"));
        when(lowPriorityCommand.getName()).thenReturn("LowPriority");
        
        SchemaCommand highPriorityCommand = mock(SchemaCommand.class);
        when(highPriorityCommand.getPriority()).thenReturn(10);
        when(highPriorityCommand.canExecute(schema)).thenReturn(true);
        when(highPriorityCommand.execute(context)).thenReturn(CommandResult.success("High priority"));
        when(highPriorityCommand.getName()).thenReturn("HighPriority");
        
        // Add in wrong order to test sorting
        executor = new CommandExecutor(Arrays.asList(lowPriorityCommand, highPriorityCommand), true);
        
        CommandResult result = executor.executeSingleCommand(context);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("High priority", result.getMessage());
        
        verify(highPriorityCommand).execute(context);
        verify(lowPriorityCommand, never()).execute(any());
    }
    
    @Test
    void testRealCommands() {
        // Test with actual command implementations
        CreateEnumCommand enumCommand = new CreateEnumCommand();
        SkipPrimitiveAliasCommand skipCommand = new SkipPrimitiveAliasCommand();
        SkipSpecialTypesCommand skipSpecialCommand = new SkipSpecialTypesCommand();
        
        executor = new CommandExecutor(Arrays.asList(enumCommand, skipCommand, skipSpecialCommand));
        
        // Test with a primitive alias schema
        BlueprintSchema primitiveSchema = new BlueprintSchema();
        primitiveSchema.setDataType(BlueprintDatatype.bytes);
        primitiveSchema.setTitle("SimpleBytes");
        
        SchemaProcessingContext primitiveContext = mock(SchemaProcessingContext.class);
        when(primitiveContext.getSchema()).thenReturn(primitiveSchema);
        
        assertTrue(executor.canHandle(primitiveSchema));
        
        List<SchemaCommand> compatibleCommands = executor.findCompatibleCommands(primitiveSchema);
        assertNotNull(compatibleCommands);
        assertTrue(compatibleCommands.size() > 0);
    }
}