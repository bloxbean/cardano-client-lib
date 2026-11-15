package com.bloxbean.cardano.client.transaction.util;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.exception.CborSerializationException;
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

    /**
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era an era, or null
     * @return whether it is Dijkstra or later; false for null
     */
    public static boolean isDijkstra(Era era) {
        return era != null && era.value >= Era.Dijkstra.value;
    }

    /**
     * Refuses to write a field that an era before Dijkstra has no place for, rather than drop it (ADR 0004 D1).
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era   the era being written
     * @param field the field, as the error names it
     * @throws CborSerializationException if the era is not Dijkstra or later
     */
    public static void requireDijkstra(Era era, String field) throws CborSerializationException {
        if (!isDijkstra(era))
            throw new CborSerializationException(field + " can only be written in Dijkstra, not in " + era);
    }

    /**
     * Refuses to write a value that Dijkstra removed (ADR 0004 D1).
     * <p>
     * Experimental: Dijkstra is not final; this may change in any release.
     *
     * @param era   the era being written
     * @param value the value, as the error names it
     * @throws CborSerializationException if the era is Dijkstra or later
     */
    public static void requireBeforeDijkstra(Era era, String value) throws CborSerializationException {
        if (isDijkstra(era))
            throw new CborSerializationException(value + " cannot be written in " + era);
    }
}
