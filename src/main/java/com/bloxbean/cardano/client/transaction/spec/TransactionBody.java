package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionDeserializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionBody {
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;
    private BigInteger fee;
    private long ttl; //Optional
    private List<MultiAsset> mint = new ArrayList<>();
    private byte[] metadataHash;
    private long validityStartInterval;

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

       if(ttl != 0) {
           bodyMap.put(new UnsignedInteger(3), new UnsignedInteger(ttl)); //ttl
       }

       if(metadataHash != null) {
           bodyMap.put(new UnsignedInteger(7), new ByteString(metadataHash));
       }

       if(validityStartInterval != 0) {
           bodyMap.put(new UnsignedInteger(8), new UnsignedInteger(validityStartInterval)); //validityStartInterval
       }

        if(mint != null && mint.size() > 0) {
            Map mintMap = new Map();
            for(MultiAsset multiAsset: mint) {
                multiAsset.serialize(mintMap);
            }
            bodyMap.put(new UnsignedInteger(9), mintMap);
        }

        return bodyMap;
    }

    public static TransactionBody deserialize(Map bodyMap) throws TransactionDeserializationException {
        TransactionBody transactionBody = new TransactionBody();

       Array inputArray =  (Array)bodyMap.get(new UnsignedInteger(0));
       List<TransactionInput> inputs = new ArrayList<>();
       for(DataItem inputItem: inputArray.getDataItems()) {
           TransactionInput ti = TransactionInput.deserialize((Array)inputItem);
           inputs.add(ti);
       }
       transactionBody.setInputs(inputs);

       Array outputArray =  (Array)bodyMap.get(new UnsignedInteger(1));
        List<TransactionOutput> outputs = new ArrayList<>();
        for(DataItem ouptutItem: outputArray.getDataItems()) {
            TransactionOutput to = TransactionOutput.deserialize((Array)ouptutItem);
            outputs.add(to);
        }
        transactionBody.setOutputs(outputs);

       UnsignedInteger feeUI = (UnsignedInteger)bodyMap.get(new UnsignedInteger(2));
       if(feeUI != null) {
            transactionBody.setFee(feeUI.getValue());
       }

        UnsignedInteger ttlUI = (UnsignedInteger)bodyMap.get(new UnsignedInteger(3));
        if(ttlUI != null) {
            transactionBody.setTtl(ttlUI.getValue().longValue());
        }

        ByteString metadataHashBS = (ByteString)bodyMap.get(new UnsignedInteger(7));
        if(metadataHashBS != null) {
            transactionBody.setMetadataHash(metadataHashBS.getBytes());
        }

        UnsignedInteger validityStartIntervalUI = (UnsignedInteger)bodyMap.get(new UnsignedInteger(8));
        if(validityStartIntervalUI != null) {
            transactionBody.setValidityStartInterval(validityStartIntervalUI.getValue().longValue());
        }

        //Mint
        Map mintMap = (Map)bodyMap.get(new UnsignedInteger(9));
        if(mintMap != null) {
            Collection<DataItem> mintDataItems = mintMap.getKeys();
            for (DataItem multiAssetKey : mintDataItems) {
                MultiAsset ma = MultiAsset.deserialize(mintMap, multiAssetKey);
                transactionBody.getMint().add(ma);
            }
        }

        return transactionBody;
    }
}
