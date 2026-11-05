package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.Getter;

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
        return from(RawCertificate.lenient(CertKind.STAKE_DELEGATION, CborSerializationUtil.span(stRegArray)));
    }

    static StakeDelegation from(RawCertificate cert) throws CborDeserializationException {
        RawModels.exact(cert);
        return new StakeDelegation(RawModels.stakeCredential(cert.credential()), new StakePoolId(cert.poolKeyHash()));
    }

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        if (stakeCredential == null)
            throw new CborSerializationException("StakeDelegation serialization failed. StakeCredential is NULL");

        Array array = new Array();
        array.add(new UnsignedInteger(2));

        array.add(stakeCredential.serialize());
        array.add(new ByteString(stakePoolId.getPoolKeyHash()));
        return array;
    }
}
