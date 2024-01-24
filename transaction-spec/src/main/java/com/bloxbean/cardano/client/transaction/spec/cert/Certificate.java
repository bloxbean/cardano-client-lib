package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.List;
import java.util.Objects;

public interface Certificate {

    static Certificate deserialize(Array certArray) throws CborDeserializationException {
        Objects.requireNonNull(certArray);

        List<DataItem> dataItemList = certArray.getDataItems();
        if (dataItemList == null || dataItemList.size() < 2) {
            throw new CborDeserializationException("Certificate deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger typeUI = (UnsignedInteger) dataItemList.get(0);
        int type = typeUI.getValue().intValue();

        Certificate certificate;
        switch (type) {
            case 0:
                certificate = StakeRegistration.deserialize(certArray);
                break;
            case 1:
                certificate = StakeDeregistration.deserialize(certArray);
                break;
            case 2:
                certificate = StakeDelegation.deserialize(certArray);
                break;
            case 3:
                certificate = PoolRegistration.deserialize(certArray);
                break;
            case 4:
                certificate = PoolRetirement.deserialize(certArray);
                break;
            case 5:
                certificate = GenesisKeyDelegation.deserialize(certArray);
                break;
            case 6:
                certificate = MoveInstataneous.deserialize(certArray);
                break;
            case 7:
                certificate = RegCert.deserialize(certArray);
                break;
            case 8:
                certificate = UnregCert.deserialize(certArray);
                break;
            case 9:
                certificate = VoteDelegCert.deserialize(certArray);
                break;
            case 10:
                certificate = StakeVoteDelegCert.deserialize(certArray);
                break;
            case 11:
                certificate = StakeRegDelegCert.deserialize(certArray);
                break;
            case 12:
                certificate = VoteRegDelegCert.deserialize(certArray);
                break;
            case 13:
                certificate = StakeVoteRegDelegCert.deserialize(certArray);
                break;
            case 14:
                certificate = AuthCommitteeHotCert.deserialize(certArray);
                break;
            case 15:
                certificate = ResignCommitteeColdCert.deserialize(certArray);
                break;
            case 16:
                certificate = RegDRepCert.deserialize(certArray);
                break;
            case 17:
                certificate = UnregDRepCert.deserialize(certArray);
                break;
            case 18:
                certificate = UpdateDRepCert.deserialize(certArray);
                break;
            default:
                throw new CborDeserializationException("Certificate deserialization failed. Unknown type : " + type);
        }

        return certificate;
    }

    Array serialize() throws CborSerializationException;

    default String getCborHex() throws CborSerializationException {
        try {
            return HexUtil.encodeHexString(CborSerializationUtil.serialize(serialize()));
        } catch (CborException e) {
            throw new CborSerializationException("Cbor serialization error", e);
        }
    }
}
