package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthCommitteeHotCert implements Certificate {
    private final CertificateType type = CertificateType.AUTH_COMMITTEE_HOT_CERT;

    private Credential committeeColdCredential;
    private Credential committeeHotCredential;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(committeeColdCredential);
        Objects.requireNonNull(committeeHotCredential);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(CredentialSerializer.serialize(committeeColdCredential));
        certArray.add(CredentialSerializer.serialize(committeeHotCredential));

        return certArray;
    }

    public static AuthCommitteeHotCert deserialize(Array certArray) {
        return from(RawCertificate.lenient(CertKind.AUTH_COMMITTEE_HOT_CERT, CborSerializationUtil.span(certArray)));
    }

    static AuthCommitteeHotCert from(RawCertificate cert) {
        return new AuthCommitteeHotCert(RawModels.credential(cert.credential()),
                RawModels.credential(cert.hotCredential()));
    }
}
