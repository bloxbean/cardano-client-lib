package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.generator;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.processor.context.ProcessingContext;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Abstract base generator using the Template Method pattern.
 * Provides a consistent structure for all code generators while allowing customization.
 * 
 * This replaces the ad-hoc code generation in the original processors.
 */
public abstract class BaseGenerator implements CodeGenerator {
    
    /**
     * Template method that defines the generation process.
     * Subclasses can override individual steps while maintaining the overall structure.
     */
    @Override
    public final TypeSpec generate(ProcessingContext context) {
        validateInput(context);
        
        TypeSpec.Builder builder = createBuilder(context);
        
        addAnnotations(builder, context);
        addJavaDoc(builder, context);
        addFields(builder, context);
        addMethods(builder, context);
        addNestedTypes(builder, context);
        
        return finalizeBuilder(builder, context);
    }
    
    /**
     * Creates the initial TypeSpec builder.
     * Subclasses must implement this to define the type (class, interface, enum).
     */
    protected abstract TypeSpec.Builder createBuilder(ProcessingContext context);
    
    /**
     * Adds annotations to the type.
     * Default implementation adds common annotations based on context.
     */
    protected void addAnnotations(TypeSpec.Builder builder, ProcessingContext context) {
        // Add custom annotations from config
        for (String annotation : context.getConfig().getCustomAnnotations()) {
            try {
                Class<?> annotationClass = Class.forName(annotation);
                builder.addAnnotation(annotationClass);
            } catch (ClassNotFoundException e) {
                // Log warning but continue generation
            }
        }
    }
    
    /**
     * Adds JavaDoc to the type.
     * Default implementation adds basic documentation.
     */
    protected void addJavaDoc(TypeSpec.Builder builder, ProcessingContext context) {
        if (context.getConfig().isGenerateJavaDoc()) {
            StringBuilder javaDoc = new StringBuilder();
            
            // Add schema description
            if (context.getSchema().getDescription() != null) {
                javaDoc.append(context.getSchema().getDescription());
            }
            
            // Add generation info
            if (javaDoc.length() > 0) javaDoc.append("\n\n");
            javaDoc.append("Auto generated code. DO NOT MODIFY");
            
            if (context.getConfig().getAuthor() != null) {
                javaDoc.append("\n@author ").append(context.getConfig().getAuthor());
            }
            
            builder.addJavadoc(javaDoc.toString());
        }
    }
    
    /**
     * Adds fields to the type.
     * Default implementation is empty - subclasses should override.
     */
    protected void addFields(TypeSpec.Builder builder, ProcessingContext context) {
        // Default: no fields
    }
    
    /**
     * Adds methods to the type.
     * Default implementation adds common methods based on configuration.
     */
    protected void addMethods(TypeSpec.Builder builder, ProcessingContext context) {
        if (context.getConfig().isGenerateEqualsHashCode()) {
            addEqualsAndHashCode(builder, context);
        }
        
        if (context.getConfig().isGenerateToString()) {
            addToString(builder, context);
        }
    }
    
    /**
     * Adds nested types to the type.
     * Default implementation is empty - subclasses should override if needed.
     */
    protected void addNestedTypes(TypeSpec.Builder builder, ProcessingContext context) {
        // Default: no nested types
    }
    
    /**
     * Finalizes the builder before creating the TypeSpec.
     * Subclasses can override for final modifications.
     */
    protected TypeSpec finalizeBuilder(TypeSpec.Builder builder, ProcessingContext context) {
        return builder.build();
    }
    
    /**
     * Helper method to add equals and hashCode methods
     */
    protected void addEqualsAndHashCode(TypeSpec.Builder builder, ProcessingContext context) {
        // Extract fields for equals/hashCode generation
        List<FieldSpec> fields = extractFields(builder);
        
        if (!fields.isEmpty()) {
            builder.addMethod(generateEquals(fields, context));
            builder.addMethod(generateHashCode(fields, context));
        }
    }
    
    /**
     * Helper method to add toString method
     */
    protected void addToString(TypeSpec.Builder builder, ProcessingContext context) {
        List<FieldSpec> fields = extractFields(builder);
        
        if (!fields.isEmpty()) {
            builder.addMethod(generateToString(fields, context));
        }
    }
    
    /**
     * Extracts fields from the builder for utility method generation
     */
    protected List<FieldSpec> extractFields(TypeSpec.Builder builder) {
        // This is a simplified version - real implementation would extract from builder
        return List.of();
    }
    
    /**
     * Generates equals method
     */
    protected MethodSpec generateEquals(List<FieldSpec> fields, ProcessingContext context) {
        MethodSpec.Builder equalsBuilder = MethodSpec.methodBuilder("equals")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(boolean.class)
                .addParameter(Object.class, "o");
        
        equalsBuilder.addStatement("if (this == o) return true");
        equalsBuilder.addStatement("if (o == null || getClass() != o.getClass()) return false");
        
        String className = context.getEffectiveClassName();
        equalsBuilder.addStatement("$L that = ($L) o", className, className);
        
        // Add field comparisons
        for (FieldSpec field : fields) {
            equalsBuilder.addStatement("if (!$T.equals($L, that.$L)) return false", 
                    java.util.Objects.class, field.name, field.name);
        }
        
        equalsBuilder.addStatement("return true");
        
        return equalsBuilder.build();
    }
    
    /**
     * Generates hashCode method
     */
    protected MethodSpec generateHashCode(List<FieldSpec> fields, ProcessingContext context) {
        MethodSpec.Builder hashCodeBuilder = MethodSpec.methodBuilder("hashCode")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(int.class);
        
        StringBuilder fieldNames = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) fieldNames.append(", ");
            fieldNames.append(fields.get(i).name);
        }
        
        hashCodeBuilder.addStatement("return $T.hash($L)", 
                java.util.Objects.class, fieldNames.toString());
        
        return hashCodeBuilder.build();
    }
    
    /**
     * Generates toString method
     */
    protected MethodSpec generateToString(List<FieldSpec> fields, ProcessingContext context) {
        MethodSpec.Builder toStringBuilder = MethodSpec.methodBuilder("toString")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class);
        
        String className = context.getEffectiveClassName();
        StringBuilder format = new StringBuilder(className + "{");
        
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) format.append(", ");
            format.append(fields.get(i).name).append("='\" + ").append(fields.get(i).name).append(" + \"'");
        }
        format.append("}");
        
        toStringBuilder.addStatement("return \"$L\"", format.toString());
        
        return toStringBuilder.build();
    }
}