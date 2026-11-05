package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
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

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class PoolRetirement implements Certificate {
    private final CertificateType type = CertificateType.POOL_RETIREMENT;

    private byte[] poolKeyHash;
    private long epoch;

    @Override
    public Array serialize(Era era) throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(4));
        array.add(new ByteString(poolKeyHash));
        array.add(unsignedLongItem(epoch));

        return array;
    }

    public static PoolRetirement deserialize(@NonNull Array poolRetirementArray) throws CborDeserializationException {
        return from(RawCertificate.lenient(CertKind.POOL_RETIREMENT, CborSerializationUtil.span(poolRetirementArray)));
    }

    static PoolRetirement from(RawCertificate cert) throws CborDeserializationException {
        RawModels.exact(cert);
        return new PoolRetirement(cert.poolKeyHash(), cert.epoch());
    }
}
