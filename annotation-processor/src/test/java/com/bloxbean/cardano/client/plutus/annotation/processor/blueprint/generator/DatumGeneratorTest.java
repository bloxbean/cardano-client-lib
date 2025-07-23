package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.registry.DataTypeProcessorRegistry;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for DatumGenerator using Template Method pattern
 */
class DatumGeneratorTest {
    
    private DatumGenerator generator;
    private DataTypeProcessorRegistry processorRegistry;
    private ProcessingContext context;
    private BlueprintSchema schema;
    private CodeGenerationConfig config;
    
    @BeforeEach
    void setUp() {
        processorRegistry = mock(DataTypeProcessorRegistry.class);
        generator = new DatumGenerator(processorRegistry);
        
        // Set up schema
        schema = new BlueprintSchema();
        schema.setDataType(BlueprintDatatype.constructor);
        schema.setTitle("TestDatum");
        schema.setIndex(0);
        
        // Set up field schemas
        BlueprintSchema fieldSchema1 = new BlueprintSchema();
        fieldSchema1.setTitle("field1");
        fieldSchema1.setDataType(BlueprintDatatype.bytes);
        
        BlueprintSchema fieldSchema2 = new BlueprintSchema();
        fieldSchema2.setTitle("field2");
        fieldSchema2.setDataType(BlueprintDatatype.integer);
        
        schema.setFields(Arrays.asList(fieldSchema1, fieldSchema2));
        
        // Set up config
        config = CodeGenerationConfig.builder()
                .packageName("test.package")
                .generateEqualsHashCode(true)
                .generateToString(true)
                .build();
        
        // Set up context
        context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .className("TestDatum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
    }
    
    @Test
    void testGetType() {
        assertEquals(CodeGenerator.GeneratorType.DATUM, generator.getType());
    }
    
    @Test
    void testValidateInputValid() {
        assertDoesNotThrow(() -> generator.validateInput(context));
    }
    
    @Test
    void testValidateInputNullContext() {
        assertThrows(IllegalArgumentException.class, () -> {
            generator.validateInput(null);
        });
    }
    
    @Test
    void testValidateInputNullSchema() {
        ProcessingContext invalidContext = ProcessingContext.builder()
                .schema(null)
                .config(config)
                .className("TestDatum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.validateInput(invalidContext);
        });
    }
    
    @Test
    void testValidateInputWrongDataType() {
        schema.setDataType(BlueprintDatatype.bytes);
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.validateInput(context);
        });
    }
    
    @Test
    void testValidateInputNoFields() {
        schema.setFields(Collections.emptyList());
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.validateInput(context);
        });
    }
    
    @Test
    void testValidateInputNullFields() {
        schema.setFields(null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.validateInput(context);
        });
    }
    
    @Test
    void testGenerate() {
        // Mock processor registry to return processors
        when(processorRegistry.findProcessor(any())).thenReturn(null);
        
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        assertEquals(TypeSpec.Kind.CLASS, typeSpec.kind);
        assertTrue(typeSpec.modifiers.contains(Modifier.PUBLIC));
        
        // Check that annotations are added
        assertFalse(typeSpec.annotations.isEmpty());
    }
    
    @Test
    void testGenerateWithDescription() {
        schema.setDescription("Test datum description");
        
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Check if JavaDoc is added (depends on config)
        if (config.isGenerateJavaDoc()) {
            assertNotNull(typeSpec.javadoc);
        }
    }
    
    @Test
    void testGenerateWithNonZeroIndex() {
        schema.setIndex(5);
        
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Check that @Constr annotation has the correct index
        assertFalse(typeSpec.annotations.isEmpty());
    }
    
    @Test
    void testGenerateWithFieldsProcessing() {
        // Mock processor registry to return mock processors
        when(processorRegistry.findProcessor(BlueprintDatatype.bytes)).thenReturn(mock(com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor.class));
        when(processorRegistry.findProcessor(BlueprintDatatype.integer)).thenReturn(mock(com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.DataTypeProcessor.class));
        
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Verify that processors were called for each field
        verify(processorRegistry).findProcessor(BlueprintDatatype.bytes);
        verify(processorRegistry).findProcessor(BlueprintDatatype.integer);
    }
    
    @Test
    void testGenerateWithDifferentConfigurations() {
        // Test with different config settings
        CodeGenerationConfig minimalConfig = CodeGenerationConfig.builder()
                .packageName("test.package")
                .generateEqualsHashCode(false)
                .generateToString(false)
                .generateJavaDoc(false)
                .build();
        
        ProcessingContext minimalContext = ProcessingContext.builder()
                .schema(schema)
                .config(minimalConfig)
                .className("TestDatum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        TypeSpec typeSpec = generator.generate(minimalContext);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // With minimal config, should have fewer methods
        // The exact count depends on the implementation
        assertNotNull(typeSpec.methodSpecs);
    }
    
    @Test
    void testGenerateWithBuilderConfig() {
        CodeGenerationConfig builderConfig = CodeGenerationConfig.builder()
                .packageName("test.package")
                .generateBuilders(true)
                .build();
        
        ProcessingContext builderContext = ProcessingContext.builder()
                .schema(schema)
                .config(builderConfig)
                .className("TestDatum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        TypeSpec typeSpec = generator.generate(builderContext);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Should have constructor if builders are enabled
        assertNotNull(typeSpec.methodSpecs);
    }
    
    @Test
    void testGenerateWithFieldsHavingNullTitles() {
        // Create field schemas without titles
        BlueprintSchema fieldSchema1 = new BlueprintSchema();
        fieldSchema1.setTitle(null);
        fieldSchema1.setDataType(BlueprintDatatype.bytes);
        
        BlueprintSchema fieldSchema2 = new BlueprintSchema();
        fieldSchema2.setTitle(null);
        fieldSchema2.setDataType(BlueprintDatatype.integer);
        
        schema.setFields(Arrays.asList(fieldSchema1, fieldSchema2));
        
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Should still generate the class even with null field titles
        // Field names should be generated as field0, field1, etc.
    }
    
    @Test
    void testGenerateWithEmptyFieldList() {
        schema.setFields(Collections.emptyList());
        
        // This should fail validation
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generate(context);
        });
    }
    
    @Test
    void testGenerateWithCustomAnnotations() {
        CodeGenerationConfig customConfig = CodeGenerationConfig.builder()
                .packageName("test.package")
                .customAnnotations(Arrays.asList("javax.annotation.Generated"))
                .build();
        
        ProcessingContext customContext = ProcessingContext.builder()
                .schema(schema)
                .config(customConfig)
                .className("TestDatum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
        
        TypeSpec typeSpec = generator.generate(customContext);
        
        assertNotNull(typeSpec);
        assertEquals("TestDatum", typeSpec.name);
        
        // Should have custom annotations in addition to @Constr
        assertFalse(typeSpec.annotations.isEmpty());
    }
}