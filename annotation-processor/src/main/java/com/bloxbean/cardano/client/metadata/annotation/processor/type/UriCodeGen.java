package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import java.net.URI;
import java.util.Set;

/**
 * Code generation strategy for {@link URI} fields.
 *
 * <p>Always stored as String on-chain (DEFAULT == STRING).
 */
public class UriCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.net.URI");
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
        return new Object[]{"$T.create(($T) " + castVar + ")", URI.class, String.class};
    }
}
