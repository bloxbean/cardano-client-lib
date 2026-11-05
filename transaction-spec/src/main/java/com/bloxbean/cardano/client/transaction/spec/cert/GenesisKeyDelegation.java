package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.transaction.raw.CertKind;
import com.bloxbean.cardano.client.transaction.raw.RawCertificate;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
//TODO -- Unit & Integration tests pending
public class GenesisKeyDelegation implements Certificate {
    private final CertificateType type = CertificateType.GENESIS_KEY_DELEGATION;

    private byte[] genesisHash;
    private byte[] genesisDelegateHash;
    private byte[] vrfKeyHash;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(5));
        array.add(new ByteString(genesisHash));
        array.add(new ByteString(genesisDelegateHash));
        array.add(new ByteString(vrfKeyHash));

        return array;
    }

    public static GenesisKeyDelegation deserialize(Array genesisKeyDelArray) throws CborDeserializationException {
        return from(RawCertificate.lenient(CertKind.GENESIS_KEY_DELEGATION, RawModels.span(genesisKeyDelArray)));
    }

    static GenesisKeyDelegation from(RawCertificate cert) throws CborDeserializationException {
        RawModels.exact(cert);
        return new GenesisKeyDelegation(cert.genesisHash(), cert.genesisDelegateHash(), cert.vrfKeyHash());
    }
}
