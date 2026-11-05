package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.math.BigInteger;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegCert implements Certificate {
    private final CertificateType type = CertificateType.REG_CERT;

    private StakeCredential stakeCredential;
    private BigInteger coin;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(stakeCredential);
        Objects.requireNonNull(coin);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(stakeCredential.serialize());
        certArray.add(new UnsignedInteger(coin));

        return certArray;
    }

    public static RegCert deserialize(Array certArray) {
        return from(RawCertificate.lenient(CertKind.REG_CERT, RawModels.span(certArray)));
    }

    static RegCert from(RawCertificate cert) {
        return new RegCert(RawModels.stakeCredential(cert.credential()), cert.coin());
    }
}
