package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BootstrapWitness {
    private byte[] publicKey;
    private byte[] signature;
    public byte[] chainCode;
    public byte[] attributes;

    public Array serialize() {
        Array array = new Array();
        array.add(new ByteString(publicKey));
        array.add(new ByteString(signature));
        array.add(new ByteString(chainCode));
        array.add(new ByteString(attributes));

        return array;
    }

    public static BootstrapWitness deserialize(Array bootstrapWitnessArrayDI) throws CborDeserializationException {
        List<DataItem> dataItemList = bootstrapWitnessArrayDI.getDataItems();
        if (dataItemList == null || dataItemList.size() != 4)
            throw new CborDeserializationException("BootstrapWitness deserialization error. Invalid no of DataItem");

        DataItem vkeyDI = dataItemList.get(0);
        DataItem sigDI = dataItemList.get(1);
        DataItem chainCodeDI = dataItemList.get(2);
        DataItem attributesDI = dataItemList.get(3);

        BootstrapWitness bootstrapWitness = new BootstrapWitness();
        bootstrapWitness.setPublicKey(((ByteString) vkeyDI).getBytes());
        bootstrapWitness.setSignature(((ByteString) sigDI).getBytes());
        bootstrapWitness.setChainCode(((ByteString) chainCodeDI).getBytes());
        bootstrapWitness.setAttributes(((ByteString) attributesDI).getBytes());

        return bootstrapWitness;
    }

}
