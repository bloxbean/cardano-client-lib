package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.cert.Certificate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionBody {

    @Builder.Default
    private List<TransactionInput> inputs = new ArrayList<>();

    @Builder.Default
    private List<TransactionOutput> outputs = new ArrayList<>();

    @Builder.Default
    private BigInteger fee = BigInteger.ZERO;
    private long ttl; //Optional

    @Builder.Default
    private List<Certificate> certs = new ArrayList<>();

    @Builder.Default
    private List<Withdrawal> withdrawals = new ArrayList<>();

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

    //babbage fields
    private TransactionOutput collateralReturn; //?16

    private BigInteger totalCollateral; //? 17

    @Builder.Default
    private List<TransactionInput> referenceInputs = new ArrayList<>(); // ? 18

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
            DataItem output = to.serialize();
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

       if (certs != null && certs.size() > 0) { //certs
           Array certArray = new Array();
           for (Certificate cert: certs) {
               certArray.add(cert.serialize());
           }

           bodyMap.put(new UnsignedInteger(4), certArray);
       }

       if (withdrawals != null && withdrawals.size() > 0) { //Withdrawals
           Map withdrawalMap = new Map();
           for (Withdrawal withdrawal: withdrawals) {
              withdrawal.serialize(withdrawalMap);
           }
           bodyMap.put(new UnsignedInteger(5), withdrawalMap);
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

        //collateral return
        if (collateralReturn != null) {
            bodyMap.put(new UnsignedInteger(16), collateralReturn.serialize());
        }

        //total collateral
        if (totalCollateral != null) {
            bodyMap.put(new UnsignedInteger(17), new UnsignedInteger(totalCollateral));
        }

        //reference inputs
        if(referenceInputs != null && referenceInputs.size() > 0) {
            Array referenceInputsArray = new Array();
            for (TransactionInput ti : referenceInputs) {
                Array input = ti.serialize();
                referenceInputsArray.add(input);
            }
            bodyMap.put(new UnsignedInteger(18), referenceInputsArray);
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
            TransactionOutput to = TransactionOutput.deserialize(ouptutItem);
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

        //certs
        Array certArray = (Array)bodyMap.get(new UnsignedInteger(4));
        if (certArray != null && certArray.getDataItems() != null && certArray.getDataItems().size() > 0) {
            for (DataItem dataItem: certArray.getDataItems()) {
                Certificate cert = Certificate.deserialize((Array) dataItem);
                transactionBody.getCerts().add(cert);
            }
        }

        //withdrawals
        Map withdrawalMap = (Map)bodyMap.get(new UnsignedInteger(5));
        if (withdrawalMap != null && withdrawalMap.getKeys() != null && withdrawalMap.getKeys().size() > 0) {
            Collection<DataItem> addrKeys = withdrawalMap.getKeys();
            for (DataItem addrKey: addrKeys) {
                Withdrawal withdrawal = Withdrawal.deserialize(withdrawalMap, addrKey);
                transactionBody.getWithdrawals().add(withdrawal);
            }
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

        //collateral return
        Array collateralReturnArray = (Array) bodyMap.get(new UnsignedInteger(16));
        if (collateralReturnArray != null) {
            TransactionOutput collateralReturn = TransactionOutput.deserialize(collateralReturnArray);
            transactionBody.setCollateralReturn(collateralReturn);
        }

        //total collateral
        UnsignedInteger totalCollateralUI = (UnsignedInteger) bodyMap.get(new UnsignedInteger(17));
        if (totalCollateralUI != null) {
            transactionBody.setTotalCollateral(totalCollateralUI.getValue());
        }

        //reference inputs
        Array referenceInputsArray =  (Array)bodyMap.get(new UnsignedInteger(18));
        if (referenceInputsArray != null) {
            List<TransactionInput> referenceInputs = new ArrayList<>();
            for (DataItem inputItem : referenceInputsArray.getDataItems()) {
                TransactionInput ti = TransactionInput.deserialize((Array) inputItem);
                referenceInputs.add(ti);
            }
            transactionBody.setReferenceInputs(referenceInputs);
        }

        return transactionBody;
    }
}
