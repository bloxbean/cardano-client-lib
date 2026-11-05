package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.transaction.raw.RawConstitution;
import com.bloxbean.cardano.client.transaction.util.RawModels;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.Objects;

/**
 * constitution =
 *   [ anchor
 *   , scripthash / null
 *   ]
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class Constitution {
    private Anchor anchor;
    private String scripthash; //TODO: Use Optional ?

    public Array serialize() {
        Objects.requireNonNull(anchor);

        Array array = new Array();
        array.add(anchor.serialize());

        if (scripthash != null)
            array.add(new ByteString(HexUtil.decodeHexString(scripthash)));
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static Constitution deserialize(Array array) {
        return from(RawConstitution.lenient(RawModels.span(array)));
    }

    /**
     * @param raw a constitution as received
     * @return the model
     */
    public static Constitution from(RawConstitution raw) {
        Anchor anchor = RawModels.anchor(raw.anchor());
        return new Constitution(anchor, raw.scriptHash().map(HexUtil::encodeHexString).orElse(null));
    }
}
