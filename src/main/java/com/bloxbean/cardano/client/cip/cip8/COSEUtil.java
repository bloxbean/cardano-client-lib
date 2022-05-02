package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.NonNull;

class COSEUtil {

    public static DataItem getIntOrTextTypeFromObject(@NonNull Object value) {
        if (value instanceof Long) {
            if (((Long) value).longValue() >= 0)
                return new UnsignedInteger((Long) value);
            else
                return new NegativeInteger((Long) value);
        } else if (value instanceof Integer) {
            if (((Integer) value).intValue() >= 0)
                return new UnsignedInteger((Integer) value);
            else
                return new NegativeInteger((Integer) value);
        } else if (value instanceof String) {
            return new UnicodeString((String) value);
        } else {
            throw new CborRuntimeException(String.format("Serialization error. Expected type: long/String, found: %s", value.getClass()));
        }
    }

    public static Object decodeIntOrTextTypeFromDataItem(@NonNull DataItem dataItem) {
        if (MajorType.UNSIGNED_INTEGER.equals(dataItem.getMajorType())) {
            return ((UnsignedInteger) dataItem).getValue().longValue();
        } else if (MajorType.NEGATIVE_INTEGER.equals(dataItem.getMajorType())) {
            return ((NegativeInteger) dataItem).getValue().longValue();
        } else if (MajorType.UNICODE_STRING.equals(dataItem.getMajorType())) {
            return ((UnicodeString) dataItem).getString();
        } else
            throw new CborRuntimeException(String.format("De-serialization error: Unexpected data type: " + dataItem.getMajorType()));
    }
}
