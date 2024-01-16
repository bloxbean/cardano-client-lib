package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import lombok.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VoteRegDelegCert implements Certificate {
    private final CertificateType type = CertificateType.VOTE_REG_DELEG_CERT;

    private StakeCredential stakeCredential;
    private DRep drep;
    private BigInteger coin;

    @Override
    public Array serialize() throws CborSerializationException {
        Objects.requireNonNull(stakeCredential);
        Objects.requireNonNull(drep);
        Objects.requireNonNull(coin);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(stakeCredential.serialize());
        certArray.add(drep.serialize());
        certArray.add(new UnsignedInteger(coin));

        return certArray;
    }

    @SneakyThrows
    public static VoteRegDelegCert deserialize(Array certArray) {
        List<DataItem> dataItemList = certArray.getDataItems();

        StakeCredential stakeCredential = StakeCredential.deserialize((Array) dataItemList.get(1));
        DRep drep = DRep.deserialize(dataItemList.get(2));
        BigInteger coin = getBigInteger(dataItemList.get(3));

        return new VoteRegDelegCert(stakeCredential, drep, coin);
    }
}
