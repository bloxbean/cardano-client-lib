package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import lombok.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class RegDrepCert implements Certificate {
    private final CertificateType type = CertificateType.REG_DREP_CERT;

    private Credential drepCredential;
    private BigInteger coin;
    private Anchor anchor;

    @Override
    public Array serialize() throws CborSerializationException {
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

    public static RegDrepCert deserialize(DataItem di) {
        Array certArray = (Array) di;
        List<DataItem> dataItemList = certArray.getDataItems();

        Credential drepCred = CredentialSerializer.deserialize((Array) dataItemList.get(1));
        BigInteger coin = getBigInteger(dataItemList.get(2));

        var anchorDI = dataItemList.get(3);
        Anchor anchor = null;
        if (anchorDI != SimpleValue.NULL)
            anchor = Anchor.deserialize((Array) anchorDI);

        return new RegDrepCert(drepCred, coin, anchor);
    }
}
