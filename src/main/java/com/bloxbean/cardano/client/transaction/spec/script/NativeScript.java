package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;

public interface NativeScript {

    public byte[] serialize() throws CborException;

    public DataItem serializeAsDataItem() throws CborException;

    public String getPolicyId() throws CborException;
}
