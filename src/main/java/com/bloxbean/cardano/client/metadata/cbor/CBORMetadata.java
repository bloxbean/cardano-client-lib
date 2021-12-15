package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.exception.MetadataDeSerializationException;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Collection;

import static co.nstant.in.cbor.model.MajorType.*;
import static co.nstant.in.cbor.model.MajorType.ARRAY;

public class CBORMetadata implements Metadata {
    private Map map;

    public CBORMetadata() {
        map = new Map();
    }

    public CBORMetadata put(BigInteger key, BigInteger value) {
        map.put(new UnsignedInteger(key), new UnsignedInteger(value));
        return this;
    }

    public CBORMetadata putNegative(BigInteger key, BigInteger value) {
        map.put(new UnsignedInteger(key), new NegativeInteger(value));
        return this;
    }

    public CBORMetadata put(BigInteger key, byte[] value) {
        map.put(new UnsignedInteger(key), new ByteString(value));
        return this;
    }

    public CBORMetadata put(BigInteger key, String value) {
        map.put(new UnsignedInteger(key), new UnicodeString(value));
        return this;
    }

    public CBORMetadata put(BigInteger key, CBORMetadataMap mm) {
        if(map != null)
            map.put(new UnsignedInteger(key), mm.getMap());
        return this;
    }

    public CBORMetadata put(BigInteger key, CBORMetadataList list) {
        map.put(new UnsignedInteger(key), list.getArray());
        return this;
    }

    @Override
    public Map getData() throws MetadataSerializationException {
        return map;
    }

    public byte[] serialize() throws MetadataSerializationException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CborBuilder cborBuilder = new CborBuilder();

            cborBuilder.add(map);

            new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
            byte[] encodedBytes = baos.toByteArray();

            return encodedBytes;
        } catch (Exception ex) {
            throw new MetadataSerializationException("CBOR serialization exception ", ex);
        }
    }

    public static CBORMetadata deserialize(Map metadataMap) throws MetadataDeSerializationException {
        CBORMetadata cborMetadata = new CBORMetadata();
        Collection<DataItem> keys = metadataMap.getKeys();
        for(DataItem keyDI: keys){
            DataItem valueDI = metadataMap.get(keyDI);
            BigInteger key = ((UnsignedInteger)keyDI).getValue();

            if(UNSIGNED_INTEGER.equals(valueDI.getMajorType())){
                cborMetadata.put(key, ((UnsignedInteger)valueDI).getValue());
            } else if(NEGATIVE_INTEGER.equals(valueDI.getMajorType())) {
                cborMetadata.putNegative(key, ((NegativeInteger)valueDI).getValue());
            } else if(BYTE_STRING.equals(valueDI.getMajorType())) {
                cborMetadata.put(key, ((ByteString)valueDI).getBytes());
            } else if(UNICODE_STRING.equals(valueDI.getMajorType())) {
                cborMetadata.put(key, ((UnicodeString)valueDI).getString());
            } else if(MAP.equals(valueDI.getMajorType())){
                CBORMetadataMap cborMetadataMap = new CBORMetadataMap((Map)valueDI);
                cborMetadata.put(key, cborMetadataMap);
            } else if(ARRAY.equals(valueDI.getMajorType())) {
                CBORMetadataList cborMetadataList = new CBORMetadataList((Array)valueDI);
                cborMetadata.put(key, cborMetadataList);
            } else {
                throw new MetadataDeSerializationException("Unsupported type : " + valueDI.getMajorType());
            }
        }

        return cborMetadata;
    }

    public byte[] getMetadataHash() throws MetadataSerializationException {
        byte[] encodedBytes = serialize();

        return KeyGenUtil.blake2bHash256(encodedBytes);
    }

}
