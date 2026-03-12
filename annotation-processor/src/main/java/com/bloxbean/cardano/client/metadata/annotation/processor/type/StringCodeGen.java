package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataTypeCodeGen;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Code generation strategy for {@link String} fields.
 *
 * <p>Strings > 64 UTF-8 bytes are split into a {@code MetadataList} of chunks.
 * On deserialization, both plain {@code String} and {@code MetadataList} forms are handled.
 *
 * <ul>
 *   <li>DEFAULT and STRING encoding are identical for String fields</li>
 * </ul>
 */
public class StringCodeGen implements MetadataTypeCodeGen {

    private static final ClassName STRING_UTILS =
            ClassName.get("com.bloxbean.cardano.client.util", "StringUtils");

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.lang.String");
    }

    @Override
    public boolean needsNullCheck(String javaType) {
        return true;
    }

    // --- Serialization ---

    @Override
    public void emitSerializeToMapDefault(MethodSpec.Builder builder, String key, String getExpr,
                                          String javaType) {
        emitStringToMap(builder, key, getExpr);
    }

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        // STRING on String is identical to DEFAULT
        emitStringToMap(builder, key, getExpr);
    }

    @Override
    public void emitSerializeToList(MethodSpec.Builder builder, String javaType) {
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
    }

    // --- Deserialization ---

    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        emitStringFromMap(builder, field, accessor);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        // STRING on String is identical to DEFAULT
        emitStringFromMap(builder, field, accessor);
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
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
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitOptionalOfSet(builder, field, "($T) v", String.class);
        builder.nextControlFlow("else if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
        builder.addStatement("$T _list = ($T) v", MetadataList.class, MetadataList.class);
        builder.beginControlFlow("for (int _i = 0; _i < _list.size(); _i++)");
        builder.addStatement("$T _chunk = _list.getValueAt(_i)", Object.class);
        builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
        builder.addStatement("_sb.append(($T) _chunk)", String.class);
        builder.endControlFlow();
        builder.endControlFlow();
        accessor.emitOptionalOfSetRaw(builder, field, "_sb.toString()");
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- Map value support ---

    @Override
    public void emitSerializeMapValue(MethodSpec.Builder builder, String mapVarSuffix, String javaType) {
        builder.beginControlFlow("if (_entry.getValue().getBytes($T.UTF_8).length > 64)", StandardCharsets.class);
        builder.addStatement("$T _valChunks = $T.createList()", MetadataList.class, MetadataBuilder.class);
        builder.beginControlFlow("for ($T _part : $T.splitStringEveryNCharacters(_entry.getValue(), 64))",
                String.class, STRING_UTILS);
        builder.addStatement("_valChunks.add(_part)");
        builder.endControlFlow();
        builder.addStatement("_map" + mapVarSuffix + ".put(_entry.getKey(), _valChunks)");
        builder.nextControlFlow("else");
        builder.addStatement("_map" + mapVarSuffix + ".put(_entry.getKey(), _entry.getValue())");
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeMapValue(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_val instanceof $T)", String.class);
        builder.addStatement("_result.put(($T) _k, ($T) _val)", String.class, String.class);
        builder.nextControlFlow("else if (_val instanceof $T)", MetadataList.class);
        builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
        builder.addStatement("$T _valList = ($T) _val", MetadataList.class, MetadataList.class);
        builder.beginControlFlow("for (int _j = 0; _j < _valList.size(); _j++)");
        builder.addStatement("$T _chunk = _valList.getValueAt(_j)", Object.class);
        builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
        builder.addStatement("_sb.append(($T) _chunk)", String.class);
        builder.endControlFlow();
        builder.endControlFlow();
        builder.addStatement("_result.put(($T) _k, _sb.toString())", String.class);
        builder.endControlFlow();
    }

    // --- Internal helpers ---

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

    private void emitStringFromMap(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "($T) v", String.class);
        builder.nextControlFlow("else if (v instanceof $T)", MetadataList.class);
        builder.addStatement("$T _sb = new $T()", StringBuilder.class, StringBuilder.class);
        builder.addStatement("$T _list = ($T) v", MetadataList.class, MetadataList.class);
        builder.beginControlFlow("for (int _i = 0; _i < _list.size(); _i++)");
        builder.addStatement("$T _chunk = _list.getValueAt(_i)", Object.class);
        builder.beginControlFlow("if (_chunk instanceof $T)", String.class);
        builder.addStatement("_sb.append(($T) _chunk)", String.class);
        builder.endControlFlow();
        builder.endControlFlow();
        accessor.emitSetRaw(builder, field, "_sb.toString()");
        builder.endControlFlow();
    }
}
