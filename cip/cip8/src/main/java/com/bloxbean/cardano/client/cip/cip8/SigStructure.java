package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.List;

@Accessors(fluent = true)
@Data
public class SigStructure implements COSEItem {
    ProtectedHeaderMap bodyProtected;
    ProtectedHeaderMap signProtected; //optional
    private SigContext sigContext;
    private byte[] externalAad;
    private byte[] payload;

    public static SigStructure deserialize(@NonNull DataItem dataItem) {
        if (!MajorType.ARRAY.equals(dataItem.getMajorType()))
            throw new CborRuntimeException(String.format("Cbor de-serialization error. Expected Array. Found: %s", dataItem.getMajorType().toString()));

        Array sigStructArray = (Array) dataItem;
        List<DataItem> sigStructDIs = sigStructArray.getDataItems();

        if (sigStructDIs.size() != 4 && sigStructDIs.size() != 5)
            throw new CborRuntimeException(String.format("Cbor de-serialization error. Expected no of item in array: 4 or 5, Found: %d",
                    sigStructDIs.size()));

        int index = 0;
        SigStructure sigStructure = new SigStructure();

        String context = ((UnicodeString) sigStructDIs.get(index++)).getString();
        sigStructure.sigContext(SigContext.valueOf(context));

        ProtectedHeaderMap protectedHeaderMap = ProtectedHeaderMap.deserialize(sigStructDIs.get(index++));
        sigStructure.bodyProtected(protectedHeaderMap);

        if (sigStructDIs.size() == 5) {
            //sign protected
            sigStructure.signProtected(ProtectedHeaderMap.deserialize(sigStructDIs.get(index++)));
        }

        sigStructure.externalAad(((ByteString) sigStructDIs.get(index++)).getBytes());
        sigStructure.payload(((ByteString) sigStructDIs.get(index++)).getBytes());

        return sigStructure;
    }

    @Override
    public DataItem serialize() {
        Array sigStructArray = new Array();

        if (sigContext != null)
            sigStructArray.add(new UnicodeString(sigContext.toString()));
        else
            throw new CborRuntimeException("Serialization error. sigContext can't be null");

        if (bodyProtected != null) {
            sigStructArray.add(bodyProtected.serialize());
        } else {
            sigStructArray.add(new ByteString(new byte[0]));
        }

        if (signProtected != null) {
            sigStructArray.add(signProtected.serialize());
        }

        if (externalAad != null)
            sigStructArray.add(new ByteString(externalAad));
        else
            sigStructArray.add(new ByteString(new byte[0]));

        if (payload != null)
            sigStructArray.add(new ByteString(payload));
        else
            sigStructArray.add(new ByteString(new byte[0]));

        return sigStructArray;
    }
}
