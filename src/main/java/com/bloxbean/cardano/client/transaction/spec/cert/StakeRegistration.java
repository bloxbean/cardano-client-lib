package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class StakeRegistration implements Certificate {
    private final CertificateType type = CertificateType.STAKE_REGISTRATION;

    private StakeCredential stakeCredential;

    public StakeRegistration(StakeCredential stakeCredential) {
        this.stakeCredential = stakeCredential;
    }

    public static StakeRegistration deserialize(Array stRegArray) throws CborDeserializationException {
        Objects.requireNonNull(stRegArray);

        List<DataItem> dataItemList = stRegArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2) {
            throw new CborDeserializationException("StakeRegistration deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 0)
            throw new CborDeserializationException("StakeRegistration deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        Array stakeCredArray = (Array) dataItemList.get(1);

        StakeCredential stakeCredential = StakeCredential.deserialize(stakeCredArray);

        return new StakeRegistration(stakeCredential);
    }

    @Override
    public Array serialize() throws CborSerializationException {
        if (stakeCredential == null)
            throw new CborSerializationException("StakeRegistration serialization failed. StakeCredential is NULL");

        Array array = new Array();
        array.add(new UnsignedInteger(0));

        array.add(stakeCredential.serialize());
        return array;
    }
}
