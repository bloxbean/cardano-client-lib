package com.bloxbean.cardano.client.transaction.spec.governance.actions;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.transaction.spec.governance.Constitution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

/**
 * new_constitution = (5, gov_action_id / null, constitution)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewConstitution implements GovAction {
    private final GovActionType type = GovActionType.NEW_CONSTITUTION;

    private GovActionId govActionId;
    private Constitution constitution;

    @Override
    public Array serialize() {
        Objects.requireNonNull(constitution);

        Array array = new Array();
        array.add(new UnsignedInteger(5));

        if (govActionId != null)
            array.add(govActionId.serialize());
        else
            array.add(SimpleValue.NULL);

        return array;
    }

    public static NewConstitution deserialize(Array govActionArray) {
        List<DataItem> govActionDIList = govActionArray.getDataItems();

        DataItem actionIdDI = govActionDIList.get(1);
        GovActionId govActionId = GovAction.getGovActionId(actionIdDI);

        Constitution constitution = Constitution.deserialize((Array) govActionDIList.get(2));

        return new NewConstitution(govActionId, constitution);
    }

}
