package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.transaction.raw.RawAnchor;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
        return RawModels.anchor(RawAnchor.of(RawModels.span(array)));
    }
}
