package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.governance.Drep;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toHex;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StakeVoteDelegCert implements Certificate {
    private final CertificateType type = CertificateType.STAKE_VOTE_DELEG_CERT;

    private StakeCredential stakeCredential;
    private String poolKeyHash;
    private Drep drep;

    @Override
    public Array serialize() throws CborSerializationException {
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

    @SneakyThrows
    public static StakeVoteDelegCert deserialize(DataItem di) {
        Array certArray = (Array) di;
        List<DataItem> dataItemList = certArray.getDataItems();

        StakeCredential stakeCredential = StakeCredential.deserialize((Array) dataItemList.get(1));
        String poolKeyHash = toHex(dataItemList.get(2));
        Drep drep = Drep.deserialize(dataItemList.get(3));
        return new StakeVoteDelegCert(stakeCredential, poolKeyHash, drep);
    }
}
