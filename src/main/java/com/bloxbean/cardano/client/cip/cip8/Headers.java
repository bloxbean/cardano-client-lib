package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Headers {
    private ProtectedHeaderMap _protected;
    private HeaderMap unprotected;

    public static Headers deserialize(DataItem[] items) {
        //List<DataItem> items = array.getDataItems();
        if (items.length != 2) {
            throw new CborRuntimeException(String.format("De-serialization error. Invalid array size. Expected size: , Found: %s",
                    items.length));
        }

        Headers headers = new Headers();
        headers._protected = ProtectedHeaderMap.deserialize(items[0]);
        headers.unprotected = HeaderMap.deserialize(items[1]);

        return headers;
    }

    public DataItem[] serialize() {
        DataItem[] dataItems = new DataItem[2];

        if (_protected != null) {
            dataItems[0] = _protected.serialize();
        } else {
            dataItems[0] = new ByteString(new byte[0]);
        }

        if (unprotected != null) {
            dataItems[1] = unprotected.serialize();
        } else {
            dataItems[1] = new Map();
        }

        return dataItems;
    }
}
