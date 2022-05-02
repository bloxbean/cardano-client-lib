package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Accessors(fluent = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class COSESign implements COSEItem {
    private Headers headers;
    private byte[] payload;
    private List<COSESignature> signatures = new ArrayList<>();

    public static COSESign deserialize(DataItem dataItem) {
        if (!MajorType.ARRAY.equals(dataItem.getMajorType()))
            throw new CborRuntimeException(String.format("De-serialization error. Expected type: Array, Found: %s",
                    dataItem.getMajorType()));

        Array coseSignArray = (Array) dataItem; //Size 4
        List<DataItem> coseSignDIs = coseSignArray.getDataItems();

        if (coseSignDIs.size() != 4) {
            throw new CborRuntimeException(String.format("De-serialization error. Invalid array size. Expected size: , Found: %s",
                    coseSignDIs.size()));
        }

        COSESign coseSign = new COSESign();

        Headers headers = Headers.deserialize(new DataItem[]{coseSignDIs.get(0), coseSignDIs.get(1)});
        coseSign.headers = headers;

        if (coseSignDIs.get(2) == SimpleValue.NULL) {
            coseSign.payload = null;
        } else {
            coseSign.payload = ((ByteString) coseSignDIs.get(2)).getBytes();
        }

        //Signatures
        Array signatureArray = (Array) coseSignDIs.get(3);
        List<DataItem> signatureDIs = signatureArray.getDataItems();
        coseSign.signatures(signatureDIs.stream()
                .map(signatureDI -> COSESignature.deserialize(signatureDI))
                .collect(Collectors.toList()));

        return coseSign;
    }

    public COSESign addSignature(COSESignature signature) {
        this.signatures.add(signature);
        return this;
    }

    public Array serialize() {
        Array cosignArray = new Array();
        Arrays.stream(headers.serialize())
                .forEach(headerItem -> cosignArray.add(headerItem));

        if (payload != null && payload.length > 0)
            cosignArray.add(new ByteString(payload));
        else
            cosignArray.add(SimpleValue.NULL);

        if (signatures != null && signatures.size() > 0) {
            Array signatureArray = new Array();
            signatures.forEach(signature -> signatureArray.add(signature.serialize()));

            cosignArray.add(signatureArray);
        } else {
            throw new CborRuntimeException("Cbor serialization failed. One or more signature required");
        }

        return cosignArray;
    }

    public byte[] serializeAsBytes() {
        DataItem di = serialize();

        try {
            return CborSerializationUtil.serialize(di, false);
        } catch (CborException e) {
            throw new CborRuntimeException("Cbor serializaion error", e);
        }
    }

}
