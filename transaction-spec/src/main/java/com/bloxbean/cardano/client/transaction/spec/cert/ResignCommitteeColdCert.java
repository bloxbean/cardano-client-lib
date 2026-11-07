package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResignCommitteeColdCert implements Certificate {
    private final CertificateType type = CertificateType.RESIGN_COMMITTEE_COLD_CERT;

    private Credential committeeColdCredential;
    private Anchor anchor;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(committeeColdCredential);

        Array array = new Array();
        array.add(new UnsignedInteger(type.getValue()));
        array.add(CredentialSerializer.serialize(committeeColdCredential));
        if (anchor != null)
            array.add(anchor.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static ResignCommitteeColdCert deserialize(DataItem di) {
        return from(RawCertificate.lenient(CertKind.RESIGN_COMMITTEE_COLD_CERT, CborSerializationUtil.span(di)));
    }

    static ResignCommitteeColdCert from(RawCertificate cert) {
        return new ResignCommitteeColdCert(RawModels.credential(cert.credential()), RawModels.anchor(cert.anchor()));
    }
}
