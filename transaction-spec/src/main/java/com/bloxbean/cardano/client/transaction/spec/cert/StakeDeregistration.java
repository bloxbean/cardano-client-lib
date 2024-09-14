package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class StakeDeregistration implements Certificate {
    private final CertificateType type = CertificateType.STAKE_DEREGISTRATION;

    private StakeCredential stakeCredential;

    public StakeDeregistration(StakeCredential stakeCredential) {
        this.stakeCredential = stakeCredential;
    }

    public static StakeDeregistration deserialize(Array stDeregArray) throws CborDeserializationException {
        Objects.requireNonNull(stDeregArray);

        List<DataItem> dataItemList = stDeregArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2) {
            throw new CborDeserializationException("StakeDeregistration deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 1)
            throw new CborDeserializationException("StakeDeregistration deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        Array stakeCredArray = (Array) dataItemList.get(1);

        StakeCredential stakeCredential = StakeCredential.deserialize(stakeCredArray);

        return new StakeDeregistration(stakeCredential);
    }

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        if (stakeCredential == null)
            throw new CborSerializationException("StakeDeregistration serialization failed. StakeCredential is NULL");

        Array array = new Array();
        array.add(new UnsignedInteger(1));

        array.add(stakeCredential.serialize());
        return array;
    }
}
