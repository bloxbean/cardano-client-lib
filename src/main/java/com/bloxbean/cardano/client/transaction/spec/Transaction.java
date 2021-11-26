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

import java.io.ByteArrayOutputStream;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class Transaction {
    private TransactionBody body;
    private TransactionWitnessSet witnessSet;
    @Deprecated
    private Metadata metadata;
    private boolean isValid;
    private AuxiliaryData auxiliaryData;

    public Transaction() {
        this.isValid = true;
    }

    public byte[] serialize() throws CborSerializationException {
        try {
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

            if(isValid)
                array.add(SimpleValue.TRUE);
            else
                array.add(SimpleValue.FALSE);

            //Auxiliary Data
            if (auxiliaryData != null) {
                DataItem auxDataMap = auxiliaryData.serialize();
                array.add(auxDataMap);
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

            DataItem txnBodyDI = txnItems.get(0);
            DataItem witnessDI = txnItems.get(1);

            if (witnessDI != null) {
                TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) witnessDI);
                transaction.setWitnessSet(witnessSet);
            }

            DataItem isValidDI = txnItems.get(2);

            boolean checkAuxData = true;
            //If it's special it can be either a bool or null. If it's null, then it's empty auxiliary data, otherwise
            //not a valid encoding
            if (isValidDI != null && isValidDI instanceof Special) {
                if (isValidDI == SimpleValue.TRUE) {
                    transaction.setValid(true);
                } else if (isValidDI == SimpleValue.FALSE) {
                    transaction.setValid(false);
                } else if (isValidDI == SimpleValue.NULL) {
                    checkAuxData = false;
                    transaction.setValid(true);
                }
            }

            if (checkAuxData) {
                //Check for AuxiliaryData
                DataItem auxiliaryDataDI = txnItems.get(3);
                if (auxiliaryDataDI != null && MajorType.MAP.equals(auxiliaryDataDI.getMajorType())) { //Auxiliary data
                    DataItem auxiliaryDataMap = auxiliaryDataDI;
                    AuxiliaryData auxiliaryData = AuxiliaryData.deserialize((Map) auxiliaryDataMap);
                    transaction.setAuxiliaryData(auxiliaryData);
                }
            }

            TransactionBody body = TransactionBody.deserialize((Map) txnBodyDI);
            transaction.setBody(body);

            return transaction;
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
    }
}
