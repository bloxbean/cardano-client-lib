package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.TransactionDeserializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
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
    private Metadata metadata;

    public byte[] serialize() throws CborException, AddressExcepion {
        if(metadata != null && body.getMetadataHash() == null) {
            byte[] metadataHash = metadata.getMetadataHash();
            body.setMetadataHash(metadataHash);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();

        Array array = new Array();
        Map bodyMap = body.serialize();
        array.add(bodyMap);

        //witness
        if(witnessSet != null) {
            Map witnessMap = witnessSet.serialize();
            array.add(witnessMap);
        } else {
            Map witnessMap = new Map();
            array.add(witnessMap);
        }

        //metadata
        if(metadata != null) {
            array.add(metadata.getData());
        } else
            array.add(new ByteString((byte[]) null)); //Null for meta

        cborBuilder.add(array);

        new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        byte[] encodedBytes = baos.toByteArray();
        return encodedBytes;
    }

    public String serializeToHex() throws CborException, AddressExcepion {
        byte[] bytes = serialize();
        return HexUtil.encodeHexString(bytes);
    }

    public static Transaction deserialize(byte[] bytes) throws TransactionDeserializationException {
        try {
            List<DataItem> dataItemList = CborDecoder.decode(bytes);

            Transaction transaction = new Transaction();
            if (dataItemList.size() != 1)
                throw new TransactionDeserializationException("Invalid no of dataitems");

            Array array = (Array) dataItemList.get(0);

            List<DataItem> txnItems = array.getDataItems();
            if (txnItems.size() < 3)
                throw new TransactionDeserializationException("Invalid no of items");

            DataItem txnBody = txnItems.get(0);
            DataItem witness = txnItems.get(1);
            DataItem metadata = txnItems.get(2);

            if (witness != null) {
                TransactionWitnessSet witnessSet = TransactionWitnessSet.deserialize((Map) witness);
                transaction.setWitnessSet(witnessSet);
            }

            //metadata
            if (MajorType.MAP.equals(metadata.getMajorType())) { //Metadata available
                Map metadataMap = (Map) metadata;
                Metadata cborMetadata = CBORMetadata.deserialize(metadataMap);
                transaction.setMetadata(cborMetadata);
            }

            TransactionBody body = TransactionBody.deserialize((Map) txnBody);
            transaction.setBody(body);

            return transaction;
        } catch (Exception e) {
            throw new TransactionDeserializationException("CBOR deserialization failed", e);
        }
    }
}
