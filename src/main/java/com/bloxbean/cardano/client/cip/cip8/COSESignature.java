package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.*;

import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class COSESignature {
    private Headers headers;
    private byte[] signature;

    public static COSESignature deserialize(@NonNull DataItem dataItem) {
        if (!MajorType.ARRAY.equals(dataItem.getMajorType()))
            throw new CborRuntimeException(String.format("De-serialization error. Expected type: Array, Found: %s",
                    dataItem.getMajorType()));

        Array coseSigArray = (Array) dataItem;
        List<DataItem> coseSigDIs = coseSigArray.getDataItems(); //Size should be 3, 2 from Headers and 1 signature

        if (coseSigDIs.size() != 3)
            throw new CborRuntimeException(
                    String.format("De-serialization error: Invalid array size. Expected: 3, Found: %d", coseSigDIs.size()));

        COSESignature coseSignature = new COSESignature();
        coseSignature.headers = Headers.deserialize(new DataItem[]{coseSigDIs.get(0), coseSigDIs.get(1)});
        coseSignature.signature = ((ByteString) coseSigDIs.get(2)).getBytes();

        return coseSignature;
    }

    public Array serialize() {
        Array array = new Array();
        Arrays.stream(headers.serialize())
                .forEach(headerItem -> array.add(headerItem));
        array.add(new ByteString(signature));

        return array;
    }
}
