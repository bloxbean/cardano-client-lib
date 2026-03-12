package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import java.util.Set;
import java.util.UUID;

/**
 * Code generation strategy for {@link UUID} fields.
 *
 * <p>Always stored as String on-chain (DEFAULT == STRING).
 */
public class UuidCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.util.UUID");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + ".toString()"};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.fromString(($T) " + castVar + ")", UUID.class, String.class};
    }
}
