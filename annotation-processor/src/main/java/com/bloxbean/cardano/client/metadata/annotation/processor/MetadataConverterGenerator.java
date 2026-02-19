package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    private static final ClassName STRING_UTILS =
            ClassName.get("com.bloxbean.cardano.client.util", "StringUtils");
    private static final ClassName HEX_UTIL =
            ClassName.get("com.bloxbean.cardano.client.util", "HexUtil");

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

    // -------------------------------------------------------------------------
    // toMetadataMap
    // -------------------------------------------------------------------------

    private MethodSpec buildToMetadataMapMethod(ClassName targetClass, String simpleClassName,
                                                List<MetadataFieldInfo> fields) {
        String paramName = firstLowerCase(simpleClassName);

        MethodSpec.Builder builder = MethodSpec.methodBuilder("toMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(MetadataMap.class)
                .addParameter(targetClass, paramName);

        builder.addStatement("$T map = $T.createMap()", MetadataMap.class, MetadataBuilder.class);

        for (MetadataFieldInfo field : fields) {
            String getExpr = buildGetExpression(paramName, field);
            boolean needsNullCheck = needsNullCheck(field.getJavaTypeName());

            if (needsNullCheck) {
                builder.beginControlFlow("if ($L != null)", getExpr);
            }

            emitToMapPut(builder, field, getExpr);

            if (needsNullCheck) {
                builder.endControlFlow();
            }
        }

        builder.addStatement("return map");
        return builder.build();
    }

    private void emitToMapPut(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        String key = field.getMetadataKey();
        String javaType = field.getJavaTypeName();
        MetadataFieldType as = field.getAs();

        switch (as) {
            case STRING_HEX:
                builder.addStatement("map.put($S, $T.encodeHexString($L))", key, HEX_UTIL, getExpr);
                break;
            case STRING_BASE64:
                builder.addStatement("map.put($S, $T.getEncoder().encodeToString($L))",
                        key, Base64.class, getExpr);
                break;
            case STRING:
                emitToMapPutAsString(builder, key, javaType, getExpr);
                break;
            default: // DEFAULT
                emitToMapPutDefault(builder, key, javaType, getExpr);
                break;
        }
    }

    /** Emit serialization for {@code as = STRING}: numerics become String.valueOf, String stays String. */
    private void emitToMapPutAsString(MethodSpec.Builder builder, String key,
                                      String javaType, String getExpr) {
        switch (javaType) {
            case "java.lang.String":
                // STRING on String is identical to DEFAULT — still needs 64-byte split logic
                emitStringToMap(builder, key, getExpr);
                break;
            case "java.math.BigInteger":
                builder.addStatement("map.put($S, $L.toString())", key, getExpr);
                break;
            case "java.math.BigDecimal":
                // DEFAULT for BigDecimal is already text — route through default
                emitToMapPutDefault(builder, key, javaType, getExpr);
                break;
            case "java.lang.Long":
            case "long":
            case "java.lang.Integer":
            case "int":
            case "java.lang.Short":
            case "short":
            case "java.lang.Byte":
            case "byte":
            case "java.lang.Boolean":
            case "boolean":
                builder.addStatement("map.put($S, $T.valueOf($L))", key, String.class, getExpr);
                break;
            case "java.lang.Double":
            case "double":
            case "java.lang.Float":
            case "float":
            case "java.lang.Character":
            case "char":
                // DEFAULT for these types is already String — no-op
                emitToMapPutDefault(builder, key, javaType, getExpr);
                break;
            default:
                break;
        }
    }

    /** Emit serialization for {@code as = DEFAULT}: natural Cardano type mapping. */
    private void emitToMapPutDefault(MethodSpec.Builder builder, String key,
                                     String javaType, String getExpr) {
        switch (javaType) {
            case "java.lang.String":
                emitStringToMap(builder, key, getExpr);
                break;
            case "byte[]":
                builder.addStatement("map.put($S, $L)", key, getExpr);
                break;
            case "java.math.BigInteger":
                builder.addStatement("map.put($S, $L)", key, getExpr);
                break;
            case "java.math.BigDecimal":
                builder.addStatement("map.put($S, $L.toPlainString())", key, getExpr);
                break;
            case "java.lang.Long":
            case "long":
                builder.addStatement("map.put($S, $T.valueOf($L))", key, BigInteger.class, getExpr);
                break;
            case "java.lang.Integer":
            case "int":
                builder.addStatement("map.put($S, $T.valueOf((long) $L))", key, BigInteger.class, getExpr);
                break;
            case "java.lang.Short":
            case "short":
            case "java.lang.Byte":
            case "byte":
                builder.addStatement("map.put($S, $T.valueOf((long) $L))", key, BigInteger.class, getExpr);
                break;
            case "java.lang.Boolean":
            case "boolean":
                builder.addStatement("map.put($S, $L ? $T.ONE : $T.ZERO)", key, getExpr,
                        BigInteger.class, BigInteger.class);
                break;
            case "java.lang.Double":
            case "double":
            case "java.lang.Float":
            case "float":
            case "java.lang.Character":
            case "char":
                builder.addStatement("map.put($S, $T.valueOf($L))", key, String.class, getExpr);
                break;
            default:
                break;
        }
    }

    /**
     * Emits the 64-byte split logic for String fields.
     * Strings > 64 UTF-8 bytes are stored as a MetadataList of chunks; shorter ones stored directly.
     */
    private void emitStringToMap(MethodSpec.Builder builder, String key, String getExpr) {
        builder.beginControlFlow("if ($L.getBytes($T.UTF_8).length > 64)", getExpr, StandardCharsets.class);
        builder.addStatement("$T _chunks = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _part : $T.splitStringEveryNCharacters($L, 64))",
                String.class, STRING_UTILS, getExpr);
        builder.addStatement("_chunks.add(_part)");
        builder.endControlFlow();
        builder.addStatement("map.put($S, _chunks)", key);
        builder.nextControlFlow("else");
        builder.addStatement("map.put($S, $L)", key, getExpr);
        builder.endControlFlow();
    }

    // -------------------------------------------------------------------------
    // fromMetadataMap
    // -------------------------------------------------------------------------

    private MethodSpec buildFromMetadataMapMethod(ClassName targetClass, String simpleClassName,
                                                  List<MetadataFieldInfo> fields) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("fromMetadataMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClass)
                .addParameter(MetadataMap.class, "map");

        builder.addStatement("$T obj = new $T()", targetClass, targetClass);
        builder.addStatement("$T v", Object.class);

        for (MetadataFieldInfo field : fields) {
            builder.addStatement("v = map.get($S)", field.getMetadataKey());
            emitFromMapGet(builder, field);
        }

        builder.addStatement("return obj");
        return builder.build();
    }

    private void emitFromMapGet(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String javaType = field.getJavaTypeName();
        MetadataFieldType as = field.getAs();

        switch (as) {
            case STRING_HEX:
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.decodeHexString((String) v)", HEX_UTIL);
                builder.endControlFlow();
                break;
            case STRING_BASE64:
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.getDecoder().decode((String) v)", Base64.class);
                builder.endControlFlow();
                break;
            case STRING:
                emitFromMapGetAsString(builder, field, javaType);
                break;
            default: // DEFAULT
                emitFromMapGetDefault(builder, field, javaType);
                break;
        }
    }

    /** Emit deserialization for {@code as = STRING}: value on chain is a String, parse back to Java type. */
    private void emitFromMapGetAsString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        String javaType) {
        switch (javaType) {
            case "java.lang.String":
                // STRING on String — same as DEFAULT, still handles MetadataList chunks
                emitStringFromMap(builder, field);
                break;
            case "java.math.BigInteger":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "new $T((String) v)", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.math.BigDecimal":
                // DEFAULT for BigDecimal is already text — route through default
                emitFromMapGetDefault(builder, field, javaType);
                break;
            case "java.lang.Long":
            case "long":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseLong((String) v)", Long.class);
                builder.endControlFlow();
                break;
            case "java.lang.Integer":
            case "int":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseInt((String) v)", Integer.class);
                builder.endControlFlow();
                break;
            case "java.lang.Short":
            case "short":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseShort((String) v)", Short.class);
                builder.endControlFlow();
                break;
            case "java.lang.Byte":
            case "byte":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseByte((String) v)", Byte.class);
                builder.endControlFlow();
                break;
            case "java.lang.Boolean":
            case "boolean":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseBoolean((String) v)", Boolean.class);
                builder.endControlFlow();
                break;
            case "java.lang.Double":
            case "double":
            case "java.lang.Float":
            case "float":
            case "java.lang.Character":
            case "char":
                // DEFAULT for these types is already String — route through default deserialization
                emitFromMapGetDefault(builder, field, javaType);
                break;
            default:
                break;
        }
    }

    /** Emit deserialization for {@code as = DEFAULT}: natural Cardano type mapping. */
    private void emitFromMapGetDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                       String javaType) {
        switch (javaType) {
            case "java.lang.String":
                emitStringFromMap(builder, field);
                break;
            case "java.math.BigInteger":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addSetterStatement(builder, field, "($T) v", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.math.BigDecimal":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "new $T((String) v)", BigDecimal.class);
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
            case "java.lang.Short":
            case "short":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addSetterStatement(builder, field, "(($T) v).shortValue()", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Byte":
            case "byte":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addSetterStatement(builder, field, "(($T) v).byteValue()", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Boolean":
            case "boolean":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addSetterStatement(builder, field, "$T.ONE.equals(v)", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Double":
            case "double":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseDouble((String) v)", Double.class);
                builder.endControlFlow();
                break;
            case "java.lang.Float":
            case "float":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatement(builder, field, "$T.parseFloat((String) v)", Float.class);
                builder.endControlFlow();
                break;
            case "java.lang.Character":
            case "char":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addSetterStatementRaw(builder, field, "((String) v).charAt(0)");
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

    /**
     * Emits the String field read: handles both a plain {@code String} value and a
     * {@code MetadataList} of chunks (produced when the string exceeded 64 bytes on write).
     */
    private void emitStringFromMap(MethodSpec.Builder builder, MetadataFieldInfo field) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        addSetterStatement(builder, field, "($T) v", String.class);
        builder.nextControlFlow("else if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
        builder.addStatement("$T _list = ($T) v", MetadataList.class, MetadataList.class);
        builder.beginControlFlow("for (int _i = 0; _i < _list.size(); _i++)");
        builder.addStatement("$T _chunk = _list.getValueAt(_i)", Object.class);
        builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
        builder.addStatement("_sb.append(($T) _chunk)", String.class);
        builder.endControlFlow();
        builder.endControlFlow();
        addSetterStatementRaw(builder, field, "_sb.toString()");
        builder.endControlFlow();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a setter call (or direct field assignment) where the value expression
     * contains a single {@code $T} placeholder resolved to {@code typeArg}.
     * {@code typeArg} may be a {@code Class<?>} or a JavaPoet {@code ClassName}.
     */
    private void addSetterStatement(MethodSpec.Builder builder, MetadataFieldInfo field,
                                    String valueExprTemplate, Object typeArg) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L(" + valueExprTemplate + ")", field.getSetterName(), typeArg);
        } else {
            builder.addStatement("obj.$L = " + valueExprTemplate, field.getJavaFieldName(), typeArg);
        }
    }

    /** Emits a setter call (or direct field assignment) with no {@code $T} placeholder. */
    private void addSetterStatementRaw(MethodSpec.Builder builder, MetadataFieldInfo field,
                                       String valueExpr) {
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
        switch (typeName) {
            case "int":
            case "long":
            case "short":
            case "byte":
            case "boolean":
            case "double":
            case "float":
            case "char":
                return false;
            default:
                return true;
        }
    }

    private String firstLowerCase(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
