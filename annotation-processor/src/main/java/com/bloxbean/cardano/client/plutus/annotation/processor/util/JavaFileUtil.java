package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintDatatype;
import com.bloxbean.cardano.client.plutus.blueprint.model.BlueprintSchema;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.CaseUtils;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.Writer;

@Slf4j
public class JavaFileUtil {

    /**
     * First character has to be upper case when creating a new class
     * @param s
     * @return
     */
    public static String firstUpperCase(String s) {
        if(s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Converts a string to camel case
     * @param s
     * @return
     */
    public static String toCamelCase(String s) {
        if (s == null || s.isEmpty())
            return s;

        return CaseUtils.toCamelCase(s, true, '_', ' ', '-');
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
        JavaFile javaFile = JavaFile.builder(packageName, build)
                .build();

        JavaFileObject builderFile = null;
        try {

            String fullClassName = packageName + "." + className;
            builderFile = processingEnv.getFiler()
                    .createSourceFile(fullClassName);
            Writer writer = builderFile.openWriter();
            javaFile.writeTo(writer);
            writer.close();
        } catch (Exception e) {
            log.error("Error creating validator class", e);
            warn(processingEnv, null, "Error creating class: %s, package: %s, error: %s", className, packageName, e.getMessage());
        }
    }

    public static String buildClassName(BlueprintSchema schema, String suffix, String title, String prefix) {
        String className = firstUpperCase(prefix) + firstUpperCase(title);
        if(schema.getDataType() == BlueprintDatatype.constructor)
            className += String.valueOf(schema.getIndex());
        className += firstUpperCase(suffix); // ToDO need to check for valid names
        return className;
    }

    private static void error(ProcessingEnvironment processingEnv, Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    private static void warn(ProcessingEnvironment processingEnv, Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                String.format(msg, args),
                e);
    }
}
