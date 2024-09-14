package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.util.CredentialSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResignCommitteeColdCert implements Certificate {
    private final CertificateType type = CertificateType.RESIGN_COMMITTEE_COLD_CERT;

    private Credential committeeColdCredential;
    private Anchor anchor;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Objects.requireNonNull(committeeColdCredential);

        Array array = new Array();
        array.add(new UnsignedInteger(type.getValue()));
        array.add(CredentialSerializer.serialize(committeeColdCredential));
        if (anchor != null)
            array.add(anchor.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static ResignCommitteeColdCert deserialize(DataItem di) {
        Array certArray = (Array) di;
        List<DataItem> dataItemList = certArray.getDataItems();

        Credential committeeColdCred = CredentialSerializer.deserialize((Array) dataItemList.get(1));

        var anchorDI = dataItemList.get(2);
        Anchor anchor = null;
        if (anchorDI != SimpleValue.NULL) {
            anchor = Anchor.deserialize((Array) anchorDI);
        }

        return new ResignCommitteeColdCert(committeeColdCred, anchor);
    }
}
