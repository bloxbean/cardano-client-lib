package com.bloxbean.cardano.client.transaction.spec.governance;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.util.HexUtil;
import lombok.*;

import java.util.List;
import java.util.Objects;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toHex;

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
        List<DataItem> constitutionDIList = array.getDataItems();

        if (constitutionDIList.size() != 2)
            throw new IllegalArgumentException("Invalid constitution array. Expected 2 items. Found : "
                    + constitutionDIList.size());

        Anchor anchor = Anchor.deserialize((Array) constitutionDIList.get(0));

        DataItem scriptHashDI = constitutionDIList.get(1);
        String scriptHash;
        if (scriptHashDI == SimpleValue.NULL)
            scriptHash = null;
        else
            scriptHash = toHex(scriptHashDI);

        return new Constitution(anchor, scriptHash);
    }
}
