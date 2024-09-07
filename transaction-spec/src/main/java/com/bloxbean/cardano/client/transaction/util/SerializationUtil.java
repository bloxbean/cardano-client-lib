package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.spec.EraSerializationConfig;

public final class SerializationUtil {

    //Create an array to represent set in Cardano
    //For Conway era or later, set the tag to 258
    public static Array createArray() {
        Array array = new Array();
        if (EraSerializationConfig.INSTANCE.getEra() == Era.Conway)
            array.setTag(258);
        return array;
    }
}
