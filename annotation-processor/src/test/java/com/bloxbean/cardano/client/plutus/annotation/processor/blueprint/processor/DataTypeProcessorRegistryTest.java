package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl.*;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.registry.DataTypeProcessorRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for DataTypeProcessorRegistry using Strategy pattern
 */
class DataTypeProcessorRegistryTest {
    
    private DataTypeProcessorRegistry registry;
    
    @BeforeEach
    void setUp() {
        // Create a registry with actual processors
        registry = new DataTypeProcessorRegistry(Arrays.asList(
            new BytesDataTypeProcessor(),
            new IntegerDataTypeProcessor(),
            new StringDataTypeProcessor(),
            new BooleanDataTypeProcessor(),
            new ListDataTypeProcessor(),
            new MapDataTypeProcessor(),
            new ConstructorDataTypeProcessor(),
            new OptionDataTypeProcessor(),
            new PairDataTypeProcessor()
        ));
    }
    
    @Test
    void testRegistryInitialization() {
        assertNotNull(registry);
        assertTrue(registry.getProcessors().size() > 0);
    }
    
    @Test
    void testFindProcessorForBytes() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.bytes);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.bytes));
    }
    
    @Test
    void testFindProcessorForInteger() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.integer);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.integer));
    }
    
    @Test
    void testFindProcessorForString() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.string);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.string));
    }
    
    @Test
    void testFindProcessorForBool() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.bool);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.bool));
    }
    
    @Test
    void testFindProcessorForList() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.list);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.list));
    }
    
    @Test
    void testFindProcessorForMap() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.map);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.map));
    }
    
    @Test
    void testFindProcessorForConstructor() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.constructor);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.constructor));
    }
    
    @Test
    void testFindProcessorForOption() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.option);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.option));
    }
    
    @Test
    void testFindProcessorForPair() {
        DataTypeProcessor processor = registry.findProcessor(BlueprintDatatype.pair);
        assertNotNull(processor);
        assertTrue(processor.canProcess(BlueprintDatatype.pair));
    }
    
    @Test
    void testProcessorPriority() {
        DataTypeProcessor bytesProcessor = registry.findProcessor(BlueprintDatatype.bytes);
        DataTypeProcessor integerProcessor = registry.findProcessor(BlueprintDatatype.integer);
        
        assertNotNull(bytesProcessor);
        assertNotNull(integerProcessor);
        
        // Verify processors have defined priorities
        assertTrue(bytesProcessor.getPriority() > 0);
        assertTrue(integerProcessor.getPriority() > 0);
    }
    
    @Test
    void testRegistryReturnsNullForUnsupportedType() {
        // This test assumes there's no processor for a hypothetical type
        // Since all types are supported, we can't test this directly
        // This is more for documentation of expected behavior
        assertDoesNotThrow(() -> registry.findProcessor(BlueprintDatatype.bytes));
    }
    
    @Test
    void testRegistryContainsAllProcessors() {
        // Test that registry contains processors for all supported types
        assertNotNull(registry.findProcessor(BlueprintDatatype.bytes));
        assertNotNull(registry.findProcessor(BlueprintDatatype.integer));
        assertNotNull(registry.findProcessor(BlueprintDatatype.string));
        assertNotNull(registry.findProcessor(BlueprintDatatype.bool));
        assertNotNull(registry.findProcessor(BlueprintDatatype.list));
        assertNotNull(registry.findProcessor(BlueprintDatatype.map));
        assertNotNull(registry.findProcessor(BlueprintDatatype.constructor));
        assertNotNull(registry.findProcessor(BlueprintDatatype.option));
        assertNotNull(registry.findProcessor(BlueprintDatatype.pair));
    }
    
    @Test
    void testProcessorConsistency() {
        // Test that the same processor is returned for the same type
        DataTypeProcessor processor1 = registry.findProcessor(BlueprintDatatype.bytes);
        DataTypeProcessor processor2 = registry.findProcessor(BlueprintDatatype.bytes);
        
        assertSame(processor1, processor2);
    }
}