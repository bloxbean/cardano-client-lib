package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.util.Set;

/**
 * Code generation strategy for {@link BigInteger} fields.
 *
 * <ul>
 *   <li>DEFAULT: direct pass-through (BigInteger is a native Cardano metadata type)</li>
 *   <li>STRING: {@code bigInt.toString()} / {@code new BigInteger(String)}</li>
 * </ul>
 */
public class BigIntegerCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.math.BigInteger");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"($T) " + castVar, BigInteger.class};
    }

    // STRING enc: store as text

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        builder.addStatement("map.put($S, $L.toString())", key, getExpr);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "new $T((String) v)", BigInteger.class);
        builder.endControlFlow();
    }
}
