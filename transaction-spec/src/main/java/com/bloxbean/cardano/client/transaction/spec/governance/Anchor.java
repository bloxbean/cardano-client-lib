package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.*;

/**
 * {@literal
 * anchor =
 *   [ anchor_url       : url
 *   , anchor_data_hash : $hash32
 *   ]
 * }
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Anchor {
    private String anchorUrl;
    private byte[] anchorDataHash;

    public DataItem serialize() {
        Array array = new Array();
        UnicodeString anchorUrlDI = new UnicodeString(anchorUrl);
        ByteString anchorDataHashDI = new ByteString(anchorDataHash);

        array.add(anchorUrlDI);
        array.add(anchorDataHashDI);

        return array;
    }

    public static Anchor deserialize(Array array) {
        if (array != null && array.getDataItems().size() != 2)
            throw new IllegalArgumentException("Invalid anchor array. Expected 2 items. Found : "
                    + array.getDataItems().size());

        List<DataItem> diList = array.getDataItems();
        String anchorUrl = toUnicodeString(diList.get(0));
        byte[] anchorDataHash = toBytes(diList.get(1));

        return new Anchor(anchorUrl, anchorDataHash);
    }
}
