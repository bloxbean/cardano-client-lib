package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.util.Objects;

//vote_deleg_cert = (9, stake_credential, drep)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoteDelegCert implements Certificate {
    private final CertificateType type = CertificateType.VOTE_DELEG_CERT;

    private StakeCredential stakeCredential;
    private DRep drep;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(stakeCredential);
        Objects.requireNonNull(drep);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(stakeCredential.serialize());
        certArray.add(drep.serialize());

        return certArray;
    }

    public static VoteDelegCert deserialize(Array certArray) {
        return from(RawCertificate.lenient(CertKind.VOTE_DELEG_CERT, RawModels.span(certArray)));
    }

    static VoteDelegCert from(RawCertificate cert) {
        return new VoteDelegCert(RawModels.stakeCredential(cert.credential()), RawModels.drep(cert.drep()));
    }
}
