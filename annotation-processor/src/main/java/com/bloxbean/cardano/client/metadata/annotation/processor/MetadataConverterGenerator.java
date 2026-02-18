package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.annotation.processor.util.Constant.GENERATED_CODE;

/**
 * Generates a {@code {ClassName}MetadataConverter} class using JavaPoet.
 * The generated class contains:
 * <ul>
 *   <li>{@code toMetadataMap(T obj) -> MetadataMap}</li>
 *   <li>{@code fromMetadataMap(MetadataMap map) -> T}</li>
 * </ul>
 */
public class MetadataConverterGenerator {

    static final String CONVERTER_SUFFIX = "MetadataConverter";

    /**
     * Generates the converter TypeSpec for the given class and its fields.
     *
     * @param packageName     package of the annotated class
     * @param simpleClassName simple name of the annotated class
     * @param fields          list of fields to include in the converter
     * @return JavaPoet TypeSpec for the converter class
     */
    public TypeSpec generate(String packageName, String simpleClassName, List<MetadataFieldInfo> fields) {
        ClassName targetClass = ClassName.get(packageName, simpleClassName);
        String converterClassName = simpleClassName + CONVERTER_SUFFIX;

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(converterClassName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc(GENERATED_CODE);

        classBuilder.addMethod(buildToMetadataMapMethod(targetClass, simpleClassName, fields));
        classBuilder.addMethod(buildFromMetadataMapMethod(targetClass, simpleClassName, fields));

        return classBuilder.build();
    }

    private MethodSpec buildToMetadataMapMethod(ClassName targetClass, String simpleClassName, List<MetadataFieldInfo> fields) {
        String paramName = firstLowerCase(simpleClassName);

        MethodSpec.Builder builder = MethodSpec.methodBuilder("toMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(MetadataMap.class)
                .addParameter(targetClass, paramName);

        builder.addStatement("$T map = $T.createMap()", MetadataMap.class, MetadataBuilder.class);

        for (MetadataFieldInfo field : fields) {
            String getExpr = buildGetExpression(paramName, field);
            String key = field.getMetadataKey();
            boolean needsNullCheck = needsNullCheck(field.getJavaTypeName());

            if (needsNullCheck) {
                builder.beginControlFlow("if ($L != null)", getExpr);
            }

            switch (field.getJavaTypeName()) {
                case "java.lang.String":
                case "byte[]":
                    builder.addStatement("map.put($S, $L)", key, getExpr);
                    break;
                case "java.math.BigInteger":
                    builder.addStatement("map.put($S, $L)", key, getExpr);
                    break;
                case "java.lang.Long":
                case "long":
                    builder.addStatement("map.put($S, $T.valueOf($L))", key, BigInteger.class, getExpr);
                    break;
                case "java.lang.Integer":
                case "int":
                    builder.addStatement("map.put($S, $T.valueOf((long) $L))", key, BigInteger.class, getExpr);
                    break;
                default:
                    break;
            }

            if (needsNullCheck) {
                builder.endControlFlow();
            }
        }

        builder.addStatement("return map");

        return builder.build();
    }

    private MethodSpec buildFromMetadataMapMethod(ClassName targetClass, String simpleClassName, List<MetadataFieldInfo> fields) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("fromMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClass)
                .addParameter(MetadataMap.class, "map");

        builder.addStatement("$T obj = new $T()", targetClass, targetClass);
        builder.addStatement("$T v", Object.class);

        for (MetadataFieldInfo field : fields) {
            builder.addStatement("v = map.get($S)", field.getMetadataKey());

            switch (field.getJavaTypeName()) {
                case "java.lang.String":
                    builder.beginControlFlow("if (v instanceof $T)", String.class);
                    addSetterStatement(builder, field, "($T) v", String.class);
                    builder.endControlFlow();
                    break;
                case "java.math.BigInteger":
                    builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                    addSetterStatement(builder, field, "($T) v", BigInteger.class);
                    builder.endControlFlow();
                    break;
                case "java.lang.Long":
                case "long":
                    builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                    addSetterStatement(builder, field, "(($T) v).longValue()", BigInteger.class);
                    builder.endControlFlow();
                    break;
                case "java.lang.Integer":
                case "int":
                    builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                    addSetterStatement(builder, field, "(($T) v).intValue()", BigInteger.class);
                    builder.endControlFlow();
                    break;
                case "byte[]":
                    builder.beginControlFlow("if (v instanceof byte[])");
                    addSetterStatementRaw(builder, field, "(byte[]) v");
                    builder.endControlFlow();
                    break;
                default:
                    break;
            }
        }

        builder.addStatement("return obj");

        return builder.build();
    }

    /**
     * Adds a setter statement where the value expression contains a $T placeholder.
     * E.g. "($T) v" or "(($T) v).longValue()"
     */
    private void addSetterStatement(MethodSpec.Builder builder, MetadataFieldInfo field,
                                    String valueExprTemplate, Class<?> typeClass) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(" + valueExprTemplate + ")", field.getSetterName(), typeClass);
        } else {
            builder.addStatement("obj.$L = " + valueExprTemplate, field.getJavaFieldName(), typeClass);
        }
    }

    /**
     * Adds a setter statement where the value expression needs no $T placeholder.
     * E.g. "(byte[]) v"
     */
    private void addSetterStatementRaw(MethodSpec.Builder builder, MetadataFieldInfo field, String valueExpr) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(" + valueExpr + ")", field.getSetterName());
        } else {
            builder.addStatement("obj.$L = " + valueExpr, field.getJavaFieldName());
        }
    }

    private String buildGetExpression(String paramName, MetadataFieldInfo field) {
        if (field.getGetterName() != null) {
            return paramName + "." + field.getGetterName() + "()";
        } else {
            return paramName + "." + field.getJavaFieldName();
        }
    }

    private boolean needsNullCheck(String typeName) {
        return !typeName.equals("int") && !typeName.equals("long");
    }

    private String firstLowerCase(String s) {
        if (s == null || s.isEmpty()) return s;

        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

}
