package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.List;

@Accessors(fluent = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class COSESign1 implements COSEItem {
    private Headers headers;
    private byte[] payload;
    private byte[] signature;

    public static COSESign1 deserialize(DataItem dataItem) {
        if (!MajorType.ARRAY.equals(dataItem.getMajorType()))
            throw new CborRuntimeException(String.format("De-serialization error. Expected type: Array, Found: %s",
                    dataItem.getMajorType()));

        Array coseSignArray = (Array) dataItem; //Size 4
        List<DataItem> coseSignDIs = coseSignArray.getDataItems();

        if (coseSignDIs.size() != 4) {
            throw new CborRuntimeException(String.format("De-serialization error. Invalid array size. Expected size: , Found: %s",
                    coseSignDIs.size()));
        }

        COSESign1 coseSign1 = new COSESign1();

        Headers headers = Headers.deserialize(new DataItem[]{coseSignDIs.get(0), coseSignDIs.get(1)});
        coseSign1.headers = headers;

        if (coseSignDIs.get(2) == SimpleValue.NULL) {
            coseSign1.payload = null;
        } else {
            coseSign1.payload = ((ByteString) coseSignDIs.get(2)).getBytes();
        }

        ByteString signatureBS = (ByteString) coseSignDIs.get(3);
        coseSign1.signature = signatureBS.getBytes();

        return coseSign1;
    }

    public Array serialize() {
        Array array = new Array();
        Arrays.stream(headers.serialize())
                .forEach(headerItem -> array.add(headerItem));

        if (payload != null && payload.length > 0)
            array.add(new ByteString(payload));
        else
            array.add(SimpleValue.NULL);

        if (signature != null)
            array.add(new ByteString(signature));
        else
            array.add(new ByteString(new byte[0]));

        return array;
    }

}
