package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StakeVoteDelegCert implements Certificate {
    private final CertificateType type = CertificateType.STAKE_VOTE_DELEG_CERT;

    private StakeCredential stakeCredential;
    private String poolKeyHash;
    private DRep drep;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(stakeCredential);
        Objects.requireNonNull(poolKeyHash);
        Objects.requireNonNull(drep);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(stakeCredential.serialize());
        certArray.add(new ByteString(HexUtil.decodeHexString(poolKeyHash)));
        certArray.add(drep.serialize());

        return certArray;
    }

    public static StakeVoteDelegCert deserialize(DataItem di) {
        return from(RawCertificate.lenient(CertKind.STAKE_VOTE_DELEG_CERT, RawModels.span(di)));
    }

    static StakeVoteDelegCert from(RawCertificate cert) {
        return new StakeVoteDelegCert(RawModels.stakeCredential(cert.credential()),
                HexUtil.encodeHexString(cert.poolKeyHash()), RawModels.drep(cert.drep()));
    }
}
