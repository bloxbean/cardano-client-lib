package com.bloxbean.cardano.client.transaction.spec.cert;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.spec.Era;
import lombok.*;

import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toBytes;

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
        List<DataItem> dataItemList = genesisKeyDelArray.getDataItems();
        if (dataItemList == null || dataItemList.size() != 4) {
            throw new CborDeserializationException("GenesisKeyDelegation deserialization failed. Invalid number of DataItem(s) : "
                    + (dataItemList != null ? String.valueOf(dataItemList.size()) : null));
        }

        UnsignedInteger type = (UnsignedInteger) dataItemList.get(0);
        if (type == null || type.getValue().intValue() != 5)
            throw new CborDeserializationException("GenesisKeyDelegation deserialization failed. Invalid type : "
                    + type != null ? String.valueOf(type.getValue().intValue()) : null);

        byte[] genesisHash = toBytes(dataItemList.get(1));
        byte[] genesisDelegateHash = toBytes(dataItemList.get(2));
        byte[] vrfKeyHash = toBytes(dataItemList.get(3));

        return new GenesisKeyDelegation(genesisHash, genesisDelegateHash, vrfKeyHash);
    }
}
