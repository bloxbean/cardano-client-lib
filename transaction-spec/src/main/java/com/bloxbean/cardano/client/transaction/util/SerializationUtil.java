package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.spec.Era;

public final class SerializationUtil {

    //Create an array to represent set in Cardano
    //For Conway era or later, set the tag to 258
    public static Array createArray(Era era) {
        Array array = new Array();

        if (era == null || era.value >= Era.Conway.value) {
            array.setTag(258);
        }
        return array;
    }
}
