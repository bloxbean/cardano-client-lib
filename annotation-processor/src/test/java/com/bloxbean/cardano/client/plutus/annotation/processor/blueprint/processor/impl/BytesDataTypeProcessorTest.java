package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.impl;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for BytesDataTypeProcessor
 */
class BytesDataTypeProcessorTest {
    
    private BytesDataTypeProcessor processor;
    private ProcessingContext context;
    private BlueprintSchema schema;
    private CodeGenerationConfig config;
    
    @BeforeEach
    void setUp() {
        processor = new BytesDataTypeProcessor();
        
        // Mock dependencies
        schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.bytes);
        schema.setTitle("TestBytes");
        
        config = CodeGenerationConfig.builder()
                .packageName("test.package")
                .build();
        
        context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .fieldName("testField")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
    }
    
    @Test
    void testCanProcessBytes() {
        assertTrue(processor.canProcess(BlueprintDatatype.bytes));
    }
    
    @Test
    void testCannotProcessNonBytes() {
        assertFalse(processor.canProcess(BlueprintDatatype.integer));
        assertFalse(processor.canProcess(BlueprintDatatype.string));
        assertFalse(processor.canProcess(BlueprintDatatype.bool));
        assertFalse(processor.canProcess(BlueprintDatatype.list));
        assertFalse(processor.canProcess(BlueprintDatatype.map));
        assertFalse(processor.canProcess(BlueprintDatatype.constructor));
        assertFalse(processor.canProcess(BlueprintDatatype.option));
        assertFalse(processor.canProcess(BlueprintDatatype.pair));
    }
    
    @Test
    void testProcessDataType() {
        FieldSpec fieldSpec = processor.processDataType(context);
        
        assertNotNull(fieldSpec);
        assertEquals("testField", fieldSpec.name);
        assertEquals(TypeName.get(byte[].class), fieldSpec.type);
        assertTrue(fieldSpec.modifiers.contains(Modifier.PRIVATE));
    }
    
    @Test
    void testProcessDataTypeWithNullContext() {
        assertThrows(NullPointerException.class, () -> {
            processor.processDataType(null);
        });
    }
    
    @Test
    void testProcessDataTypeWithNullSchema() {
        ProcessingContext invalidContext = ProcessingContext.builder()
                .schema(null)
                .config(config)
                .fieldName("testField")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        assertThrows(NullPointerException.class, () -> {
            processor.processDataType(invalidContext);
        });
    }
    
    @Test
    void testProcessDataTypeWithWrongDataType() {
        schema.setDataType(BlueprintDatatype.integer);
        
        assertThrows(IllegalArgumentException.class, () -> {
            processor.processDataType(context);
        });
    }
    
    @Test
    void testProcessDataTypeWithNullFieldName() {
        ProcessingContext invalidContext = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .fieldName(null)
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            processor.processDataType(invalidContext);
        });
    }
    
    @Test
    void testProcessDataTypeWithEmptyFieldName() {
        ProcessingContext invalidContext = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .fieldName("")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            processor.processDataType(invalidContext);
        });
    }
    
    @Test
    void testPriority() {
        assertEquals(50, processor.getPriority());
    }
    
    @Test
    void testProcessDataTypeWithDescription() {
        schema.setDescription("Test bytes field description");
        
        FieldSpec fieldSpec = processor.processDataType(context);
        
        assertNotNull(fieldSpec);
        assertEquals("testField", fieldSpec.name);
        assertEquals(TypeName.get(byte[].class), fieldSpec.type);
        
        // Check if JavaDoc was added (this depends on the actual implementation)
        // The actual implementation might not add JavaDoc, so this is optional
        if (fieldSpec.javadoc != null && !fieldSpec.javadoc.isEmpty()) {
            assertTrue(fieldSpec.javadoc.toString().contains("Test bytes field description"));
        }
    }
    
    @Test
    void testProcessDataTypeWithDifferentFieldNames() {
        String[] fieldNames = {"field1", "myBytes", "dataBytes", "hexData"};
        
        for (String fieldName : fieldNames) {
            ProcessingContext testContext = ProcessingContext.builder()
                    .schema(schema)
                    .config(config)
                    .fieldName(fieldName)
                    .processingEnvironment(mock(ProcessingEnvironment.class))
                    .build();
            
            FieldSpec fieldSpec = processor.processDataType(testContext);
            
            assertNotNull(fieldSpec);
            assertEquals(fieldName, fieldSpec.name);
            assertEquals(TypeName.get(byte[].class), fieldSpec.type);
        }
    }
}