package com.bloxbean.cardano.client.metadata.annotation.processor;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import lombok.RequiredArgsConstructor;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;

/**
 * Resolves getter/setter accessors for fields during metadata extraction.
 * Handles standard JavaBeans naming, Lombok-generated accessors, and direct field access.
 */
@RequiredArgsConstructor
class MetadataAccessorResolver {

    private final ProcessingEnvironment processingEnv;
    private final Messager messager;

    // ── Accessor resolution ───────────────────────────────────────────

    AccessorResult resolveAccessors(TypeElement leafTypeElement, VariableElement ve,
                                     String fieldName, boolean hasLombok) {
        String getterName = null;
        ExecutableElement getter = findGetter(leafTypeElement, ve);
        if (getter != null) {
            getterName = getter.getSimpleName().toString();
        } else if (hasLombok) {
            getterName = "get" + capitalize(fieldName);
        } else if (!isDirectlyAccessible(ve)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No getter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
            return null;
        }

        String setterName = null;
        ExecutableElement setter = findSetter(leafTypeElement, ve);
        if (setter != null) {
            setterName = setter.getSimpleName().toString();
        } else if (hasLombok) {
            setterName = "set" + capitalize(fieldName);
        } else if (!isDirectlyAccessible(ve)) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "No setter found for field '" + fieldName + "' and field is not public. Field will be skipped.", ve);
            return null;
        }

        return new AccessorResult(getterName, setterName);
    }

    // ── Accessor helpers ──────────────────────────────────────────────

    private ExecutableElement findGetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String getterMethodName = "get" + capitalize(fieldName);
        String isGetterMethodName = "is" + capitalize(fieldName);
        String fieldTypeName = variableElement.asType().toString();
        boolean isBooleanType = fieldTypeName.equals(PRIM_BOOLEAN) || fieldTypeName.equals(BOOLEAN);

        for (Element enclosedElement : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (!(enclosedElement instanceof ExecutableElement executableElement)) continue;

            String methodName = executableElement.getSimpleName().toString();
            boolean nameMatches = methodName.equals(getterMethodName)
                    || (isBooleanType && methodName.equals(isGetterMethodName));

            if (nameMatches &&
                    executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                    executableElement.getParameters().isEmpty() &&
                    executableElement.getReturnType().toString().equals(fieldTypeName)) {
                return executableElement;
            }
        }

        return null;
    }

    private ExecutableElement findSetter(TypeElement typeElement, VariableElement variableElement) {
        String fieldName = variableElement.getSimpleName().toString();
        String setterMethodName = "set" + capitalize(fieldName);

        for (Element enclosedElement : processingEnv.getElementUtils().getAllMembers(typeElement)) {
            if (!(enclosedElement instanceof ExecutableElement executableElement)) continue;

            if (executableElement.getSimpleName().toString().equals(setterMethodName) &&
                    executableElement.getModifiers().contains(Modifier.PUBLIC) &&
                    executableElement.getParameters().size() == 1 &&
                    executableElement.getParameters().get(0).asType().toString()
                            .equals(variableElement.asType().toString()) &&
                    executableElement.getReturnType().getKind().equals(TypeKind.VOID)) {
                return executableElement;
            }
        }

        return null;
    }

    private boolean isDirectlyAccessible(VariableElement ve) {
        return ve.getModifiers().contains(Modifier.PUBLIC) || ve.getModifiers().isEmpty();
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ── Inner record ──────────────────────────────────────────────────

    record AccessorResult(String getterName, String setterName) {}
}
