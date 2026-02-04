package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import com.bloxbean.cardano.client.plutus.annotation.processor.util.JavaFileUtil;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Central wrapper for writing generated sources.
 * Delegates to JavaFileUtil to preserve existing behavior.
 */
public class SourceWriter {
    private final ProcessingEnvironment processingEnv;

    public SourceWriter(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void write(String packageName, TypeSpec typeSpec, String className) {
        JavaFileUtil.createJavaFile(packageName, typeSpec, className, processingEnv);
    }
}

