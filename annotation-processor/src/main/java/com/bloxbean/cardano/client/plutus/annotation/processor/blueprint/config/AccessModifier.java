package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;

/**
 * Enumeration of Java access modifiers for generated code.
 * Provides utility methods to apply modifiers to JavaPoet builders.
 */
public enum AccessModifier {
    
    PRIVATE("private", Modifier.PRIVATE),
    PROTECTED("protected", Modifier.PROTECTED),
    PUBLIC("public", Modifier.PUBLIC),
    PACKAGE_PRIVATE("package-private"); // No modifier = package-private
    
    private final String displayName;
    private final Modifier modifier;
    
    AccessModifier(String displayName, Modifier modifier) {
        this.displayName = displayName;
        this.modifier = modifier;
    }
    
    AccessModifier(String displayName) {
        this.displayName = displayName;
        this.modifier = null; // Package-private has no modifier
    }
    
    /**
     * Returns the display name of this access modifier
     * 
     * @return display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Returns the JavaPoet Modifier, or null for package-private
     * 
     * @return JavaPoet Modifier or null
     */
    public Modifier getModifier() {
        return modifier;
    }
    
    /**
     * Applies this access modifier to a FieldSpec builder
     * 
     * @param builder the FieldSpec builder
     * @return the builder with modifier applied
     */
    public FieldSpec.Builder applyTo(FieldSpec.Builder builder) {
        if (modifier != null) {
            builder.addModifiers(modifier);
        }
        return builder;
    }
    
    /**
     * Applies this access modifier to a MethodSpec builder
     * 
     * @param builder the MethodSpec builder
     * @return the builder with modifier applied
     */
    public MethodSpec.Builder applyTo(MethodSpec.Builder builder) {
        if (modifier != null) {
            builder.addModifiers(modifier);
        }
        return builder;
    }
    
    /**
     * Applies this access modifier to a TypeSpec builder
     * 
     * @param builder the TypeSpec builder
     * @return the builder with modifier applied
     */
    public TypeSpec.Builder applyTo(TypeSpec.Builder builder) {
        if (modifier != null) {
            builder.addModifiers(modifier);
        }
        return builder;
    }
    
    /**
     * Checks if this is a public modifier
     * 
     * @return true if public
     */
    public boolean isPublic() {
        return this == PUBLIC;
    }
    
    /**
     * Checks if this is a private modifier
     * 
     * @return true if private
     */
    public boolean isPrivate() {
        return this == PRIVATE;
    }
    
    /**
     * Checks if this is a protected modifier
     * 
     * @return true if protected
     */
    public boolean isProtected() {
        return this == PROTECTED;
    }
    
    /**
     * Checks if this is package-private (no modifier)
     * 
     * @return true if package-private
     */
    public boolean isPackagePrivate() {
        return this == PACKAGE_PRIVATE;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}