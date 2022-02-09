package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionBody {

    @Builder.Default
    private List<TransactionInput> inputs = new ArrayList<>();

    @Builder.Default
    private List<TransactionOutput> outputs = new ArrayList<>();

    private BigInteger fee;
    private long ttl; //Optional
    //certs -- Not implemented
    //withdrawals -- Not implemented
    //update -- Not implemented
    private byte[] auxiliaryDataHash; //auxiliary_data_hash
    private long validityStartInterval;

    @Builder.Default
    private List<MultiAsset> mint = new ArrayList<>();

    private byte[] scriptDataHash;

    @Builder.Default
    private List<TransactionInput> collateral = new ArrayList<>();

    @Builder.Default
    private List<byte[]> requiredSigners = new ArrayList<>();

    private NetworkId networkId; // 1 or 0

    public Map serialize() throws CborSerializationException, AddressExcepion {
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

        if (fee != null) {
            bodyMap.put(new UnsignedInteger(2), new UnsignedInteger(fee)); //fee
        } else {
            throw new CborSerializationException("Fee cannot be null");
        }

       if(ttl != 0) {
           bodyMap.put(new UnsignedInteger(3), new UnsignedInteger(ttl)); //ttl
       }

       if(auxiliaryDataHash != null) {
           bodyMap.put(new UnsignedInteger(7), new ByteString(auxiliaryDataHash));
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

        if(scriptDataHash != null) {
            bodyMap.put(new UnsignedInteger(11), new ByteString(scriptDataHash));
        }

        //collateral
        if(collateral != null && collateral.size() > 0) {
            Array collateralArray = new Array();
            for (TransactionInput ti : collateral) {
                Array input = ti.serialize();
                collateralArray.add(input);
            }
            bodyMap.put(new UnsignedInteger(13), collateralArray);
        }

        //required_signers
        if (requiredSigners != null && requiredSigners.size() > 0) {
            Array requiredSignerArray = new Array();
            for (byte[] requiredSigner : requiredSigners) {
                requiredSignerArray.add(new ByteString(requiredSigner));
            }
            bodyMap.put(new UnsignedInteger(14), requiredSignerArray);
        }

        //NetworkId
        if (networkId != null) {
            switch (networkId) {
                case TESTNET:
                    bodyMap.put(new UnsignedInteger(15), new UnsignedInteger(0));
                    break;
                case MAINNET:
                    bodyMap.put(new UnsignedInteger(15), new UnsignedInteger(1));
                    break;
            }
        }

        return bodyMap;
    }

    public static TransactionBody deserialize(Map bodyMap) throws CborDeserializationException {
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
            transactionBody.setAuxiliaryDataHash(metadataHashBS.getBytes());
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

        //script_data_hash
        ByteString scriptDataHashBS = (ByteString)bodyMap.get(new UnsignedInteger(11));
        if (scriptDataHashBS != null) {
            transactionBody.setScriptDataHash(scriptDataHashBS.getBytes());
        }

        //collateral
        Array collateralArray =  (Array)bodyMap.get(new UnsignedInteger(13));
        if (collateralArray != null) {
            List<TransactionInput> collateral = new ArrayList<>();
            for (DataItem inputItem : collateralArray.getDataItems()) {
                TransactionInput ti = TransactionInput.deserialize((Array) inputItem);
                collateral.add(ti);
            }
            transactionBody.setCollateral(collateral);
        }

        //required_signers
        Array requiredSignerArray = (Array)bodyMap.get(new UnsignedInteger(14));
        if (requiredSignerArray != null) {
            List<byte[]> requiredSigners = new ArrayList<>();
            for (DataItem requiredSigDI: requiredSignerArray.getDataItems()) {
                ByteString requiredSigBS = (ByteString) requiredSigDI;
                requiredSigners.add(requiredSigBS.getBytes());
            }
            transactionBody.setRequiredSigners(requiredSigners);
        }

        //network Id
        UnsignedInteger networkIdUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(15));
        if (networkIdUI != null) {
            int networkIdInt = networkIdUI.getValue().intValue();
            if (networkIdInt == 0) {
                transactionBody.setNetworkId(NetworkId.TESTNET);
            }else if (networkIdInt == 1) {
                transactionBody.setNetworkId(NetworkId.MAINNET);
            } else {
                throw new CborDeserializationException("Invalid networkId value : " + networkIdInt);
            }
        }

        return transactionBody;
    }
}
