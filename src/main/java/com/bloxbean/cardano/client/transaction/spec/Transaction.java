package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {
    private TransactionBody body;
    private TransactionWitnessSet witnessSet;
    @Deprecated
    private Metadata metadata;
    private boolean isValid;
    private AuxiliaryData auxiliaryData;

    public byte[] serialize() throws CborSerializationException {
        try {
//            if (metadata != null && body.getMetadataHash() == null) {
//                byte[] metadataHash = metadata.getMetadataHash();
//                body.setMetadataHash(metadataHash);
//            }

            if (auxiliaryData != null && body.getAuxiliaryDataHash() == null) {
                byte[] auxiliaryDataHash = auxiliaryData.getAuxiliaryDataHash();
                body.setAuxiliaryDataHash(auxiliaryDataHash);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CborBuilder cborBuilder = new CborBuilder();

            Array array = new Array();
            Map bodyMap = body.serialize();
            array.add(bodyMap);

            //witness
            if (witnessSet != null) {
                Map witnessMap = witnessSet.serialize();
                array.add(witnessMap);
            } else {
                Map witnessMap = new Map();
                array.add(witnessMap);
            }
            //metadata - Still supported in Alonzo
//            if (metadata != null && auxiliaryData == null) {
//                array.add(metadata.getData());
//            } else
//                array.add(new ByteString((byte[]) null)); //Null for meta  //TODO alonzo changes

            if(isValid)
                array.add(SimpleValue.TRUE);
            else
                array.add(SimpleValue.FALSE);

            //Auxiliary Data
            if (auxiliaryData != null) {
                DataItem[] dataItems = auxiliaryData.serialize();
                for (DataItem dataItem: dataItems) {
                    array.add(dataItem);
                }
            } else
                array.add(SimpleValue.NULL);

            cborBuilder.add(array);

            new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
            byte[] encodedBytes = baos.toByteArray();
            return encodedBytes;
        } catch (Exception e) {
            throw new CborSerializationException("CBOR Serialization failed", e);
        }
    }

    public String serializeToHex() throws CborSerializationException {
        try {
            byte[] bytes = serialize();
            return HexUtil.encodeHexString(bytes);
        } catch (Exception ex) {
            throw new CborSerializationException("CBOR serialization exception", ex);
        }
    }

    public static Transaction deserialize(byte[] bytes) throws CborDeserializationException {
        try {
            List<DataItem> dataItemList = CborDecoder.decode(bytes);

            Transaction transaction = new Transaction();
            if (dataItemList.size() != 1)
                throw new CborDeserializationException("Invalid no of dataitems");

            Array array = (Array) dataItemList.get(0);

            List<DataItem> txnItems = array.getDataItems();
            if (txnItems.size() < 3)
                throw new CborDeserializationException("Invalid no of items");

            DataItem txnBody = txnItems.get(0);
            DataItem witness = txnItems.get(1);
            DataItem metadata = txnItems.get(2);

            if (witness != null) {
                TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) witness);
                transaction.setWitnessSet(witnessSet);
            }

            //metadata
//            if (MajorType.MAP.equals(metadata.getMajorType())) { //Metadata available
//                Map metadataMap = (Map) metadata;
//                Metadata cborMetadata = CBORMetadata.deserialize(metadataMap);
//                transaction.setMetadata(cborMetadata);
//            }

            if(metadata != null && metadata.getTag() != null && metadata.getTag().getValue() == 259) { //Auxiliary data
                DataItem auxiliaryDataMap = metadata;
                AuxiliaryData auxiliaryData = AuxiliaryData.deserialize((Map) auxiliaryDataMap);
                transaction.setAuxiliaryData(auxiliaryData);
            }

            TransactionBody body = TransactionBody.deserialize((Map) txnBody);
            transaction.setBody(body);

            return transaction;
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
    }
}
