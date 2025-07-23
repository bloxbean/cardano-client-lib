package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessingContextTest {
    
    private BlueprintSchema schema;
    private CodeGenerationConfig config;
    private ProcessingEnvironment processingEnvironment;
    
    @BeforeEach
    void setUp() {
        schema = new BlueprintSchema();
        schema.setTitle("TestSchema");
        schema.setDataType(BlueprintDatatype.constructor);
        
        config = CodeGenerationConfig.builder()
                .packageName("test.package")
                .build();
        
        processingEnvironment = mock(ProcessingEnvironment.class);
    }
    
    @Test
    void testBuilder() {
        ProcessingContext context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .className("TestClass")
                .fieldName("testField")
                .processingEnvironment(processingEnvironment)
                .build();
        
        assertNotNull(context);
        assertEquals(schema, context.getSchema());
        assertEquals(config, context.getConfig());
        assertEquals("TestClass", context.getClassName());
        assertEquals("testField", context.getFieldName());
        assertEquals(processingEnvironment, context.getProcessingEnvironment());
    }
    
    @Test
    void testGetEffectiveClassName() {
        ProcessingContext context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .className("TestClass")
                .processingEnvironment(processingEnvironment)
                .build();
        
        assertEquals("TestClass", context.getEffectiveClassName());
    }
    
    @Test
    void testGetEffectiveClassNameFromSchema() {
        ProcessingContext context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .processingEnvironment(processingEnvironment)
                .build();
        
        assertEquals("TestSchema", context.getEffectiveClassName());
    }
}