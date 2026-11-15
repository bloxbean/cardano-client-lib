package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

import java.math.BigInteger;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UnregDRepCert implements Certificate {
    private final CertificateType type = CertificateType.UNREG_DREP_CERT;

    private Credential drepCredential;
    private BigInteger coin;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(drepCredential);
        Objects.requireNonNull(coin);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(CredentialSerializer.serialize(drepCredential));
        certArray.add(new UnsignedInteger(coin));

        return certArray;
    }

    public static UnregDRepCert deserialize(DataItem di) {
        return from(RawCertificate.lenient(CertKind.UNREG_DREP_CERT, CborSerializationUtil.span(di)));
    }

    static UnregDRepCert from(RawCertificate cert) {
        return new UnregDRepCert(RawModels.credential(cert.credential()), cert.coin());
    }
}
