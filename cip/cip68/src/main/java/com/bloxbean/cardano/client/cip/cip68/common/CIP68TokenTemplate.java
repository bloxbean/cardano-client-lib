package com.bloxbean.cardano.client.cip.cip68.common;

import com.bloxbean.cardano.client.cip.cip67.CIP67AssetNameUtil;
import com.bloxbean.cardano.client.cip.cip68.CIP68ReferenceToken;
import com.bloxbean.cardano.client.crypto.bip32.util.BytesUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.PlutusDataPrettyPrinter;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.NonNull;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.fromByteString;
import static com.bloxbean.cardano.client.cip.cip68.common.CIP68Util.toByteString;

/**
 * Base class for CIP68 token templates. This class provides the basic functionality to create different types of CIP68 tokens.
 *
 * @param <T>
 */
public abstract class CIP68TokenTemplate<T extends CIP68TokenTemplate> extends DatumProperties<T> {

    public static final String DESCRIPTION_KEY = "description";
    public static final String NAME_KEY = "name";

    private int assetNameLabel;
    private byte[] nameBytes;

    private CIP68Datum cip68Datum;

    public CIP68TokenTemplate(int assetNameLabel) {
        super();
        this.assetNameLabel = assetNameLabel;
        this.cip68Datum = CIP68Datum.create(mapPlutusData);
    }

    public CIP68TokenTemplate(int assetNameLabel, CIP68Datum cip68Datum) {
        super(cip68Datum.getMetadata());
        this.assetNameLabel = assetNameLabel;
        this.cip68Datum = cip68Datum;
    }

    public CIP68TokenTemplate(String assetName, int assetNameLabel, CIP68Datum cip68Datum) {
        super(cip68Datum.getMetadata());
        this.assetNameLabel = assetNameLabel;
        this.nameBytes = assetName.getBytes(StandardCharsets.UTF_8);
        this.cip68Datum = cip68Datum;
    }

    public int getAssetNameLabel() {
        return assetNameLabel;
    }

    public String getAssetNameAsHex() {
        byte[] assetNameLabelBytes = CIP67AssetNameUtil.labelToPrefix(assetNameLabel);
        return "0x" + new String(HexUtil.encodeHexString(assetNameLabelBytes))
                + HexUtil.encodeHexString(nameBytes);
    }

    public byte[] getAssetNameAsBytes() {
        byte[] assetNameLabelBytes = CIP67AssetNameUtil.labelToPrefix(assetNameLabel);
        return BytesUtil.merge(assetNameLabelBytes, nameBytes);
    }

    public CIP68ReferenceToken getReferenceToken() {
        return new CIP68ReferenceToken(this);
    }

    public Asset getAsset(BigInteger value) {
        String assetNameHex = getAssetNameAsHex();
        if (HexUtil.decodeHexString(assetNameHex).length > 32)
            throw new IllegalArgumentException("Asset name length cannot be more than 32 bytes");

        return new Asset(getAssetNameAsHex(), value);
    }

    public CIP68Datum getDatum() {
        return cip68Datum;
    }

    public String getFriendlyAssetName() {
        return String.format("(%d) %s", assetNameLabel, getName());
    }

    public T name(@NonNull String name) {
        this.nameBytes = name.getBytes(StandardCharsets.UTF_8);
        property(toByteString(NAME_KEY), toByteString(name));
        return (T) this;
    }

    public T name(byte[] nameBytes) {
        this.nameBytes = nameBytes;
        property(toByteString(NAME_KEY), BytesPlutusData.of(this.nameBytes));
        return (T) this;
    }

    public T description(String description) {
        property(toByteString(DESCRIPTION_KEY), toByteString(description));
        return (T) this;
    }

    public String getName() {
        var valuePlutusData = (BytesPlutusData) mapPlutusData.getMap().get(toByteString(NAME_KEY));
        return fromByteString(valuePlutusData);
    }

    public String getDescription() {
        var valuePlutusData = (BytesPlutusData) mapPlutusData.getMap().get(toByteString(DESCRIPTION_KEY));
        return fromByteString(valuePlutusData);
    }

    protected T populateFromDatumBytes(byte[] datumBytes) {
        try {
            ConstrPlutusData constrPlutusData = (ConstrPlutusData) PlutusData.deserialize(datumBytes);
            ListPlutusData list = constrPlutusData.getData();
            MapPlutusData metadataMapPlutusData = (MapPlutusData) list.getPlutusDataList().get(0);
            int version = ((BigIntPlutusData) list.getPlutusDataList().get(1)).getValue().intValue();
            PlutusData extra = list.getPlutusDataList().get(2);

            CIP68Datum datum = CIP68Datum.create(metadataMapPlutusData);
            datum.setVersion(version);
            datum.setExtra(extra);

            this.cip68Datum = datum;
            this.nameBytes = ((BytesPlutusData) metadataMapPlutusData.getMap().get(toByteString(NAME_KEY))).getValue();
            name(nameBytes);
            this.mapPlutusData = metadataMapPlutusData;

            return (T) this;

        } catch (CborDeserializationException e) {
            throw new CborRuntimeException("Error deserializing CIP68 Metadata", e);
        }
    }

    public MapPlutusData getMetadata() {
        return mapPlutusData;
    }

    public String getMetadataJson() {
        return PlutusDataPrettyPrinter.toJson(getMetadata());
    }

    public ConstrPlutusData getDatumAsPlutusData() {
        return getDatum().asPlutusData();
    }

}
