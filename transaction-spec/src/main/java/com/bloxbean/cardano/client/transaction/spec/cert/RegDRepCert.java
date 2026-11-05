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
import lombok.*;

import java.math.BigInteger;
import java.util.Objects;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class RegDRepCert implements Certificate {
    private final CertificateType type = CertificateType.REG_DREP_CERT;

    private Credential drepCredential;
    private BigInteger coin;
    private Anchor anchor;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(drepCredential);
        Objects.requireNonNull(coin);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(CredentialSerializer.serialize(drepCredential));
        certArray.add(new UnsignedInteger(coin));
        if (anchor != null)
            certArray.add(anchor.serialize());
        else
            certArray.add(SimpleValue.NULL);

        return certArray;
    }

    public static RegDRepCert deserialize(DataItem di) {
        return from(RawCertificate.lenient(CertKind.REG_DREP_CERT, CborSerializationUtil.span(di)));
    }

    static RegDRepCert from(RawCertificate cert) {
        return new RegDRepCert(RawModels.credential(cert.credential()), cert.coin(), RawModels.anchor(cert.anchor()));
    }
}
