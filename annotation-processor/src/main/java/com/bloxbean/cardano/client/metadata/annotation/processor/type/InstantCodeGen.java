package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;

/**
 * Code generation strategy for {@link Instant} fields.
 *
 * <ul>
 *   <li>DEFAULT: epoch seconds as BigInteger</li>
 *   <li>STRING: ISO-8601 text via {@code Instant.toString()} / {@code Instant.parse()}</li>
 * </ul>
 */
public class InstantCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.time.Instant");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{"$T.valueOf(" + valueExpr + ".getEpochSecond())", BigInteger.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.ofEpochSecond((($T) " + castVar + ").longValue())",
                Instant.class, BigInteger.class};
    }

    // STRING: ISO-8601 text

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        builder.addStatement("map.put($S, $L.toString())", key, getExpr);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "$T.parse((String) v)", Instant.class);
        builder.endControlFlow();
    }
}
