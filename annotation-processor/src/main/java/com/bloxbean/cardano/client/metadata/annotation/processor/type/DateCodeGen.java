package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Code generation strategy for {@link Date} fields.
 *
 * <ul>
 *   <li>DEFAULT: epoch millis as BigInteger</li>
 *   <li>STRING: ISO-8601 text via {@code toInstant().toString()} / {@code Date.from(Instant.parse())}</li>
 * </ul>
 */
public class DateCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of(DATE);
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{"$T.valueOf(" + valueExpr + ".getTime())", BigInteger.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"new $T((($T) " + castVar + ").longValue())",
                Date.class, BigInteger.class};
    }

    // STRING: ISO-8601 via Instant

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        builder.addStatement("map.put($S, $L.toInstant().toString())", key, getExpr);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSetFmt(builder, field,
                "$T.from($T.parse((String) v))", Date.class, Instant.class);
        builder.endControlFlow();
    }
}
