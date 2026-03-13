package com.bloxbean.cardano.client.metadata.annotation.processor.type;

import static com.bloxbean.cardano.client.metadata.annotation.processor.MetadataConstants.*;

import java.util.Currency;
import java.util.Set;

/**
 * Code generation strategy for {@link Currency} fields.
 *
 * <p>Always stored as String (currency code) on-chain (DEFAULT == STRING).
 */
public class CurrencyCodeGen extends AbstractMetadataTypeCodeGen {

    @Override
    public Set<String> supportedJavaTypes() {
        return Set.of(CURRENCY);
    }

    @Override
    protected Class<?> onChainType(String javaType) {
        return String.class;
    }

    @Override
    protected Object[] serializeExpression(String valueExpr, String javaType) {
        return new Object[]{valueExpr + ".getCurrencyCode()"};
    }

    @Override
    protected Object[] deserializeExpression(String castVar, String javaType) {
        return new Object[]{"$T.getInstance(($T) " + castVar + ")", Currency.class, String.class};
    }
}
