package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.DefaultNamingStrategy;
import com.bloxbean.cardano.client.plutus.annotation.processor.util.naming.NamingStrategy;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;

@Slf4j
public class JavaFileUtil {

    public static final String CARDANO_CLIENT_LIB_GENERATED_DIR = "cardano.client.lib.generated.dir";

    // Use DefaultNamingStrategy for all naming operations
    private static final NamingStrategy namingStrategy = new DefaultNamingStrategy();

    /**
     * First character has to be upper case when creating a new class
     * @param s
     * @return
     */
    public static String firstUpperCase(String s) {
        return namingStrategy.firstUpperCase(s);
    }

    public static String firstLowerCase(String s) {
        return namingStrategy.firstLowerCase(s);
    }

    /**
     * Converts a string to camel case.
     * Handles all CIP-53 blueprint naming conventions including:
     * - Legacy Aiken (v1.0.x): List$ByteArray, Tuple$Int_Int
     * - Modern Aiken (v1.1.x+): List<Int>, aiken/crypto/Hash
     * - Module paths with tildes: types~1order~1Action
     *
     * @param s the input string
     * @return camelCase string that is a valid Java identifier
     */
    public static String toCamelCase(String s) {
        return namingStrategy.toCamelCase(s);
    }

    /**
     * Converts a string to PascalCase for class names.
     * Handles all CIP-53 blueprint naming conventions.
     *
     * @param s the input string
     * @return PascalCase string that is a valid Java class name
     */
    public static String toClassNameFormat(String s) {
        return namingStrategy.toClassName(s);
    }

    /**
     * Converts a package name to valid Java package format.
     *
     * @param pkg the package name
     * @return lowercase package name with no special characters
     */
    public static String toPackageNameFormat(String pkg) {
        return namingStrategy.toPackageNameFormat(pkg);
    }


    /**
     * Creates a Java file from a TypeSpec with a given classname and package
     *
     * @param packageName
     * @param build
     * @param className
     * @param processingEnv
     */
    public static void createJavaFile(String packageName, TypeSpec build, String className, ProcessingEnvironment processingEnv) {
        String generatedDir = processingEnv.getOptions().get(CARDANO_CLIENT_LIB_GENERATED_DIR);

        JavaFile javaFile = JavaFile.builder(packageName, build)
                .build();

        JavaFileObject builderFile = null;
        try {

            String fullClassName = packageName + "." + className;
            builderFile = processingEnv.getFiler()
                    .createSourceFile(fullClassName);

            if (generatedDir == null) {
                Writer writer = builderFile.openWriter();
                javaFile.writeTo(writer);
                writer.close();
            } else {
                javaFile.writeTo(new java.io.File(generatedDir));
            }
        } catch (Exception e) {
            log.error("Error creating class : " + className, e);
            warn(processingEnv, null, "Error creating class: %s, package: %s, error: %s", className, packageName, e.getMessage());
        }
    }

    public static void error(ProcessingEnvironment processingEnv, Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    public static void warn(ProcessingEnvironment processingEnv, Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                String.format(msg, args),
                e);
    }
}
