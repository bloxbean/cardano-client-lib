package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.*;

import java.util.List;
import java.util.Objects;

/**
 * no_confidence = (3, gov_action_id / null)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoConfidence implements GovAction {
    private final GovActionType type = GovActionType.NO_CONFIDENCE;

    private GovActionId prevGovActionId;

    @Override
    public Array serialize() {
        Array array = new Array();
        array.add(new UnsignedInteger(3));

        if (prevGovActionId != null)
            array.add(prevGovActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static GovAction deserialize(Array govActionArray) {
        Objects.requireNonNull(govActionArray);

        List<DataItem> govActionDIList = govActionArray.getDataItems();
        DataItem actionIdDI = govActionDIList.get(1);
        GovActionId govActionId = GovAction.getGovActionId(actionIdDI);

        return new NoConfidence(govActionId);
    }
}
