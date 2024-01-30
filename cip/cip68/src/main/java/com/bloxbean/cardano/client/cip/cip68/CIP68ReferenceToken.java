package com.bloxbean.cardano.client.cip.cip68;

import com.bloxbean.cardano.client.cip.cip68.common.CIP68TokenTemplate;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import lombok.NonNull;

import java.math.BigInteger;

public class CIP68ReferenceToken extends CIP68TokenTemplate<CIP68TokenTemplate> {

    private static final int ASSET_NAME_LABEL = 100;

    public CIP68ReferenceToken(CIP68TokenTemplate tokenTemplate) {
        super(tokenTemplate.getName(), ASSET_NAME_LABEL, tokenTemplate.getDatum());
    }

    @Override
    public Asset getAsset(@NonNull BigInteger value) {
        if (value.compareTo(BigInteger.ONE) > 0)
            throw new IllegalArgumentException("Reference token value should be 1");

        return super.getAsset(value);
    }

    public Asset getAsset() {
        return super.getAsset(BigInteger.ONE);
    }
}
