package com.bloxbean.cardano.client.cip.cip68;

import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;

import java.math.BigInteger;

public class CIP68Metadata extends CBORMetadata {
    private int version = 1;
    private CBORMetadataList metadataList;
    private PlutusData extra = PlutusData.unit();
    private CIP68TokenTemplate tokenStandard;

    private CIP68Metadata() {
        metadataList = new CBORMetadataList();
        put(1, BigInteger.valueOf(version));
        put(2, extra.serializeToHex());
    }

    public static CIP68Metadata create() {
        return new CIP68Metadata();
    }

    private CIP68Metadata(Map map) {
        super(map);
    }

    public static CIP68Metadata create(byte[] cborBytes) {
        Map data = CBORMetadata.deserialize(cborBytes).getData();
        return new CIP68Metadata(data);
    }

    public CIP68Metadata addToken(CIP68TokenTemplate token) {
        this.tokenStandard = token;
        put(0, token);
        return this;
    }

    public CIP68Metadata version(int version) {
        this.version = version;
        put(1, BigInteger.valueOf(version));
        return this;
    }

    public CIP68Metadata addExtraData(PlutusData extra) {
        this.extra = extra;
        put(2, extra.serializeToHex());
        return this;
    }

    public PlutusData asPlutusData() throws CborDeserializationException {
        Map dataItem = getData();
        PlutusData build = null;
        try {
            build = ConstrPlutusData.builder()
                    .data(ListPlutusData.of(
                            PlutusData.deserialize(dataItem.get(new UnsignedInteger(0))),
                            BigIntPlutusData.of(BigInteger.valueOf(this.version)),
                            PlutusData.deserialize(dataItem.get(new UnsignedInteger(2)))
                    )).build();
        } catch (CborDeserializationException e) {
            throw new CborDeserializationException("Error deserializing CIP68 Metadata", e);
        }
        return build;
    }
}
