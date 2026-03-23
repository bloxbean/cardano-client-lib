package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Set;

/**
 * Code generation strategy for {@link Duration} fields.
 *
 * <ul>
 *   <li>DEFAULT: total seconds as BigInteger</li>
 *   <li>STRING: ISO-8601 text via {@code Duration.toString()} / {@code Duration.parse()}</li>
 * </ul>
 */
public class DurationCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of(DURATION);
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{"$T.valueOf(" + valueExpr + ".getSeconds())", BigInteger.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.ofSeconds((($T) " + castVar + ").longValue())",
                Duration.class, BigInteger.class};
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
        accessor.emitSet(builder, field, "$T.parse((String) v)", Duration.class);
        builder.endControlFlow();
    }
}
