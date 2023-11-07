package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.math.BigInteger;
import java.util.List;

public class CredentialSerializer {
    public static Credential deserialize(Array stakeCredArray)  {
        List<DataItem> dataItemList = stakeCredArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2)
            throw new CborRuntimeException("Credential deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));

        UnsignedInteger typeDI = (UnsignedInteger) dataItemList.get(0);
        ByteString hashDI = (ByteString) dataItemList.get(1);

        BigInteger typeBI = typeDI.getValue();
        if (typeBI.intValue() == 0) {
            return Credential.fromKey(hashDI.getBytes());
        } else if (typeBI.intValue() == 1) {
            return Credential.fromScript(hashDI.getBytes());
        } else {
            throw new CborRuntimeException("Credential deserialization failed. Invalid CredType : "
                    + typeBI.intValue());
        }
    }

    public static Array serialize(Credential credential)  {
        Array array = new Array();
        if (credential.getType() == CredentialType.Key) {
            array.add(new UnsignedInteger(0));
        } else if (credential.getType() == CredentialType.Script) {
            array.add(new UnsignedInteger(1));
        } else {
            throw new CborRuntimeException("Invalid credential type : " + credential.getType());
        }

        array.add(new ByteString(credential.getBytes()));
        return array;
    }

}
