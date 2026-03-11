package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.math.BigInteger;
import java.util.Set;

/**
 * Code generation strategy for {@code boolean} / {@link Boolean} fields.
 *
 * <ul>
 *   <li>DEFAULT: {@code BigInteger.ONE} / {@code BigInteger.ZERO}</li>
 *   <li>STRING: {@code String.valueOf()} / {@code Boolean.parseBoolean()}</li>
 * </ul>
 */
public class BooleanCodeGen extends AbstractMetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of("java.lang.Boolean", "boolean");

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return BigInteger.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + " ? $T.ONE : $T.ZERO", BigInteger.class, BigInteger.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.ONE.equals(" + castVar + ")", BigInteger.class};
    }

    // STRING enc

    @Override
    public void emitSerializeToMapString(MethodSpec.Builder builder, String key, String getExpr,
                                         String javaType) {
        builder.addStatement("map.put($S, $T.valueOf($L))", key, String.class, getExpr);
    }

    @Override
    public void emitDeserializeScalarString(MethodSpec.Builder builder, MetadataFieldInfo field,
                                            MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSet(builder, field, "$T.parseBoolean((String) v)", Boolean.class);
        builder.endControlFlow();
    }
}
