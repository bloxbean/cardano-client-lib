package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.math.BigInteger;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StakeRegDelegCert implements Certificate {
    private final CertificateType type = CertificateType.STAKE_REG_DELEG_CERT;

    private StakeCredential stakeCredential;
    private String poolKeyHash;
    private BigInteger coin;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(stakeCredential);
        Objects.requireNonNull(poolKeyHash);
        Objects.requireNonNull(coin);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(stakeCredential.serialize());
        certArray.add(new ByteString(HexUtil.decodeHexString(poolKeyHash)));
        certArray.add(new UnsignedInteger(coin));

        return certArray;
    }

    public static StakeRegDelegCert deserialize(Array certArray) {
        return from(RawCertificate.lenient(CertKind.STAKE_REG_DELEG_CERT, RawModels.span(certArray)));
    }

    static StakeRegDelegCert from(RawCertificate cert) {
        return new StakeRegDelegCert(RawModels.stakeCredential(cert.credential()),
                HexUtil.encodeHexString(cert.poolKeyHash()), cert.coin());
    }
}
