package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import java.util.Locale;
import java.util.Set;

/**
 * Code generation strategy for {@link Locale} fields.
 *
 * <p>Always stored as String (language tag) on-chain (DEFAULT == STRING).
 */
public class LocaleCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of("java.util.Locale");
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + ".toLanguageTag()"};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.forLanguageTag(($T) " + castVar + ")", Locale.class, String.class};
    }
}
