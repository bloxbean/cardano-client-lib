package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.util.List;

/**
 * Deserialize cbor from CIP30's wallet.getBalance()
 */
public class CIP30ValueDeserializer {

    private CIP30ValueDeserializer() {
        throw new IllegalStateException("Utility Class");
    }

    /**
     * Deserialize cbor from CIP30's wallet.getBalance() method
     * @param bytes balance cbor bytes from wallet.getBalance()
     * @return {@link Value}
     */
    public static Value deserialize(byte[] bytes) throws CborDeserializationException {
        var value = new Value();
        try {
            List<DataItem> dataItemList = CborDecoder.decode(bytes);
            if (dataItemList.size() != 1) {
                throw new CborDeserializationException("Invalid no of items");
            }
            DataItem dataItem = dataItemList.get(0);
            if (dataItem.getMajorType() == MajorType.UNSIGNED_INTEGER) {
                value.setCoin(((UnsignedInteger) dataItem).getValue());
            } else if (dataItem.getMajorType() == MajorType.ARRAY) {
                var array = (Array) dataItemList.get(0);
                value.setCoin(((UnsignedInteger) array.getDataItems().get(0)).getValue());
                Map multiAssetsMap = (Map) array.getDataItems().get(1);
                for (DataItem key : multiAssetsMap.getKeys()) {
                    value.getMultiAssets().add(MultiAsset.deserialize(multiAssetsMap, key));
                }
            }
        } catch (Exception e) {
            throw new CborDeserializationException("CBOR deserialization failed", e);
        }
        return value;
    }
}
