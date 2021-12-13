package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VkeyWitness {
    private byte[] vkey;
    private byte[] signature;

    public Array serialize() {
        Array array = new Array();
        array.add(new ByteString(vkey));
        array.add(new ByteString(signature));

        return array;
    }

    public static VkeyWitness deserialize(Array vkWitness) throws CborDeserializationException {
        List<DataItem> dataItemList = vkWitness.getDataItems();
        if(dataItemList == null || dataItemList.size() != 2)
            throw new CborDeserializationException("VkeyWitness deserialization error. Invalid no of DataItem");

        DataItem vkeyDI = dataItemList.get(0);
        DataItem sigDI = dataItemList.get(1);

        VkeyWitness vkeyWitness = new VkeyWitness();
        vkeyWitness.setVkey(((ByteString)vkeyDI).getBytes());
        vkeyWitness.setSignature(((ByteString)sigDI).getBytes());

        return vkeyWitness;
    }

    @Override
    public String toString() {
        if (vkey != null && signature != null) {
            return "VkeyWitness{" +
                    "vkey=" + HexUtil.encodeHexString(vkey) +
                    ", signature=" + HexUtil.encodeHexString(signature) +
                    '}';
        } else {
            return super.toString();
        }
    }
}
