package com.bloxbean.cardano.client.cip.cip68.common;

import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.plutus.util.PlutusDataPrettyPrinter;
import lombok.Data;

import java.math.BigInteger;

/**
 * This class represents the Datum for CIP68.
 */
@Data
public class CIP68Datum {
    private int version = 1;
    private PlutusData extra = PlutusData.unit();
    private MapPlutusData metadata;

    private CIP68Datum(MapPlutusData metadata) {
        this.metadata = metadata;
    }

    public static CIP68Datum create(MapPlutusData metadata) {
        return new CIP68Datum(metadata);
    }

    public CIP68Datum version(int version) {
        this.version = version;
        return this;
    }

    public CIP68Datum extra(PlutusData extra) {
        this.extra = extra;
        return this;
    }

    public MapPlutusData getMetadata() {
        if (this.metadata == null)
            return null;
        return this.metadata;
    }


    public String getMetadataJson() {
        if (this.metadata == null)
            return null;
        return PlutusDataPrettyPrinter.toJson(this.metadata);
    }

    public ConstrPlutusData asPlutusData() {
        return ConstrPlutusData.builder()
                .data(ListPlutusData.of(
                        metadata,
                        BigIntPlutusData.of(BigInteger.valueOf(this.version)),
                        extra
                )).build();
    }

}
