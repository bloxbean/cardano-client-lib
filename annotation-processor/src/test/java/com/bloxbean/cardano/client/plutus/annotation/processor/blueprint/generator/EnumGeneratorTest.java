package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnumGeneratorTest {
    
    private EnumGenerator generator;
    private ProcessingContext context;
    private BlueprintSchema schema;
    private CodeGenerationConfig config;
    
    @BeforeEach
    void setUp() {
        generator = new EnumGenerator();
        
        // Create anyOf schemas
        BlueprintSchema option1 = new BlueprintSchema();
        option1.setTitle("Option1");
        option1.setDataType(BlueprintDatatype.constructor);
        option1.setIndex(0);
        
        BlueprintSchema option2 = new BlueprintSchema();
        option2.setTitle("Option2");
        option2.setDataType(BlueprintDatatype.constructor);
        option2.setIndex(1);
        
        schema = new BlueprintSchema();
        schema.setTitle("TestEnum");
        schema.setAnyOf(Arrays.asList(option1, option2));
        
        config = CodeGenerationConfig.builder()
                .packageName("test.package")
                .build();
        
        context = ProcessingContext.builder()
                .schema(schema)
                .config(config)
                .className("TestEnum")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
    }
    
    @Test
    void testGetType() {
        assertEquals(CodeGenerator.GeneratorType.ENUM, generator.getType());
    }
    
    @Test
    void testValidateInputValid() {
        assertDoesNotThrow(() -> generator.validateInput(context));
    }
    
    @Test
    void testGenerate() {
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestEnum", typeSpec.name);
        assertEquals(TypeSpec.Kind.ENUM, typeSpec.kind);
        assertTrue(typeSpec.modifiers.contains(Modifier.PUBLIC));
        
        // Check enum constants
        assertEquals(2, typeSpec.enumConstants.size());
        assertTrue(typeSpec.enumConstants.containsKey("OPTION1"));
        assertTrue(typeSpec.enumConstants.containsKey("OPTION2"));
    }
}