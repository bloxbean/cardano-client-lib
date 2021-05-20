package com.bloxbean.cardano.client.transaction.model;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.ByteArrayOutputStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Transaction {
    private TransactionBody body;
    private TransactionWitnessSet witnessSet;
   // private TransactionMetadata metadata; //Optional

    public byte[] serialize() throws CborException, AddressExcepion {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CborBuilder cborBuilder = new CborBuilder();

        ArrayBuilder txnArrayBuilder = cborBuilder.addArray();
        //txn body
        MapBuilder txnBodyMapBuilder = txnArrayBuilder.addMap();
        body.serialize(txnBodyMapBuilder);

        //witness
        MapBuilder witnessMapBuilder = txnArrayBuilder.addMap();
        if(witnessSet != null)
            witnessSet.serialize(witnessMapBuilder);
        witnessMapBuilder.end();

        txnArrayBuilder.add((byte[]) null); //Null for meta
        txnArrayBuilder.end();

        new CborEncoder(baos).nonCanonical().encode(cborBuilder.build());
        byte[] encodedBytes = baos.toByteArray();
        return encodedBytes;
    }

    public String serializeToHex() throws CborException, AddressExcepion {
        byte[] bytes = serialize();
        return HexUtil.encodeHexString(bytes);
    }
}
