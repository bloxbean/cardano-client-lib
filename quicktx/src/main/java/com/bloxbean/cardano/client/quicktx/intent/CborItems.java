package com.bloxbean.cardano.client.quicktx.intent;

import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;

/**
 * Decodes CBOR from document hex fields with the expected shape checked, so malformed input is reported as such
 * rather than as a {@link ClassCastException}.
 */
final class CborItems {

    private CborItems() {
    }

    /**
     * @param bytes CBOR bytes from a document field
     * @param type the CBOR item type the field must hold
     * @param what what the bytes describe, for the error message
     * @return the decoded item
     * @throws IllegalArgumentException when the bytes hold another CBOR type
     */
    static <T extends DataItem> T decode(byte[] bytes, Class<T> type, String what) {
        DataItem item = CborSerializationUtil.deserialize(bytes);
        if (!type.isInstance(item))
            throw new IllegalArgumentException(what + " must be CBOR " + type.getSimpleName() + ", but was "
                    + (item == null ? "empty" : item.getMajorType()));
        return type.cast(item);
    }
}
