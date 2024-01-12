package com.bloxbean.cardano.client.cip.cip68;

public class CIP68ReferenceToken extends CIP68TokenTemplate<CIP68TokenTemplate> {

    private static final int ASSET_NAME_LABEL = 100;

    public CIP68ReferenceToken (CIP68TokenTemplate tokenTemplate) {
        super(tokenTemplate.getMap(), tokenTemplate.getAssetName(), ASSET_NAME_LABEL);
    }
}
