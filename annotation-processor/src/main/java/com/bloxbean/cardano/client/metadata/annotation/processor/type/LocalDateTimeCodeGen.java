package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Code generation strategy for {@link LocalDateTime} fields.
 *
 * <p>DEFAULT and STRING are identical: ISO-8601 String on-chain.
 */
public class LocalDateTimeCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.time.LocalDateTime");
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
        return new Object[]{"$T.parse(($T) " + castVar + ")", LocalDateTime.class, String.class};
    }
}
