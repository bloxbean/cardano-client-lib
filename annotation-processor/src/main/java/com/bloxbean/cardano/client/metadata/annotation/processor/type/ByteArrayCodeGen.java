package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import java.util.Base64;
import java.util.Set;

/**
 * Code generation strategy for {@code byte[]} fields.
 *
 * <ul>
 *   <li>DEFAULT: direct pass-through (byte[] is a native Cardano metadata type)</li>
 *   <li>STRING_HEX: hex-encoded string via HexUtil</li>
 *   <li>STRING_BASE64: Base64-encoded string</li>
 * </ul>
 *
 * Note: byte[] enc=STRING is unsupported (caught by annotation processor validation).
 * This class does not implement the STRING enc methods.
 */
public class ByteArrayCodeGen extends AbstractMetadataTypeCodeGen {

    private static final ClassName HEX_UTIL =
            ClassName.get("com.bloxbean.cardano.client.util", "HexUtil");

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of(BYTE_ARRAY);
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return byte[].class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"(byte[]) " + castVar};
    }

    // Scalar deserialize uses raw expression (no $T for byte[])
    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof byte[])");
        accessor.emitSetRaw(builder, field, "(byte[]) v");
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_el instanceof byte[])");
        builder.addStatement("_result.add((byte[]) _el)");
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof byte[])");
        accessor.emitOptionalOfSetRaw(builder, field, "(byte[]) v");
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }

    // --- STRING_HEX / STRING_BASE64 handled at the enc dispatch level ---

    /** Emit STRING_HEX serialization: {@code map.put(key, HexUtil.encodeHexString(val))}. */
    public void emitSerializeHex(MethodSpec.Builder builder, String key, String getExpr) {
        builder.addStatement("map.put($S, $T.encodeHexString($L))", key, HEX_UTIL, getExpr);
    }

    /** Emit STRING_BASE64 serialization. */
    public void emitSerializeBase64(MethodSpec.Builder builder, String key, String getExpr) {
        builder.addStatement("map.put($S, $T.getEncoder().encodeToString($L))",
                key, Base64.class, getExpr);
    }

    /** Emit STRING_HEX deserialization. */
    public void emitDeserializeHex(MethodSpec.Builder builder, MetadataFieldInfo field,
                                   MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "$T.decodeHexString((String) v)", HEX_UTIL);
        builder.endControlFlow();
    }

    /** Emit STRING_BASE64 deserialization. */
    public void emitDeserializeBase64(MethodSpec.Builder builder, MetadataFieldInfo field,
                                      MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "$T.getDecoder().decode((String) v)", Base64.class);
        builder.endControlFlow();
    }

    // --- Collection element enc-aware methods ---

    /** Emit hex-encoded element serialization: {@code _list.add(HexUtil.encodeHexString(_el))}. */
    public void emitSerializeToListHex(MethodSpec.Builder builder) {
        builder.addStatement("_list.add($T.encodeHexString(_el))", HEX_UTIL);
    }

    /** Emit Base64-encoded element serialization. */
    public void emitSerializeToListBase64(MethodSpec.Builder builder) {
        builder.addStatement("_list.add($T.getEncoder().encodeToString(_el))", Base64.class);
    }

    /** Emit hex-encoded element deserialization. */
    public void emitDeserializeElementHex(MethodSpec.Builder builder) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add($T.decodeHexString((String) _el))", HEX_UTIL);
        builder.endControlFlow();
    }

    /** Emit Base64-encoded element deserialization. */
    public void emitDeserializeElementBase64(MethodSpec.Builder builder) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add($T.getDecoder().decode((String) _el))", Base64.class);
        builder.endControlFlow();
    }
}
