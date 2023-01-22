package com.bloxbean.cardano.client.cip.cip30;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Serialize Value Object to cbor CIP30's Balance Object
 * to Support CIP30's wallet.getUtxo(amount: cbor<value>) method
 */
public class CIP30ValueSerializer {

    private CIP30ValueSerializer() {
        throw new IllegalStateException("Utility Class");
    }

    /**
     * Serialize Value Object to cbor bytes array
     * @param value {@link Value} Object
     * @return cbor bytes represent Value
     */
    public static byte[] serialize(Value value) throws CborSerializationException {
        try {
            List<DataItem> dataItems = new ArrayList<>();
            UnsignedInteger coin = new UnsignedInteger(BigInteger.ZERO);
            if (value.getCoin() != null) {
                coin = new UnsignedInteger(value.getCoin());
            }
            // Coin Only
            if (value.getMultiAssets() == null || value.getMultiAssets().isEmpty()) {
                dataItems.add(coin);
            } else {
                Array array = new Array();
                array.add(coin);
                array.add(value.serialize());
                dataItems.add(array);
            }
            return CborSerializationUtil.serialize(dataItems.toArray(new DataItem[0]));
        } catch (Exception e) {
            throw new CborSerializationException("CBOR Serialization failed", e);
        }
    }
}
