package com.bloxbean.cardano.client.metadata.annotation.processor;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.annotation.MetadataFieldType;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

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

        if (javaType.startsWith("java.util.List<") || javaType.startsWith("java.util.Set<")
                || javaType.startsWith("java.util.SortedSet<")) {
            emitToMapPutList(builder, field, getExpr);
            return;
        }

        if (javaType.startsWith("java.util.Optional<")) {
            emitToMapPutOptional(builder, field, getExpr);
            return;
        }

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

        if (javaType.startsWith("java.util.List<")) {
            emitFromMapGetCollection(builder, field,
                    ClassName.get("java.util", "List"), ClassName.get("java.util", "ArrayList"));
            return;
        }
        if (javaType.startsWith("java.util.Set<")) {
            emitFromMapGetCollection(builder, field,
                    ClassName.get("java.util", "Set"), ClassName.get("java.util", "LinkedHashSet"));
            return;
        }
        if (javaType.startsWith("java.util.SortedSet<")) {
            emitFromMapGetCollection(builder, field,
                    ClassName.get("java.util", "SortedSet"), ClassName.get("java.util", "TreeSet"));
            return;
        }

        if (javaType.startsWith("java.util.Optional<")) {
            emitFromMapGetOptional(builder, field);
            return;
        }

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
    // List<T> / Set<T> / SortedSet<T> serialization
    // -------------------------------------------------------------------------

    /** Emits the toMetadataMap body for a {@code List<T>} field. */
    private void emitToMapPutList(MethodSpec.Builder builder, MetadataFieldInfo field, String getExpr) {
        String key = field.getMetadataKey();
        String elementType = field.getElementTypeName();
        TypeName elemTypeName = elementTypeName(elementType);

        builder.addStatement("$T _list = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _el : $L)", elemTypeName, getExpr);
        builder.beginControlFlow("if (_el != null)");
        emitListElementAdd(builder, elementType);
        builder.endControlFlow(); // if not null
        builder.endControlFlow(); // for loop
        builder.addStatement("map.put($S, _list)", key);
    }

    /** Emits the add statement for a single element into {@code _list}. */
    private void emitListElementAdd(MethodSpec.Builder builder, String elementType) {
        switch (elementType) {
            case "java.lang.String":
                builder.beginControlFlow("if (_el.getBytes($T.UTF_8).length > 64)", StandardCharsets.class);
                builder.addStatement("$T _elChunks = $T.createList()", MetadataList.class, MetadataBuilder.class);
                builder.beginControlFlow("for ($T _part : $T.splitStringEveryNCharacters(_el, 64))",
                        String.class, STRING_UTILS);
                builder.addStatement("_elChunks.add(_part)");
                builder.endControlFlow();
                builder.addStatement("_list.add(_elChunks)");
                builder.nextControlFlow("else");
                builder.addStatement("_list.add(_el)");
                builder.endControlFlow();
                break;
            case "java.math.BigInteger":
                builder.addStatement("_list.add(_el)");
                break;
            case "java.math.BigDecimal":
                builder.addStatement("_list.add(_el.toPlainString())");
                break;
            case "java.lang.Long":
                builder.addStatement("_list.add($T.valueOf(_el))", BigInteger.class);
                break;
            case "java.lang.Integer":
                builder.addStatement("_list.add($T.valueOf((long) _el))", BigInteger.class);
                break;
            case "java.lang.Short":
            case "java.lang.Byte":
                builder.addStatement("_list.add($T.valueOf((long) _el))", BigInteger.class);
                break;
            case "java.lang.Boolean":
                builder.addStatement("_list.add(_el ? $T.ONE : $T.ZERO)", BigInteger.class, BigInteger.class);
                break;
            case "java.lang.Double":
            case "java.lang.Float":
            case "java.lang.Character":
                builder.addStatement("_list.add($T.valueOf(_el))", String.class);
                break;
            case "byte[]":
                builder.addStatement("_list.add(_el)");
                break;
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Optional<T> serialization / deserialization
    // -------------------------------------------------------------------------

    /**
     * Emits the toMetadataMap body for an {@code Optional<T>} field.
     * A present value is serialized identically to the corresponding plain scalar.
     * A null or absent Optional means the key is omitted (the outer null guard handles null;
     * this method adds the {@code isPresent()} check).
     */
    private void emitToMapPutOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                      String getExpr) {
        builder.beginControlFlow("if ($L.isPresent())", getExpr);
        emitToMapPutDefault(builder, field.getMetadataKey(), field.getElementTypeName(),
                getExpr + ".get()");
        builder.endControlFlow();
    }

    /**
     * Emits the fromMetadataMap body for an {@code Optional<T>} field.
     * The setter is always called: {@code Optional.of(value)} on a match, {@code Optional.empty()}
     * in the else branch. This ensures the field is always initialised, unlike plain scalars.
     */
    private void emitFromMapGetOptional(MethodSpec.Builder builder, MetadataFieldInfo field) {
        String elementType = field.getElementTypeName();
        switch (elementType) {
            case "java.lang.String":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addOptionalOfSetterWith1Arg(builder, field, "($T) v", String.class);
                builder.nextControlFlow("else if (v instanceof $T)", MetadataList.class);
                builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
                builder.addStatement("$T _list = ($T) v", MetadataList.class, MetadataList.class);
                builder.beginControlFlow("for (int _i = 0; _i < _list.size(); _i++)");
                builder.addStatement("$T _chunk = _list.getValueAt(_i)", Object.class);
                builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
                builder.addStatement("_sb.append(($T) _chunk)", String.class);
                builder.endControlFlow();
                builder.endControlFlow();
                addOptionalOfSetterRaw(builder, field, "_sb.toString()");
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.math.BigInteger":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "($T) v", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.math.BigDecimal":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addOptionalOfSetterWith1Arg(builder, field, "new $T((String) v)", BigDecimal.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Long":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "(($T) v).longValue()", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Integer":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "(($T) v).intValue()", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Short":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "(($T) v).shortValue()", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Byte":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "(($T) v).byteValue()", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Boolean":
                builder.beginControlFlow("if (v instanceof $T)", BigInteger.class);
                addOptionalOfSetterWith1Arg(builder, field, "$T.ONE.equals(v)", BigInteger.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Double":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addOptionalOfSetterWith1Arg(builder, field, "$T.parseDouble((String) v)", Double.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Float":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addOptionalOfSetterWith1Arg(builder, field, "$T.parseFloat((String) v)", Float.class);
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "java.lang.Character":
                builder.beginControlFlow("if (v instanceof $T)", String.class);
                addOptionalOfSetterRaw(builder, field, "((String) v).charAt(0)");
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            case "byte[]":
                builder.beginControlFlow("if (v instanceof byte[])");
                addOptionalOfSetterRaw(builder, field, "(byte[]) v");
                builder.nextControlFlow("else");
                addOptionalEmptySetter(builder, field);
                builder.endControlFlow();
                break;
            default:
                break;
        }
    }

    /** Emits {@code obj.setX(Optional.of(innerFmt))} where innerFmt contains one {@code $T} arg. */
    private void addOptionalOfSetterWith1Arg(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             String innerFmt, Object typeArg) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(" + innerFmt + "))",
                    field.getSetterName(), Optional.class, typeArg);
        } else {
            builder.addStatement("obj.$L = $T.of(" + innerFmt + ")",
                    field.getJavaFieldName(), Optional.class, typeArg);
        }
    }

    /** Emits {@code obj.setX(Optional.of(innerExpr))} with a raw inner expression (no {@code $T}). */
    private void addOptionalOfSetterRaw(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        String innerExpr) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.of(" + innerExpr + "))",
                    field.getSetterName(), Optional.class);
        } else {
            builder.addStatement("obj.$L = $T.of(" + innerExpr + ")",
                    field.getJavaFieldName(), Optional.class);
        }
    }

    /** Emits {@code obj.setX(Optional.empty())} (the absent / key-missing case). */
    private void addOptionalEmptySetter(MethodSpec.Builder builder, MetadataFieldInfo field) {
        if (field.getSetterName() != null) {
            builder.addStatement("obj.$L($T.empty())", field.getSetterName(), Optional.class);
        } else {
            builder.addStatement("obj.$L = $T.empty()", field.getJavaFieldName(), Optional.class);
        }
    }

    // -------------------------------------------------------------------------
    // List<T> / Set<T> / SortedSet<T> deserialization
    // -------------------------------------------------------------------------

    /** Emits the fromMetadataMap body for a {@code List<T>} or {@code Set<T>} field. */
    private void emitFromMapGetCollection(MethodSpec.Builder builder, MetadataFieldInfo field,
                                          ClassName interfaceClass, ClassName implClass) {
        String elementType = field.getElementTypeName();
        TypeName elemTypeName = elementTypeName(elementType);
        ParameterizedTypeName collectionType =
                ParameterizedTypeName.get(interfaceClass, elemTypeName);

        builder.beginControlFlow("if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _rawList = ($T) v", MetadataList.class, MetadataList.class);
        builder.addStatement("$T _result = new $T<>()", collectionType, implClass);
        builder.beginControlFlow("for (int _i = 0; _i < _rawList.size(); _i++)");
        builder.addStatement("$T _el = _rawList.getValueAt(_i)", Object.class);
        emitListElementRead(builder, elementType);
        builder.endControlFlow(); // for loop
        addSetterStatementRaw(builder, field, "_result");
        builder.endControlFlow(); // instanceof MetadataList
    }

    /** Emits the code that reads a single raw element {@code _el} and adds it to {@code _result}. */
    private void emitListElementRead(MethodSpec.Builder builder, String elementType) {
        switch (elementType) {
            case "java.lang.String":
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add(($T) _el)", String.class);
                builder.nextControlFlow("else if (_el instanceof $T)", MetadataList.class);
                builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
                builder.addStatement("$T _elList = ($T) _el", MetadataList.class, MetadataList.class);
                builder.beginControlFlow("for (int _j = 0; _j < _elList.size(); _j++)");
                builder.addStatement("$T _chunk = _elList.getValueAt(_j)", Object.class);
                builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
                builder.addStatement("_sb.append(($T) _chunk)", String.class);
                builder.endControlFlow();
                builder.endControlFlow();
                builder.addStatement("_result.add(_sb.toString())");
                builder.endControlFlow();
                break;
            case "java.math.BigInteger":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add(($T) _el)", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.math.BigDecimal":
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add(new $T(($T) _el))", BigDecimal.class, String.class);
                builder.endControlFlow();
                break;
            case "java.lang.Long":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add((($T) _el).longValue())", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Integer":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add((($T) _el).intValue())", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Short":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add((($T) _el).shortValue())", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Byte":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add((($T) _el).byteValue())", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Boolean":
                builder.beginControlFlow("if (_el instanceof $T)", BigInteger.class);
                builder.addStatement("_result.add($T.ONE.equals(_el))", BigInteger.class);
                builder.endControlFlow();
                break;
            case "java.lang.Double":
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add($T.parseDouble(($T) _el))", Double.class, String.class);
                builder.endControlFlow();
                break;
            case "java.lang.Float":
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add($T.parseFloat(($T) _el))", Float.class, String.class);
                builder.endControlFlow();
                break;
            case "java.lang.Character":
                builder.beginControlFlow("if (_el instanceof $T)", String.class);
                builder.addStatement("_result.add((($T) _el).charAt(0))", String.class);
                builder.endControlFlow();
                break;
            case "byte[]":
                builder.beginControlFlow("if (_el instanceof byte[])");
                builder.addStatement("_result.add((byte[]) _el)");
                builder.endControlFlow();
                break;
            default:
                break;
        }
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

    /** Maps a fully-qualified element type name to its JavaPoet {@link TypeName}. */
    private TypeName elementTypeName(String elementType) {
        switch (elementType) {
            case "java.lang.String":    return TypeName.get(String.class);
            case "java.math.BigInteger": return TypeName.get(BigInteger.class);
            case "java.math.BigDecimal": return TypeName.get(BigDecimal.class);
            case "java.lang.Long":      return TypeName.get(Long.class);
            case "java.lang.Integer":   return TypeName.get(Integer.class);
            case "java.lang.Short":     return TypeName.get(Short.class);
            case "java.lang.Byte":      return TypeName.get(Byte.class);
            case "java.lang.Boolean":   return TypeName.get(Boolean.class);
            case "java.lang.Double":    return TypeName.get(Double.class);
            case "java.lang.Float":     return TypeName.get(Float.class);
            case "java.lang.Character": return TypeName.get(Character.class);
            case "byte[]":              return TypeName.get(byte[].class);
            default: throw new IllegalArgumentException("Unsupported List element type: " + elementType);
        }
    }
}
