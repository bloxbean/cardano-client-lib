package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config.CodeGenerationConfig;
import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.squareup.javapoet.TypeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidatorGeneratorTest {
    
    private ValidatorGenerator generator;
    private Validator validator;
    private ProcessingContext context;
    private CodeGenerationConfig config;
    
    @BeforeEach
    void setUp() {
        validator = new Validator();
        validator.setTitle("TestValidator");
        validator.setCompiledCode("590a4d590a4a01000033332332");
        validator.setHash("abcd1234");
        validator.setParameters(Collections.emptyList());
        
        generator = new ValidatorGenerator(validator);
        
        config = CodeGenerationConfig.builder()
                .packageName("test.package")
                .build();
        
        context = ProcessingContext.builder()
                .config(config)
                .className("TestValidator")
                .processingEnvironment(mock(ProcessingEnvironment.class))
                .build();
    }
    
    @Test
    void testGetType() {
        assertEquals(CodeGenerator.GeneratorType.VALIDATOR, generator.getType());
    }
    
    @Test
    void testValidateInputValid() {
        assertDoesNotThrow(() -> generator.validateInput(context));
    }
    
    @Test
    void testValidateInputNullValidator() {
        ValidatorGenerator nullValidator = new ValidatorGenerator(null);
        assertThrows(IllegalArgumentException.class, () -> {
            nullValidator.validateInput(context);
        });
    }
    
    @Test
    void testGenerate() {
        TypeSpec typeSpec = generator.generate(context);
        
        assertNotNull(typeSpec);
        assertEquals("TestValidator", typeSpec.name);
        assertEquals(TypeSpec.Kind.CLASS, typeSpec.kind);
        assertTrue(typeSpec.modifiers.contains(Modifier.PUBLIC));
        
        // Check for expected static fields
        assertEquals(3, typeSpec.fieldSpecs.size()); // SCRIPT, TITLE, HASH
        
        // Check for expected methods
        assertFalse(typeSpec.methodSpecs.isEmpty());
    }
}