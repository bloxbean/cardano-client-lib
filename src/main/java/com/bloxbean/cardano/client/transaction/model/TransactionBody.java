package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionBody {
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;
    private BigInteger fee;
    private Integer ttl; //Optional
    private List<MultiAsset> mint = new ArrayList<>();
    private byte[] metadataHash;

    public void serialize(MapBuilder mapBuilder) throws CborException, AddressExcepion {
        ArrayBuilder inputArrayBuilder = mapBuilder.putArray(0);
        for(TransactionInput ti: inputs) {
            ti.serialize(inputArrayBuilder.addArray());
        }
        inputArrayBuilder.end();

        ArrayBuilder outputArrayBuilder = mapBuilder.putArray(1);
        for(TransactionOutput to: outputs) {
            to.serialize(outputArrayBuilder.addArray());
        }
        outputArrayBuilder.end();

        mapBuilder.put(new UnsignedInteger(2), new UnsignedInteger(fee));
        mapBuilder.put(3, ttl);

        if(metadataHash != null) {
            mapBuilder.put(7, metadataHash);
        }

        if(mint != null && mint.size() > 0) {
            Map map = new Map();
            for(MultiAsset multiAsset: mint) {
                multiAsset.serialize(map);
                mapBuilder.put(new UnsignedInteger(9), map);
            }
        }

        mapBuilder.end();
    }
}
