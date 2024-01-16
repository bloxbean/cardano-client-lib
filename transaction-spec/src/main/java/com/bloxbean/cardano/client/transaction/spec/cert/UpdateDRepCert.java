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

import java.util.List;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateDRepCert implements Certificate {
    private final CertificateType type = CertificateType.UPDATE_DREP_CERT;

    private Credential drepCredential;
    private Anchor anchor;

    @Override
    public Array serialize() throws CborSerializationException {
        Objects.requireNonNull(drepCredential);

        Array certArray = new Array();
        certArray.add(new UnsignedInteger(type.getValue()));
        certArray.add(CredentialSerializer.serialize(drepCredential));

        if (anchor != null)
            certArray.add(anchor.serialize());
        else
            certArray.add(SimpleValue.NULL);

        return certArray;
    }

    public static UpdateDRepCert deserialize(DataItem di) {
        Array certArray = (Array) di;
        List<DataItem> dataItemList = certArray.getDataItems();

        Credential drepCred = CredentialSerializer.deserialize((Array) dataItemList.get(1));

        var anchorDI = dataItemList.get(2);
        Anchor anchor = null;
        if (anchorDI != SimpleValue.NULL)
            anchor = Anchor.deserialize((Array) anchorDI);

        return new UpdateDRepCert(drepCred, anchor);
    }
}
