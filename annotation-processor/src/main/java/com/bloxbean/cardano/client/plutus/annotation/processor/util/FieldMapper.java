package com.bloxbean.cardano.client.plutus.annotation.processor.util;

import com.bloxbean.cardano.client.plutus.annotation.processor.ClassDefinitionGenerator;
import com.bloxbean.cardano.client.plutus.annotation.processor.model.*;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Converts JavaPoet {@link FieldSpec}s to model {@link Field}s.
 * Handles type detection via {@link FieldTypeDetector}, CONSTRUCTOR fallback,
 * and sets {@code converterClassFqn} on CONSTRUCTOR-typed fields.
 */
public final class FieldMapper {

    private FieldMapper() {
    }

    /**
     * Converts a list of JavaPoet FieldSpecs to model Fields.
     *
     * @param fieldSpecs  the field specs to convert
     * @param isInterface predicate that checks (packageName, simpleName) → true if the type is an interface
     * @return list of model Fields with converterClassFqn set for CONSTRUCTOR types
     */
    public static List<Field> fromSpecs(List<FieldSpec> fieldSpecs,
                                         BiPredicate<String, String> isInterface) {
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < fieldSpecs.size(); i++) {
            fields.add(fromSpec(fieldSpecs.get(i), i, isInterface));
        }
        return fields;
    }

    private static Field fromSpec(FieldSpec fs, int index,
                                   BiPredicate<String, String> isInterface) {
        FieldType ft = FieldTypeDetector.fromTypeName(fs.type);
        if (ft == null) {
            ft = new FieldType();
            ft.setFqTypeName(fs.type.toString());
            ft.setType(Type.CONSTRUCTOR);
            ft.setJavaType(new JavaType(fs.type.toString(), true));
            if (fs.type instanceof ClassName cn) {
                boolean isIface = isInterface.test(cn.packageName(), cn.simpleName());
                ft.setConverterClassFqn(ClassDefinitionGenerator.resolveConverterFqn(cn, isIface));
            }
        } else {
            fixUpGenericTypes(ft, isInterface);
        }

        String getter;
        if (Type.BOOL.equals(ft.getType()) && JavaType.BOOLEAN.equals(ft.getJavaType())) {
            getter = "is" + capitalize(fs.name);
        } else {
            getter = "get" + capitalize(fs.name);
        }

        return Field.builder()
                .name(fs.name)
                .index(index)
                .fieldType(ft)
                .hashGetter(true)
                .getterName(getter)
                .build();
    }

    /**
     * Walks generic type arguments and sets converterClassFqn for CONSTRUCTOR types.
     */
    private static void fixUpGenericTypes(FieldType fieldType,
                                           BiPredicate<String, String> isInterface) {
        for (FieldType genericType : fieldType.getGenericTypes()) {
            if (genericType.getType() == Type.CONSTRUCTOR) {
                String typeFqn = genericType.getJavaType().getName();
                try {
                    ClassName cn = ClassName.bestGuess(typeFqn);
                    boolean isIface = isInterface.test(cn.packageName(), cn.simpleName());
                    genericType.setConverterClassFqn(
                            ClassDefinitionGenerator.resolveConverterFqn(cn, isIface));
                } catch (IllegalArgumentException ignored) {
                    // bestGuess can fail for parameterized types — skip
                }
            }
            fixUpGenericTypes(genericType, isInterface);
        }
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
