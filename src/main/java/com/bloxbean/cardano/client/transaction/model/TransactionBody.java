package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
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

    public Map serialize() throws CborException, AddressExcepion {
        Map bodyMap = new Map();

        Array inputsArray = new Array();
        for(TransactionInput ti: inputs) {
            Array input = ti.serialize();
            inputsArray.add(input);
        }
        bodyMap.put(new UnsignedInteger(0), inputsArray);

        Array outputsArray = new Array();
        for(TransactionOutput to: outputs) {
            Array output = to.serialize();
            outputsArray.add(output);
        }
        bodyMap.put(new UnsignedInteger(1), outputsArray);

       bodyMap.put(new UnsignedInteger(2), new UnsignedInteger(fee)); //fee
       bodyMap.put(new UnsignedInteger(3), new UnsignedInteger(ttl)); //ttl

        if(metadataHash != null) {
            bodyMap.put(new UnsignedInteger(7), new ByteString(metadataHash));
        }

        if(mint != null && mint.size() > 0) {
            Map mintMap = new Map();
            for(MultiAsset multiAsset: mint) {
                multiAsset.serialize(mintMap);
                bodyMap.put(new UnsignedInteger(9), mintMap);
            }
        }

        return bodyMap;
    }
}
