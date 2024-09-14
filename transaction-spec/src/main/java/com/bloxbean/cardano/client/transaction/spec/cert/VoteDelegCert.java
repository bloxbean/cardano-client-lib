package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import lombok.*;

import java.util.List;
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

    @SneakyThrows
    public static VoteDelegCert deserialize(Array certArray) {
        List<DataItem> dataItemList = certArray.getDataItems();

        StakeCredential stakeCredential = StakeCredential.deserialize((Array) dataItemList.get(1));
        DRep drep = DRep.deserialize(dataItemList.get(2));
        return new VoteDelegCert(stakeCredential, drep);
    }
}
