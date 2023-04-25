package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class StakeDelegation implements Certificate {
    private final CertificateType type = CertificateType.STAKE_DELEGATION;

    private StakeCredential stakeCredential;
    private StakePoolId stakePoolId;

    public StakeDelegation(StakeCredential stakeCredential, StakePoolId stakePoolId) {
        this.stakeCredential = stakeCredential;
        this.stakePoolId = stakePoolId;
    }

    public static StakeDelegation deserialize(Array stRegArray) throws CborDeserializationException {
        Objects.requireNonNull(stRegArray);

        List<DataItem> dataItemList = stRegArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 3) {
            throw new CborDeserializationException("StakeDelegation deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 2)
            throw new CborDeserializationException("StakeRegistration deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        Array stakeCredArray = (Array) dataItemList.get(1);
        StakeCredential stakeCredential = StakeCredential.deserialize(stakeCredArray);

        ByteString poolKeyHashDI = (ByteString) dataItemList.get(2);
        StakePoolId stakePoolId = new StakePoolId(poolKeyHashDI.getBytes());

        return new StakeDelegation(stakeCredential, stakePoolId);
    }

    @Override
    public Array serialize() throws CborSerializationException {
        if (stakeCredential == null)
            throw new CborSerializationException("StakeDelegation serialization failed. StakeCredential is NULL");

        Array array = new Array();
        array.add(new UnsignedInteger(2));

        array.add(stakeCredential.serialize());
        array.add(new ByteString(stakePoolId.getPoolKeyHash()));
        return array;
    }
}
