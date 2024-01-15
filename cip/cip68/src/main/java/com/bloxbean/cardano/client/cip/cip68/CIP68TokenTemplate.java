package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.cip.cip25.NFTProperties;
import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public abstract class CIP68TokenTemplate<T extends CIP68TokenTemplate> extends NFTProperties {

    public static final String DESCRIPTION_KEY = "description";
    public static final String NAME_KEY = "name";

    private int assetNameLabel;
    private String name;
    CIP68TokenTemplate(int assetNameLabel) {
        this.assetNameLabel = assetNameLabel;
    }
    CIP68TokenTemplate(Map map, int assetNameLabel) {
        super(map);
        this.assetNameLabel = assetNameLabel;
    }

    CIP68TokenTemplate(Map map, String assetName, int assetNameLabel) {
        super(map);
        this.assetNameLabel = assetNameLabel;
        this.name = assetName;
    }

    public int getAssetNameLabel() {
        return assetNameLabel;
    }

    public String getAssetNameAsHex() {
        byte[] assetNameLabelBytes = CIP67AssetNameUtil.labelToPrefix(assetNameLabel);
        return "0x" + new String(HexUtil.encodeHexString(assetNameLabelBytes))
                + HexUtil.encodeHexString(name.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] getAssetNameAsBytes() {
        byte[] assetNameLabelBytes = CIP67AssetNameUtil.labelToPrefix(assetNameLabel);
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        return BytesUtil.merge(assetNameLabelBytes, nameBytes);
    }

    public CIP68ReferenceToken getReferenceToken() {
        return new CIP68ReferenceToken(this);
    }

    public Asset getAsset(BigInteger value) {
        return new Asset(getAssetNameAsHex(), value);
    }

    public CIP68Metadata getMetadata() {
        return CIP68Metadata.create().addToken(this);
    }

    public String getAssetName() {
        return name;
    }

    public T name(String name) {
        this.name = name;
        put(NAME_KEY, name);
        return (T) this;
    }

    public T description(String description) {
        put(DESCRIPTION_KEY, description);
        return (T) this;
    }

    public String getName() {
        return (String) get(NAME_KEY);
    }

    public String getDescription() {
        return (String) get(DESCRIPTION_KEY);
    }

    @Override
    public T property(String name, String value) {
        return (T) super.property(name, value);
    }

    /**
     * Json reprensation if possible
     * @return
     */
    public String toString() {
        try {
            return toJson();
        } catch (Exception e) {
            return super.toString();
        }
    }
}
