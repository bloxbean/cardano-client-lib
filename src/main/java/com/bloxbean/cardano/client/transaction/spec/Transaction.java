package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
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
}
