package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.Special;

import java.util.List;

/**
 * cbor-java 0.9's recursive decoder, the oracle for {@link DataItemDecoder}. Callers compare its trees and then call
 * {@link #untagSingletons()} (in a finally block).
 */
final class CborJava {
    private CborJava() {
    }

    static List<DataItem> decodeAll(byte[] bytes) throws CborException {
        return CborDecoder.decode(bytes);
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
