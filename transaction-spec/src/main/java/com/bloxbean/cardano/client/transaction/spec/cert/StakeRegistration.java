package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.Getter;

import static com.bloxbean.cardano.client.transaction.util.SerializationUtil.requireBeforeDijkstra;

@Getter
public class StakeRegistration implements Certificate {
    private final CertificateType type = CertificateType.STAKE_REGISTRATION;

    private StakeCredential stakeCredential;

    public StakeRegistration(StakeCredential stakeCredential) {
        this.stakeCredential = stakeCredential;
    }

    public static StakeRegistration deserialize(Array stRegArray) throws CborDeserializationException {
        return from(RawCertificate.lenient(CertKind.STAKE_REGISTRATION, CborSerializationUtil.span(stRegArray)));
    }

    static StakeRegistration from(RawCertificate cert) throws CborDeserializationException {
        return new StakeRegistration(RawModels.stakeCredential(RawModels.exact(cert).credential()));
    }

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        requireBeforeDijkstra(era, "A StakeRegistration certificate (tag 0)");
        if (stakeCredential == null)
            throw new CborSerializationException("StakeRegistration serialization failed. StakeCredential is NULL");

        Array array = new Array();
        array.add(new UnsignedInteger(0));

        array.add(stakeCredential.serialize());
        return array;
    }
}
