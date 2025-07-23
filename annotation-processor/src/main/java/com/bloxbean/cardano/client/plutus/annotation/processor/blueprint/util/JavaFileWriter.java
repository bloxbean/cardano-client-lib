package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.exception.CodeGenerationException;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic;
import java.io.IOException;

/**
 * Utility class for writing Java files using JavaPoet.
 * Provides a clean abstraction over file writing operations.
 */
public class JavaFileWriter {
    
    private final ProcessingEnvironment processingEnvironment;
    
    public JavaFileWriter(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }
    
    /**
     * Writes a TypeSpec to a Java file
     * 
     * @param packageName the package name
     * @param typeSpec the type specification
     * @throws CodeGenerationException if writing fails
     */
    public void writeJavaFile(String packageName, TypeSpec typeSpec) {
        try {
            JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
                    .build();
            
            javaFile.writeTo(processingEnvironment.getFiler());
            
            // Log successful generation
            processingEnvironment.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated class: " + packageName + "." + typeSpec.name
            );
            
        } catch (IOException e) {
            throw new CodeGenerationException(
                "Failed to write Java file for " + typeSpec.name + " in package " + packageName,
                e
            );
        }
    }
    
    /**
     * Writes a TypeSpec to a Java file with custom JavaFile configuration
     * 
     * @param javaFile the configured JavaFile
     * @throws CodeGenerationException if writing fails
     */
    public void writeJavaFile(JavaFile javaFile) {
        try {
            javaFile.writeTo(processingEnvironment.getFiler());
            
            // Log successful generation
            processingEnvironment.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated class: " + javaFile.packageName + "." + javaFile.typeSpec.name
            );
            
        } catch (IOException e) {
            throw new CodeGenerationException(
                "Failed to write Java file for " + javaFile.typeSpec.name + 
                " in package " + javaFile.packageName,
                e
            );
        }
    }
    
    /**
     * Writes a TypeSpec with additional configuration options
     * 
     * @param packageName the package name
     * @param typeSpec the type specification
     * @param skipJavaLangImports whether to skip java.lang imports
     * @param indent the indentation string
     * @throws CodeGenerationException if writing fails
     */
    public void writeJavaFile(String packageName, TypeSpec typeSpec, 
                             boolean skipJavaLangImports, String indent) {
        try {
            JavaFile.Builder builder = JavaFile.builder(packageName, typeSpec);
            
            if (skipJavaLangImports) {
                builder.skipJavaLangImports(true);
            }
            
            if (indent != null) {
                builder.indent(indent);
            }
            
            JavaFile javaFile = builder.build();
            javaFile.writeTo(processingEnvironment.getFiler());
            
            // Log successful generation
            processingEnvironment.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                "Generated class: " + packageName + "." + typeSpec.name
            );
            
        } catch (IOException e) {
            throw new CodeGenerationException(
                "Failed to write Java file for " + typeSpec.name + " in package " + packageName,
                e
            );
        }
    }
    
    /**
     * Logs an error message
     * 
     * @param message the error message
     */
    public void logError(String message) {
        processingEnvironment.getMessager().printMessage(
            Diagnostic.Kind.ERROR,
            message
        );
    }
    
    /**
     * Logs a warning message
     * 
     * @param message the warning message
     */
    public void logWarning(String message) {
        processingEnvironment.getMessager().printMessage(
            Diagnostic.Kind.WARNING,
            message
        );
    }
    
    /**
     * Logs an info message
     * 
     * @param message the info message
     */
    public void logInfo(String message) {
        processingEnvironment.getMessager().printMessage(
            Diagnostic.Kind.NOTE,
            message
        );
    }
}