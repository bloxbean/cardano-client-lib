package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatum;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.bloxbean.cardano.client.plutus.blueprint.model.Validator;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.ClassName;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generator for Validator classes using the Template Method pattern.
 * Replaces the complex validator generation logic in ValidatorProcessor.
 */
public class ValidatorGenerator extends BaseGenerator {
    
    private final Validator validator;
    
    public ValidatorGenerator(Validator validator) {
        this.validator = validator;
    }
    
    @Override
    public GeneratorType getType() {
        return GeneratorType.VALIDATOR;
    }
    
    @Override
    public void validateInput(ProcessingContext context) {
        super.validateInput(context);
        if (validator == null) {
            throw new IllegalArgumentException("Validator cannot be null for validator generation");
        }
        
        if (validator.getTitle() == null || validator.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Validator must have a valid title");
        }
        
        if (validator.getCompiledCode() == null || validator.getCompiledCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Validator must have compiled code");
        }
    }
    
    @Override
    protected TypeSpec.Builder createBuilder(ProcessingContext context) {
        String className = context.getEffectiveClassName();
        
        return TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);
    }
    
    @Override
    protected void addAnnotations(TypeSpec.Builder builder, ProcessingContext context) {
        // Add parent annotations first
        super.addAnnotations(builder, context);
        
        // Note: @PlutusScript annotation is not available in the current codebase
        // The compiled code is instead provided as a static field
    }
    
    @Override
    protected void addFields(TypeSpec.Builder builder, ProcessingContext context) {
        // Add script constant field
        FieldSpec scriptField = FieldSpec.builder(
                        String.class, 
                        "SCRIPT", 
                        Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
                )
                .initializer("$S", validator.getCompiledCode())
                .addJavadoc("The compiled Plutus script code")
                .build();
        
        builder.addField(scriptField);
        
        // Add validator title field
        FieldSpec titleField = FieldSpec.builder(
                        String.class, 
                        "TITLE", 
                        Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
                )
                .initializer("$S", validator.getTitle())
                .addJavadoc("The validator title from the blueprint")
                .build();
        
        builder.addField(titleField);
        
        // Add hash field if available
        if (validator.getHash() != null) {
            FieldSpec hashField = FieldSpec.builder(
                            String.class, 
                            "HASH", 
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
                    )
                    .initializer("$S", validator.getHash())
                    .addJavadoc("The validator script hash")
                    .build();
            
            builder.addField(hashField);
        }
    }
    
    @Override
    protected void addMethods(TypeSpec.Builder builder, ProcessingContext context) {
        // Add factory methods for creating script instances
        addScriptFactoryMethods(builder, context);
        
        // Add utility methods
        addUtilityMethods(builder, context);
        
        // Don't call super since validators don't need equals/hashCode/toString by default
        if (context.getConfig().isGenerateEqualsHashCode() || 
            context.getConfig().isGenerateToString()) {
            super.addMethods(builder, context);
        }
    }
    
    /**
     * Adds factory methods for creating script instances
     */
    private void addScriptFactoryMethods(TypeSpec.Builder builder, ProcessingContext context) {
        // Add getPlutusScript method
        TypeName plutusScriptType = ClassName.get(
            "com.bloxbean.cardano.client.plutus.spec", 
            "PlutusScript"
        );
        
        MethodSpec getScriptMethod = MethodSpec.methodBuilder("getPlutusScript")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(plutusScriptType)
                .addStatement("return $T.builder().cborHex(SCRIPT).build()", plutusScriptType)
                .addJavadoc("Creates a PlutusScript instance from the compiled code")
                .addJavadoc("@return PlutusScript instance")
                .build();
        
        builder.addMethod(getScriptMethod);
        
        // Add getScriptHash method if hash is available
        if (validator.getHash() != null) {
            MethodSpec getHashMethod = MethodSpec.methodBuilder("getScriptHash")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(String.class)
                    .addStatement("return HASH")
                    .addJavadoc("Gets the script hash")
                    .addJavadoc("@return script hash as hex string")
                    .build();
            
            builder.addMethod(getHashMethod);
        }
    }
    
    /**
     * Adds utility methods for the validator
     */
    private void addUtilityMethods(TypeSpec.Builder builder, ProcessingContext context) {
        // Add getTitle method
        MethodSpec getTitleMethod = MethodSpec.methodBuilder("getTitle")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(String.class)
                .addStatement("return TITLE")
                .addJavadoc("Gets the validator title")
                .addJavadoc("@return validator title")
                .build();
        
        builder.addMethod(getTitleMethod);
        
        // Add getCompiledCode method
        MethodSpec getCodeMethod = MethodSpec.methodBuilder("getCompiledCode")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(String.class)
                .addStatement("return SCRIPT")
                .addJavadoc("Gets the compiled script code")
                .addJavadoc("@return compiled code as CBOR hex string")
                .build();
        
        builder.addMethod(getCodeMethod);
        
        // Add validation method if parameters are defined
        if (validator.getParameters() != null && !validator.getParameters().isEmpty()) {
            addValidationMethod(builder, context);
        }
    }
    
    /**
     * Adds a validation method based on validator parameters
     */
    private void addValidationMethod(TypeSpec.Builder builder, ProcessingContext context) {
        MethodSpec.Builder validateBuilder = MethodSpec.methodBuilder("validate")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(boolean.class)
                .addJavadoc("Validates the given parameters against this validator\n")
                .addJavadoc("@return true if validation passes\n");
        
        // Add parameters based on validator definition
        for (BlueprintDatum param : validator.getParameters()) {
            String paramTitle = param.getTitle();
            String paramDesc = param.getDescription();
            
            // Use schema title/description if datum doesn't have them
            if (paramTitle == null && param.getSchema() != null) {
                paramTitle = param.getSchema().getTitle();
            }
            if (paramDesc == null && param.getSchema() != null) {
                paramDesc = param.getSchema().getDescription();
            }
            
            if (paramTitle != null) {
                validateBuilder.addParameter(Object.class, paramTitle.toLowerCase());
                validateBuilder.addJavadoc("@param $L $L\n", 
                    paramTitle.toLowerCase(), 
                    paramDesc != null ? paramDesc : "parameter"
                );
            }
        }
        
        // Simple implementation - in practice this would contain actual validation logic
        validateBuilder.addStatement("// TODO: Implement validation logic");
        validateBuilder.addStatement("return true");
        
        builder.addMethod(validateBuilder.build());
    }
    
    @Override
    protected List<FieldSpec> extractFields(TypeSpec.Builder builder) {
        // Validators have predefined fields, don't extract dynamically
        return new ArrayList<>();
    }
}