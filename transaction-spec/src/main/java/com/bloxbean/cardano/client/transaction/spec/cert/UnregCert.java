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
public class UnregCert implements Certificate {
    private final CertificateType type = CertificateType.UNREG_CERT;

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

    public static UnregCert deserialize(Array certArray) {
        return from(RawCertificate.lenient(CertKind.UNREG_CERT, RawModels.span(certArray)));
    }

    static UnregCert from(RawCertificate cert) {
        return new UnregCert(RawModels.stakeCredential(cert.credential()), cert.coin());
    }
}
