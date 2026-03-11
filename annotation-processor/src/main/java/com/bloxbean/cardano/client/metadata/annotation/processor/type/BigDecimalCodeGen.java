package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import com.squareup.javapoet.MethodSpec;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Code generation strategy for {@link BigDecimal} fields.
 *
 * <ul>
 *   <li>DEFAULT: stored as {@code toPlainString()}, parsed back via {@code new BigDecimal(String)}</li>
 *   <li>STRING: identical to DEFAULT (BigDecimal is always text on chain)</li>
 * </ul>
 */
public class BigDecimalCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.math.BigDecimal");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + ".toPlainString()"};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"new $T((String) " + castVar + ")", BigDecimal.class};
    }

    @Override
    public void emitDeserializeElement(MethodSpec.Builder builder, String javaType) {
        builder.beginControlFlow("if (_el instanceof $T)", String.class);
        builder.addStatement("_result.add(new $T(($T) _el))", BigDecimal.class, String.class);
        builder.endControlFlow();
    }
}
