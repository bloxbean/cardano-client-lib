package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
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
    private byte[] metadataHash;

//    public List<TransactionInput> getInputs() {
//        return inputs;
//    }
//
//    public void setInputs(List<TransactionInput> inputs) {
//        this.inputs = inputs;
//    }
//
//    public List<TransactionOutput> getOutputs() {
//        return outputs;
//    }
//
//    public void setOutputs(List<TransactionOutput> outputs) {
//        this.outputs = outputs;
//    }
//
//    public BigInteger getFee() {
//        return fee;
//    }
//
//    public void setFee(BigInteger fee) {
//        this.fee = fee;
//    }
//
//    public Integer getTtl() {
//        return ttl;
//    }
//
//    public void setTtl(Integer ttl) {
//        this.ttl = ttl;
//    }
//
//    public byte[] getMetadataHash() {
//        return metadataHash;
//    }
//
//    public void setMetadataHash(byte[] metadataHash) {
//        this.metadataHash = metadataHash;
//    }

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

        mapBuilder.put(2, fee.longValue()); //TODO BigInteger to long value
        mapBuilder.put(3, ttl);

        if(metadataHash != null) {
            mapBuilder.put(7, metadataHash);
        }

        mapBuilder.end();
    }
}
