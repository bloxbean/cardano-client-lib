package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.RawCredential;

public class CredentialSerializer {
    public static Credential deserialize(Array stakeCredArray)  {
        return RawModels.credential(RawCredential.of(CborSerializationUtil.span(stakeCredArray)));
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
