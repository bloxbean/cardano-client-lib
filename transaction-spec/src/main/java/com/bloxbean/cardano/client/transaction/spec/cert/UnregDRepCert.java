package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import lombok.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;

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
        Array certArray = (Array) di;
        List<DataItem> dataItemList = certArray.getDataItems();

        Credential drepCred = CredentialSerializer.deserialize((Array) dataItemList.get(1));
        BigInteger coin = getBigInteger(dataItemList.get(2));
        return new UnregDRepCert(drepCred, coin);
    }
}
