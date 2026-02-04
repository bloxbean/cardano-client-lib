package com.bloxbean.cardano.client.plutus.annotation.processor.blueprint.support;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 * Standardized error/warn/note reporting over Messager.
 */
public class ErrorReporter {
    private final ProcessingEnvironment processingEnv;

    public ErrorReporter(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public void error(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    public void warn(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.WARNING,
                String.format(msg, args),
                e);
    }

    public void note(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.NOTE,
                String.format(msg, args),
                e);
    }
}

