package com.bloxbean.cardano.client.plutus.annotation.processor.converter;

import com.bloxbean.cardano.client.plutus.annotation.processor.model.Field;
import com.squareup.javapoet.CodeBlock;

import java.util.Objects;

/**
 * Encapsulates field access patterns: getter/setter names, null checks.
 */
public class FieldAccessor {

    public String fieldOrGetter(Field field) {
        if (field.isHashGetter()) {
            return field.getGetterName() + "()";
        } else {
            return field.getName();
        }
    }

    public String setter(String fieldName) {
        return "set" + capitalize(fieldName);
    }

    public CodeBlock nullCheck(Field field) {
        return nullCheck(field.getName(), fieldOrGetter(field));
    }

    public CodeBlock nullCheck(String fieldName, String fieldOrGetterName) {
        return CodeBlock.builder()
                .addStatement("$T.requireNonNull(obj.$L, \"$L cannot be null\")", Objects.class, fieldOrGetterName, fieldName)
                .build();
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
