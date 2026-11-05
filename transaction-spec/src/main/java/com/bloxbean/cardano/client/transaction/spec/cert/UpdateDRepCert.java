package com.bloxbean.cardano.client.transaction.spec.cert;

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
import lombok.*;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateDRepCert implements Certificate {
    private final CertificateType type = CertificateType.UPDATE_DREP_CERT;

    private Credential drepCredential;
    private Anchor anchor;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(drepCredential);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(CredentialSerializer.serialize(drepCredential));

        if (anchor != null)
            certArray.add(anchor.serialize());
        else
            certArray.add(SimpleValue.NULL);

        return certArray;
    }

    public static UpdateDRepCert deserialize(DataItem di) {
        return from(RawCertificate.lenient(CertKind.UPDATE_DREP_CERT, RawModels.span(di)));
    }

    static UpdateDRepCert from(RawCertificate cert) {
        return new UpdateDRepCert(RawModels.credential(cert.credential()), RawModels.anchor(cert.anchor()));
    }
}
