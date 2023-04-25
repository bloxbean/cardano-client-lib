package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.*;

import java.util.List;

import static com.bloxbean.cardano.client.transaction.util.CborSerializationUtil.*;

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
    public Array serialize() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(4));
        array.add(new ByteString(poolKeyHash));
        array.add(new UnsignedInteger(epoch));

        return array;
    }

    public static PoolRetirement deserialize(@NonNull Array poolRetirementArray) throws CborDeserializationException {
        List<DataItem> dataItemList = poolRetirementArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 3) {
            throw new CborDeserializationException("PoolRetirement deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 4)
            throw new CborDeserializationException("PoolRetirement deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        byte[] poolKeyHash = toBytes(dataItemList.get(1));
        long epoch = toLong(dataItemList.get(2));

        return new PoolRetirement(poolKeyHash, epoch);
    }
}
