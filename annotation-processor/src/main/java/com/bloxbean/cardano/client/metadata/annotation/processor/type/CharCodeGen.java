package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldAccessor;
import com.bloxbean.cardano.client.metadata.annotation.processor.MetadataFieldInfo;
import com.squareup.javapoet.MethodSpec;

import java.util.Set;

/**
 * Code generation strategy for {@code char} / {@link Character} fields.
 *
 * <ul>
 *   <li>DEFAULT: stored as String via {@code String.valueOf()}, parsed back via {@code charAt(0)}</li>
 *   <li>STRING: identical to DEFAULT</li>
 * </ul>
 */
public class CharCodeGen extends AbstractMetadataTypeCodeGen {

    private static final Set<String> TYPES = Set.of("java.lang.Character", "char");

    @Override
    public Set<String> supportedJavaTypes() {
        return TYPES;
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{"$T.valueOf(" + valueExpr + ")", String.class};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"((String) " + castVar + ").charAt(0)"};
    }

    // Scalar deserialization uses raw expression (no $T arg)
    @Override
    public void emitDeserializeScalarDefault(MethodSpec.Builder builder, MetadataFieldInfo field,
                                             MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitSetRaw(builder, field, "((String) v).charAt(0)");
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add((($T) _el).charAt(0))", String.class);
        builder.endControlFlow();
    }

    @Override
    public void emitDeserializeOptional(MethodSpec.Builder builder, MetadataFieldInfo field,
                                        MetadataFieldAccessor accessor) {
        builder.beginControlFlow("if (v instanceof $T)", String.class);
        accessor.emitOptionalOfSetRaw(builder, field, "((String) v).charAt(0)");
        builder.nextControlFlow("else");
        accessor.emitOptionalEmpty(builder, field);
        builder.endControlFlow();
    }
}
