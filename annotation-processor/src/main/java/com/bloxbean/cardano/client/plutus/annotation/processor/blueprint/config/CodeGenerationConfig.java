package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.config;

import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration object for Blueprint code generation.
 * Uses builder pattern for flexible configuration.
 */
public final class CodeGenerationConfig {
    
    private final String packageName;
    private final NamingStrategy namingStrategy;
    private final boolean generateBuilders;
    private final boolean generateEqualsHashCode;
    private final boolean generateToString;
    private final boolean generateJavaDoc;
    private final List<String> customAnnotations;
    private final AccessModifier defaultFieldAccess;
    private final String author;
    private final boolean enableValidation;
    private final boolean enableCaching;
    
    private CodeGenerationConfig(Builder builder) {
        this.packageName = Objects.requireNonNull(builder.packageName, "Package name cannot be null");
        this.namingStrategy = builder.namingStrategy;
        this.generateBuilders = builder.generateBuilders;
        this.generateEqualsHashCode = builder.generateEqualsHashCode;
        this.generateToString = builder.generateToString;
        this.generateJavaDoc = builder.generateJavaDoc;
        this.customAnnotations = List.copyOf(builder.customAnnotations);
        this.defaultFieldAccess = builder.defaultFieldAccess;
        this.author = builder.author;
        this.enableValidation = builder.enableValidation;
        this.enableCaching = builder.enableCaching;
    }
    
    // Static factory methods
    public static Builder builder() {
        return new Builder();
    }
    
    public static Builder defaultConfig() {
        return new Builder()
            .namingStrategy(NamingStrategy.CAMEL_CASE)
            .generateBuilders(false)
            .generateEqualsHashCode(false)
            .generateToString(false)
            .generateJavaDoc(true)
            .defaultFieldAccess(AccessModifier.PRIVATE)
            .author("Blueprint Code Generator")
            .enableValidation(true)
            .enableCaching(true);
    }
    
    // Getters
    public String getPackageName() { return packageName; }
    public NamingStrategy getNamingStrategy() { return namingStrategy; }
    public boolean isGenerateBuilders() { return generateBuilders; }
    public boolean isGenerateEqualsHashCode() { return generateEqualsHashCode; }
    public boolean isGenerateToString() { return generateToString; }
    public boolean isGenerateJavaDoc() { return generateJavaDoc; }
    public List<String> getCustomAnnotations() { return customAnnotations; }
    public AccessModifier getDefaultFieldAccess() { return defaultFieldAccess; }
    public String getAuthor() { return author; }
    public boolean isEnableValidation() { return enableValidation; }
    public boolean isEnableCaching() { return enableCaching; }
    
    // Builder class
    public static class Builder {
        private String packageName;
        private NamingStrategy namingStrategy = NamingStrategy.CAMEL_CASE;
        private boolean generateBuilders = false;
        private boolean generateEqualsHashCode = false;
        private boolean generateToString = false;
        private boolean generateJavaDoc = true;
        private List<String> customAnnotations = List.of();
        private AccessModifier defaultFieldAccess = AccessModifier.PRIVATE;
        private String author = "Blueprint Code Generator";
        private boolean enableValidation = true;
        private boolean enableCaching = true;
        
        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }
        
        public Builder namingStrategy(NamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
            return this;
        }
        
        public Builder generateBuilders(boolean generateBuilders) {
            this.generateBuilders = generateBuilders;
            return this;
        }
        
        public Builder generateEqualsHashCode(boolean generateEqualsHashCode) {
            this.generateEqualsHashCode = generateEqualsHashCode;
            return this;
        }
        
        public Builder generateToString(boolean generateToString) {
            this.generateToString = generateToString;
            return this;
        }
        
        public Builder generateJavaDoc(boolean generateJavaDoc) {
            this.generateJavaDoc = generateJavaDoc;
            return this;
        }
        
        public Builder customAnnotations(List<String> customAnnotations) {
            this.customAnnotations = customAnnotations != null ? List.copyOf(customAnnotations) : List.of();
            return this;
        }
        
        public Builder defaultFieldAccess(AccessModifier defaultFieldAccess) {
            this.defaultFieldAccess = defaultFieldAccess;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder enableValidation(boolean enableValidation) {
            this.enableValidation = enableValidation;
            return this;
        }
        
        public Builder enableCaching(boolean enableCaching) {
            this.enableCaching = enableCaching;
            return this;
        }
        
        public CodeGenerationConfig build() {
            return new CodeGenerationConfig(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CodeGenerationConfig that = (CodeGenerationConfig) o;
        return generateBuilders == that.generateBuilders &&
               generateEqualsHashCode == that.generateEqualsHashCode &&
               generateToString == that.generateToString &&
               generateJavaDoc == that.generateJavaDoc &&
               enableValidation == that.enableValidation &&
               enableCaching == that.enableCaching &&
               Objects.equals(packageName, that.packageName) &&
               namingStrategy == that.namingStrategy &&
               Objects.equals(customAnnotations, that.customAnnotations) &&
               defaultFieldAccess == that.defaultFieldAccess &&
               Objects.equals(author, that.author);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(packageName, namingStrategy, generateBuilders, generateEqualsHashCode,
                          generateToString, generateJavaDoc, customAnnotations, defaultFieldAccess,
                          author, enableValidation, enableCaching);
    }
    
    @Override
    public String toString() {
        return "CodeGenerationConfig{" +
               "packageName='" + packageName + '\'' +
               ", namingStrategy=" + namingStrategy +
               ", generateBuilders=" + generateBuilders +
               ", generateEqualsHashCode=" + generateEqualsHashCode +
               ", generateToString=" + generateToString +
               ", generateJavaDoc=" + generateJavaDoc +
               ", customAnnotations=" + customAnnotations +
               ", defaultFieldAccess=" + defaultFieldAccess +
               ", author='" + author + '\'' +
               ", enableValidation=" + enableValidation +
               ", enableCaching=" + enableCaching +
               '}';
    }
}