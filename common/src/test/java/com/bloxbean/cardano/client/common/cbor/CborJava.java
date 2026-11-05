package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Special;

import java.util.ArrayList;
import java.util.List;

/**
 * cbor-java 0.9's recursive decoder, the oracle for {@link DataItemDecoder}. Callers compare its trees, through
 * {@link #withoutBreaks(DataItem)}, and then call {@link #untagSingletons()} (in a finally block).
 */
final class CborJava {
    private CborJava() {
    }

    static List<DataItem> decodeAll(byte[] bytes) throws CborException {
        return CborDecoder.decode(bytes);
    }

    /**
     * The tree as {@link DataItemDecoder} gives it: cbor-java ends an indefinite array with a BREAK item, which the
     * decoder leaves out. Subtrees without one are shared; the others are copied, so map keys keep their hashes.
     */
    static DataItem withoutBreaks(DataItem item) {
        if (item instanceof Array) {
            Array array = (Array) item;
            List<DataItem> items = new ArrayList<>();
            boolean changed = false;
            for (DataItem child : array.getDataItems()) {
                DataItem normalized = withoutBreaks(child);
                changed |= normalized != child;
                if (child == Special.BREAK && array.isChunked())
                    changed = true;
                else
                    items.add(normalized);
            }
            if (!changed)
                return item;
            Array copy = new Array();
            copy.setChunked(array.isChunked());
            items.forEach(copy::add);
            if (array.hasTag())
                copy.setTag(array.getTag());
            return copy;
        }
        if (item instanceof Map) {
            Map map = (Map) item;
            Map copy = new Map();
            boolean changed = false;
            for (DataItem key : map.getKeys()) {
                DataItem value = map.get(key);
                DataItem normalizedKey = withoutBreaks(key);
                DataItem normalizedValue = withoutBreaks(value);
                changed |= normalizedKey != key || normalizedValue != value;
                copy.put(normalizedKey, normalizedValue);
            }
            if (!changed)
                return item;
            copy.setChunked(map.isChunked());
            if (map.hasTag())
                copy.setTag(map.getTag());
            return copy;
        }
        return item;
    }

    // cbor-java attaches a tag to its shared singleton when it decodes a tagged simple value; undo it so one input
    // cannot change how later ones decode and encode.
    static void untagSingletons() {
        SimpleValue.FALSE.removeTag();
        SimpleValue.TRUE.removeTag();
        SimpleValue.NULL.removeTag();
        SimpleValue.UNDEFINED.removeTag();
        Special.BREAK.removeTag();
    }
}
